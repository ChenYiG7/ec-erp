# -*- coding: utf-8 -*-
"""#31 收付款/回款 真库验证(TODO"SQL 兼容性红线":mapper XML 自定义 SQL 必须真库验证)。

一次跑完五件事:
  1. 建表/加列/菜单种子幂等重放(payment_record/payment_alloc CREATE IF NOT EXISTS、
     supplier.settle_days 与 purchase_order.audit_time information_schema 判存、sys_menu 40/4001~4003 INSERT IGNORE)
     ——已建库对齐等价于 scripts/archive/payment_receipt_migration.py 的①~③;
  2. Σalloc 聚合(payment_query Mapper.xml sumPaidByPoIds):NORMAL 计入/VOIDED 失效;
  3. 供应商应付视图(pageSupplierPayables):草稿单不计应付、已付按 ΣNORMAL 分摊;
  4. uk_ref(ref_type,ref_id,deleted) 派生幂等:同源单据第二条插入必须被唯一键拦;
  5. 账期超期查询(listOverduePayables,#31 账期到期提醒):audit_time+settle_days<今日 且未付清命中,
     付清/草稿/无锚点单不命中。

聚合语句从 erp-finance PaymentQueryMapper.xml 与 erp-purchase PurchaseOrderMapper.xml 原文提取
(foreach 占位 #{poId} 注入哑元 id 列表;测的就是要发布的)。
哑元 id 统一 990031 段,验证完逆序删除业务哑元行,不碰业务数据;建表/菜单保留(对齐用)。
用法:python scripts/validate_payment_sql.py
"""
import io
import re
import sys
from pathlib import Path

import pymysql

sys.path.insert(0, str(Path(__file__).resolve().parent))
from menu_tool import connect, load_props  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
XML = ROOT / 'erp-finance' / 'src' / 'main' / 'resources' / 'mapper' / 'PaymentQueryMapper.xml'
PURCHASE_XML = ROOT / 'erp-purchase' / 'src' / 'main' / 'resources' / 'mapper' / 'PurchaseOrderMapper.xml'
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
PASSED = []

SUPPLIER_ID = 990031
PO_A = 990031          # AUDITED total 100,付款分摊 60+40(NORMAL)+50(VOIDED,不计)
PO_B = 990032          # DRAFT total 30(不计应付)
PO_C = 990033          # AUDITED total 50,无付款
PAY_NORMAL_1 = 990031
PAY_NORMAL_2 = 990032
PAY_VOIDED = 990033
REPORT_REF_ID = 990031

DDL_PATH = ROOT / 'docs' / 'sql' / '01_schema_init.sql'


def ok(label):
    PASSED.append(label)
    print(f'  ✅ {label}')


def extract_statement(statement_id, xml=None):
    text = (xml or XML).read_text(encoding='utf-8')
    m = re.search(r'<select\s+id="%s"[^>]*>(.*?)</select>' % statement_id, text, re.S)
    if not m:
        raise SystemExit(f'XML 中找不到语句 {statement_id}')
    body = re.sub(r'<!--.*?-->', ' ', m.group(1), flags=re.S)
    # foreach 标签翻译为 open/close 字面量(本仓 foreach 均为 IN 列表:open="(" close=")"),留循环体
    body = re.sub(r'<foreach[^>]*>', '(', body)
    body = body.replace('</foreach>', ')')
    body = body.replace('&gt;', '>').replace('&lt;', '<')
    return re.sub(r'\s+', ' ', body).strip()


def column_exists(cur, table, column):
    cur.execute("SELECT COUNT(*) FROM information_schema.columns "
                "WHERE table_schema='erp' AND table_name=%s AND column_name=%s", (table, column))
    return cur.fetchone()[0] > 0


def replay_ddl_and_menus(cur):
    """从正本 SQL 截取 payment 两表 DDL 原样执行(保持逐字同源),加列/菜单幂等重放。"""
    sql_text = DDL_PATH.read_text(encoding='utf-8')
    for table in ('payment_record', 'payment_alloc'):
        m = re.search(r"CREATE TABLE IF NOT EXISTS %s \(.*?\n\) COMMENT '[^']*'" % table, sql_text, re.S)
        if not m:
            raise SystemExit(f'正本 SQL 中找不到建表段 {table}')
        cur.execute(m.group(0))
    if not column_exists(cur, 'supplier', 'settle_days'):
        cur.execute("ALTER TABLE supplier ADD COLUMN settle_days INT NULL "
                    "COMMENT '账期天数(V1仅展示,到期提醒随预警引擎评估,TODO#31)'")
    if not column_exists(cur, 'purchase_order', 'audit_time'):
        cur.execute("ALTER TABLE purchase_order ADD COLUMN audit_time DATETIME NULL "
                    "COMMENT '审核时间(audit 动作落,#31 账期起算锚点;2026-09-12 加列,已建库跑 replay_schema_migration.py 补齐)'")
    cur.execute("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, perm_key, path, component, icon, sort) "
                "VALUES (40, 31, '资金流水', 2, 'finance:payment:list', '/finance/payments', 'finance/payment/index', 'CreditCard', 4)")
    cur.execute("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, perm_key) VALUES "
                "(4001, 40, '采购付款登记', 3, 'finance:payment:purchase'),"
                "(4002, 40, '手工登记', 3, 'finance:payment:manual'),"
                "(4003, 40, '作废', 3, 'finance:payment:void')")
    cur.execute("INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1,40),(1,4001),(1,4002),(1,4003)")


CLEANUP_SQLS = [
    f"DELETE FROM payment_alloc WHERE payment_id IN ({PAY_NORMAL_1},{PAY_NORMAL_2},{PAY_VOIDED})",
    f"DELETE FROM payment_record WHERE id IN ({PAY_NORMAL_1},{PAY_NORMAL_2},{PAY_VOIDED}) OR "
    f"(ref_type='SETTLEMENT_REPORT' AND ref_id={REPORT_REF_ID})",
    f"DELETE FROM purchase_order WHERE id IN ({PO_A},{PO_B},{PO_C})",
    f"DELETE FROM supplier WHERE id={SUPPLIER_ID}",
]


def insert_payment(cur, pay_id, no, status, direction, biz, party_type, amount, currency, rate, cny, ref=None):
    cur.execute(
        f"INSERT INTO payment_record (id, payment_no, direction, biz_type, party_type, party_id, "
        f"amount, currency, exchange_rate, amount_cny, paid_at, status, ref_type, ref_id, deleted) "
        f"VALUES (%s,%s,%s,%s,%s,{SUPPLIER_ID},%s,%s,%s,%s,'2026-09-10 10:00:00',%s,%s,%s,0)",
        (pay_id, no, direction, biz, party_type, amount, currency, rate, cny, status,
         ref[0] if ref else None, ref[1] if ref else None))


def main():
    conn = connect()
    try:
        cur = conn.cursor()
        # 先幂等建表/菜单(首次跑库中无表,DELETE 预清扫须在其后)
        replay_ddl_and_menus(cur)
        conn.commit()
        ok('建表/加列/菜单种子幂等重放(payment_record/payment_alloc/settle_days/40/4001~4003)')
        for sql in CLEANUP_SQLS:
            cur.execute(sql)
        conn.commit()

        # ── 哑元:供应商(settle_days=7)+ 三张采购单(旧审核锚点;草稿不计应付)+ 两条 NORMAL 付款(分摊 60/40)、
        #    一条 VOIDED(分摊 50)──
        cur.execute(f"INSERT INTO supplier (id, name, status, settle_days) VALUES ({SUPPLIER_ID},'__val__资金供应商',1,7)")
        cur.execute(
            f"INSERT INTO purchase_order (id, po_no, supplier_id, warehouse_id, status, audit_time, total_amount) VALUES "
            f"({PO_A},'__val__POA',{SUPPLIER_ID},990031,'AUDITED','2026-08-01 10:00:00',100),"
            f"({PO_B},'__val__POB',{SUPPLIER_ID},990031,'DRAFT',NULL,30),"
            f"({PO_C},'__val__POC',{SUPPLIER_ID},990031,'AUDITED','2026-08-01 10:00:00',50)")
        insert_payment(cur, PAY_NORMAL_1, '__val__PAY1', 'NORMAL', 'EXPENSE', 'PURCHASE_PAYMENT',
                       'SUPPLIER', '60.0000', 'CNY', 1, '60.0000')
        insert_payment(cur, PAY_NORMAL_2, '__val__PAY2', 'NORMAL', 'EXPENSE', 'PURCHASE_PAYMENT',
                       'SUPPLIER', '40.0000', 'CNY', 1, '40.0000')
        insert_payment(cur, PAY_VOIDED, '__val__PAY3', 'VOIDED', 'EXPENSE', 'PURCHASE_PAYMENT',
                       'SUPPLIER', '50.0000', 'CNY', 1, '50.0000')
        cur.execute(
            f"INSERT INTO payment_alloc (id, payment_id, alloc_biz_type, alloc_biz_id, amount) VALUES "
            f"(990031,{PAY_NORMAL_1},'PURCHASE',{PO_A},60),"
            f"(990032,{PAY_NORMAL_2},'PURCHASE',{PO_A},40),"
            f"(990033,{PAY_VOIDED},'PURCHASE',{PO_A},50)")
        conn.commit()

        # 1) Σalloc 聚合:NORMAL 60+40=100;VOIDED 50 必须排除;待付=总额100-已付100=0
        sql = extract_statement('sumPaidByPoIds').replace('#{poId}', str(PO_A))
        cur.execute(sql)
        rows = {r[0]: r for r in cur.fetchall()}
        assert PO_A in rows, f'采购单哑元行缺失: {rows}'
        assert rows[PO_A][1] == 100, f'总额列不符: {rows[PO_A]}'
        assert rows[PO_A][2] == 100, f'已付 Σ 不符(应 100,VOIDED 50 须排除): {rows[PO_A]}'
        assert rows[PO_A][3] == 0, f'待付不符(应 0): {rows[PO_A]}'
        ok('sumPaidByPoIds(NORMAL 聚合/VOIDED 失效/SQL 侧算待付)')

        # 2) 供应商应付视图:应付 100+50=150(DRAFT 30 不计),已付 100
        cur.execute(extract_statement('pageSupplierPayables'))
        match = [r for r in cur.fetchall() if r[0] == SUPPLIER_ID]
        assert match, '供应商哑元行缺失'
        _, name, settle_days, payable, paid, unpaid = match[0]
        assert name == '__val__资金供应商', f'名称 join 异常: {match[0]}'
        assert payable == 150, f'应付不符(应 150,草稿 30 不计): {match[0]}'
        assert paid == 100, f'已付不符(应 100): {match[0]}'
        assert unpaid == 50, f'待付不符(应 150-100=50): {match[0]}'
        ok('pageSupplierPayables(6 列构造映射/草稿不计应付/已付 ΣNORMAL 分摊/待付=应付-已付)')

        # 3) uk_ref 派生幂等:同源结算报告第二条必须撞唯一键
        insert_payment(cur, 990041, '__val__PAYR1', 'NORMAL', 'INCOME', 'SETTLEMENT_RECEIPT',
                       'PLATFORM', '100.0000', 'USD', None, None,
                       ref=('SETTLEMENT_REPORT', REPORT_REF_ID))
        conn.commit()
        blocked = False
        try:
            insert_payment(cur, 990042, '__val__PAYR2', 'NORMAL', 'INCOME', 'SETTLEMENT_RECEIPT',
                           'PLATFORM', '100.0000', 'USD', None, None,
                           ref=('SETTLEMENT_REPORT', REPORT_REF_ID))
            conn.commit()
        except pymysql.err.IntegrityError:
            blocked = True
            conn.rollback()
        assert blocked, 'uk_ref 未拦截同源报告重复派生'
        ok('uk_ref(ref_type,ref_id,deleted) 派生幂等唯一键')

        # 5) 账期超期查询(#31 账期到期提醒):PO_C(旧审核+未付)命中;PO_A 已付清/PO_B 草稿不命中
        cur.execute(extract_statement('listOverduePayables', PURCHASE_XML).replace('#{asOf}', "'2026-09-12'"))
        overdue = {r[0]: r for r in cur.fetchall()}
        assert PO_C in overdue, f'超期未付清单 PO_C 未命中: {overdue}'
        assert PO_A not in overdue, f'已付清单 PO_A 不应命中(未付>0 条件): {overdue}'
        assert PO_B not in overdue, f'草稿单 PO_B 不应命中(状态口径): {overdue}'
        assert overdue[PO_C][4] == 7, f'账期天数列不符(应 7): {overdue[PO_C]}'
        assert overdue[PO_C][6] == 50, f'未付金额不符(应 50): {overdue[PO_C]}'
        ok('listOverduePayables(#31 账期超期:锚点+账期+未付清命中,付清/草稿排除,7 列构造对齐)')

    finally:
        cur = conn.cursor()
        for sql in reversed(CLEANUP_SQLS):
            cur.execute(sql)
        conn.commit()
        conn.close()

    print(f'\nALL GREEN: {len(PASSED)} 项验证通过(业务哑元已清理,建表/菜单保留)')


if __name__ == '__main__':
    main()
