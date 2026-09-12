# -*- coding: utf-8 -*-
"""#19 利润口径 Mapper SQL 真库验证(TODO"SQL 兼容性红线":mapper XML 自定义 SQL 必须真库验证)。

对象:
  A. erp-finance/.../mapper/ProfitQueryMapper.xml 四条第一层语句
(从 XML 原文提取,测的就是要发布的;<include refid="profitLines"/> 原位展开,<if> 去标签留内容,
哑元参数值以文本注入——仅数字/带引号字面量,无注入面):

  1. selectProfitLinesAll        主查询(已支付三态过滤/时间窗/sku 过滤形态)行与排除断言
  2. selectProfitLines           同体分页形态(拦截器 LIMIT 语义)→ 手拼 LIMIT 验证语法
  3. sumCostByOrderItemIds       OUT_SHIP×发货单行聚合:SUM(-cost_amount) 与 sku 匹配防串行
  4. sumCommissionByPlatformItemIds COMMISSION 同键多行 SUM + 店铺分组键
  4b.#27① 数据权限店铺集        shopIds IN 非授权店铺集空集断言(2026-09-12,空集由调用方短路禁落 SQL)

  B. #19 周期口径(2026-09-11,docs/plans/19-profit-caliber.md):
  5. ProfitPeriodQueryMapper.xml sumSettlementFees 分费种聚合(报告隔离/带符号 SUM)
  6. platform_fee_rate/profit_period_report 建表与菜单 41/42/4101~4103 幂等重放
  7. profit_period_report uk(shop_id,settlement_id) 唯一键拦截重复派生

  C. #32 周期校差(2026-09-12):
  8. ProfitPeriodReportMapper.xml upsertPeriod ODKU upsert(VALUES 行别名 AS new,9.7.2 原生形态;
     同 uk 二次执行整行覆盖不重复建行,重算刷新幂等)

哑元行键统一 '__val__' 后缀,验证完删除,不碰业务数据。用法:python scripts/validate_profit_sql.py
"""
import io
import re
import sys
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parent.parent
XML = ROOT / 'erp-finance' / 'src' / 'main' / 'resources' / 'mapper' / 'ProfitQueryMapper.xml'
PERIOD_XML = ROOT / 'erp-finance' / 'src' / 'main' / 'resources' / 'mapper' / 'ProfitPeriodQueryMapper.xml'
PERIOD_REPORT_XML = ROOT / 'erp-finance' / 'src' / 'main' / 'resources' / 'mapper' / 'ProfitPeriodReportMapper.xml'
DDL_PATH = ROOT / 'docs' / 'sql' / '01_schema_init.sql'
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
PASSED = []


def load_props():
    props = {}
    for line in (ROOT / 'local.properties').read_text(encoding='utf-8', errors='ignore').splitlines():
        line = line.strip()
        if not line or line.startswith('#') or '=' not in line:
            continue
        k, v = line.split('=', 1)
        props[k.strip()] = v.strip()
    return props


def extract_statement(statement_id, drop_ifs=False, xml_path=XML, kind='select'):
    text = xml_path.read_text(encoding='utf-8')
    m = re.search(r'<%s\s+id="%s"[^>]*>(.*?)</%s>' % (kind, statement_id, kind), text, re.S)
    if not m:
        raise SystemExit(f'XML 中找不到语句 {statement_id}')
    body = m.group(1)
    include = re.search(r'<include refid="(\w+)"/>', body)
    if include:
        frag = re.search(r'<sql\s+id="%s">(.*?)</sql>' % include.group(1), text, re.S).group(1)
        body = body.replace(include.group(0), frag)
    body = re.sub(r'<!--.*?-->', ' ', body, flags=re.S)
    # foreach → 集合占位(open/close 括号在标签属性里,随标签消失;集合值以含括号 SQL 字面量注入)
    body = re.sub(r'<foreach[^>]*\bcollection="(\w+)"[^>]*>.*?</foreach>', r'%(\1)s', body, flags=re.S)
    # <where> 语义 = 输出 WHERE 关键字(首条件为静态三态,原位展开等价)
    body = body.replace('<where>', ' WHERE ').replace('</where>', ' ')
    if drop_ifs:  # 无参形态:模拟 MyBatis test 不成立,<if> 整块剔除(留内容会生成 = NULL 滤空全表)
        body = re.sub(r'<if[^>]*>.*?</if>', ' ', body, flags=re.S)
    # 去残余动态标签留内容:test 属性值内含 `>`(如 shopIds.size() > 0),[^>]* 会截断——
    # 引号段允许含 > 的标签形态整体剥离
    body = re.sub(r'</?(?:if|where|foreach|trim|choose|when|otherwise)\b(?:"[^"]*"|\'[^\']*\'|[^>"\'])*>', ' ', body)
    body = body.replace('&gt;', '>').replace('&lt;', '<')
    body = re.sub(r'#{(\w+)}', r'%(\1)s', body)                # #{p} → %(p)s 文本注入
    return re.sub(r'\s+', ' ', body).strip()


def drain_warnings(cur, label):
    cur.execute('SHOW WARNINGS')
    for row in cur.fetchall():
        print(f'  ⚠ [{label}] {row[0]} {row[1]}')


def ok(cur, label):
    drain_warnings(cur, label.split('.')[0])
    PASSED.append(label)
    print(f'  ✅ {label}')


def connect():
    """同 menu_tool.py 形态:只显式传 pymysql 认可的键(勿把 MYSQL_USERNAME 转成 username 透传)。"""
    p = load_props()
    return pymysql.connect(host=p['MYSQL_HOST'], port=int(p.get('MYSQL_PORT', 3306)),
        user=p['MYSQL_USERNAME'], password=p['MYSQL_PASSWORD'], database='erp',
        charset='utf8mb4', autocommit=False)


def replay_period_ddl_and_menus(cur):
    """#19 周期口径:两表 DDL 从正本逐字截取执行 + 菜单 41/42/4101~4103 幂等重放(已建库对齐)。"""
    sql_text = DDL_PATH.read_text(encoding='utf-8')
    for table in ('platform_fee_rate', 'profit_period_report'):
        m = re.search(r"CREATE TABLE IF NOT EXISTS %s \(.*?\n\) COMMENT '[^']*'" % table, sql_text, re.S)
        if not m:
            raise SystemExit(f'正本 SQL 中找不到建表段 {table}')
        cur.execute(m.group(0))
    cur.execute("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, perm_key, path, component, icon, sort) "
                "VALUES (41, 31, '平台费率', 2, 'finance:fee-rate:list', '/finance/fee-rates', "
                "'finance/fee-rate/index', 'Percent', 5),"
                "(42, 31, '周期利润', 2, 'finance:period:list', '/finance/profit-periods', "
                "'finance/profit-period/index', 'DataAnalysis', 6)")
    cur.execute("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, perm_key) VALUES "
                "(4101, 41, '新增', 3, 'finance:fee-rate:add'),"
                "(4102, 41, '编辑', 3, 'finance:fee-rate:edit'),"
                "(4103, 41, '删除', 3, 'finance:fee-rate:remove')")
    cur.execute("INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES "
                "(1,41),(1,4101),(1,4102),(1,4103),(1,42)")


# 哑元清理(逆序依赖):setup 带 commit 先于校验,失败运行会残留,故入口先预清扫一遍保证幂等重跑
CLEANUP_SQLS = [
    "DELETE FROM profit_period_report WHERE shop_id=990001",
    "DELETE FROM settlement_detail WHERE shop_id=990001",
    "DELETE FROM settlement_report WHERE shop_id=990001",
    "DELETE FROM delivery_order_item WHERE delivery_id=990001",
    "DELETE FROM delivery_order WHERE id=990001",
    "DELETE FROM inventory_flow WHERE sku_id=990001",
    "DELETE FROM inventory WHERE sku_id=990001",
    "DELETE FROM warehouse WHERE id=990001",
    "DELETE FROM shop_order_item WHERE order_id IN (990001,990002)",
    "DELETE FROM shop_order WHERE id IN (990001,990002)",
    "DELETE FROM shop WHERE id=990001",
]


def main():
    conn = connect()
    try:
        cur = conn.cursor()
        # 新表先幂等建表/菜单(首次跑库中无 #19 周期两表,后续 DELETE 才不炸)
        replay_period_ddl_and_menus(cur)
        conn.commit()
        ok(cur, 'period.建表/菜单种子幂等重放(platform_fee_rate/profit_period_report/41/42/4101~4103)')
        for sql in CLEANUP_SQLS:  # 防御性预清扫
            cur.execute(sql)
        conn.commit()
        # ── 哑元数据:店铺 990001/订单 WAIT_SHIP 990001(USD 100)+ WAIT_PAY 990002(被排除)──
        setup = [
            "INSERT INTO shop (id, shop_name, platform, status) VALUES (990001,'__val__利润店','AMAZON',1)",
            """INSERT INTO shop_order (id, shop_id, platform, platform_order_id, order_status, order_time,
               currency, exchange_rate) VALUES (990001,990001,'AMAZON','__val__PO1','WAIT_SHIP','2026-09-01 10:00:00','USD',1),
               (990002,990001,'AMAZON','__val__PO2','WAIT_PAY','2026-09-02 10:00:00','USD',1)""",
            """INSERT INTO shop_order_item (id, order_id, platform_order_item_id, platform_sku, sku_id,
               quantity, unit_price, item_amount, currency) VALUES
               (990001,990001,'__val__AMI1','__val__SKU-A',990001,2,50.0000,100.0000,'USD'),
               (990002,990002,'__val__AMI2','__val__SKU-B',990002,1,10.0000,10.0000,'USD')""",
            "INSERT INTO warehouse (id, wh_name, wh_type) VALUES (990001,'__val__仓','SELF')",
            "INSERT INTO inventory (id, sku_id, warehouse_id, qty_on_hand, qty_locked, qty_transit, qty_available) VALUES (990001,990001,990001,50,0,0,50)",
            """INSERT INTO inventory_flow (id, sku_id, warehouse_id, flow_type, quantity, before_qty, after_qty,
               biz_type, biz_id, unit_cost, cost_amount) VALUES
               (990001,990001,990001,'OUT_SHIP',-10,50,50,'DELIVERY_ORDER',990001,5.00000000,-50.00),
               (990002,990001,990001,'OUT_SHIP',-5,50,50,'DELIVERY_ORDER',990001,5.00000000,-25.00)""",
            "INSERT INTO delivery_order (id, order_id, shop_id, delivery_no, status, warehouse_id) VALUES (990001,990001,990001,'__val__DN1','SHIPPED',990001)",
            "INSERT INTO delivery_order_item (id, delivery_id, order_item_id, sku_id, ship_qty) VALUES (990001,990001,990001,990001,15)",
            """INSERT INTO settlement_detail (id, report_id, shop_id, order_id, order_item_id, fee_type, amount, posted_at)
               VALUES (990001,990001,990001,'__val__PO1','__val__AMI1','COMMISSION',-5.00,'2026-09-05 00:00:00'),
                      (990002,990001,990001,'__val__PO1','__val__AMI1','COMMISSION',-3.00,'2026-09-06 00:00:00'),
                      (990003,990001,990001,'__val__PO1','__val__AMI1','SALE',100.00,'2026-09-05 00:00:00'),
                      (990004,990001,990001,NULL,NULL,'TRANSFER',120.00,'2026-09-08 00:00:00'),
                      (990005,990001,990001,'__val__PO1','__val__AMI1','FBA_FEE',-7.00,'2026-09-05 00:00:00'),
                      (990006,990001,990001,NULL,NULL,'ADVERTISING',-2.00,'2026-09-05 00:00:00'),
                      (990007,990002,990001,'__val__PO2','__val__AMI2','COMMISSION',-9.00,'2026-09-07 00:00:00')""",
            "INSERT INTO settlement_report (id, shop_id, settlement_id, period_start, period_end, currency) VALUES "
            "(990001,990001,'__val__S1','2026-09-01 00:00:00','2026-09-30 00:00:00','USD'),"
            "(990002,990001,'__val__S2','2026-09-01 00:00:00','2026-09-30 00:00:00','USD')",
        ]
        for sql in setup:
            cur.execute(sql)
        conn.commit()

        # SQL 字面量注入(文本替换非参数化):字符串/日期自带引号,集合值含括号;禁 Python None(会渲染成 'None' 非法)
        # shopIds=#27① 数据权限店铺集(profitLines 公共片段 foreach→%(shopIds)s),传元组括号形态
        full = {'shopId': 990001, 'shopIds': '(990001)', 'platform': "'AMAZON'", 'skuId': 990001,
                'dateFrom': "'2026-09-01 00:00:00'", 'dateTo': "'2026-09-30 00:00:00'"}

        # 1) selectProfitLinesAll:全参数形态——已支付态过滤 + 店铺/平台/sku + 时间窗
        sql_all = extract_statement('selectProfitLinesAll')
        cur.execute(sql_all % full)
        rows = cur.fetchall()
        assert len(rows) == 1 and rows[0][0] == 990001 and rows[0][12] == 'USD', f'主查询行不符: {rows}'
        ok(cur, 'selectProfitLinesAll.已支付态+时间窗+sku过滤')
        # 1b) #27① 数据权限店铺集 IN 过滤:非授权店铺集应查不到任何行(空集由调用方短路禁落 SQL,防 IN ())
        cur.execute(sql_all % {**full, 'shopIds': '(990009)'})
        assert not cur.fetchall(), 'shopIds 非授权店铺集应为空'
        ok(cur, 'selectProfitLinesAll.#27① shopIds IN 非授权店铺集空集')
        # WAIT_PAY 单被排除(无参形态:模拟 MyBatis if 不成立,<if> 整块剔除)
        cur.execute(extract_statement('selectProfitLinesAll', drop_ifs=True))
        ids = {r[0] for r in cur.fetchall()}
        assert 990002 not in ids and 990001 in ids, f'未支付态未被排除: {ids}'
        ok(cur, 'selectProfitLinesAll.WAIT_PAY排除')

        # 2) selectProfitLines 分页形态:拦截器 LIMIT → 手拼验证语法
        sql_page = extract_statement('selectProfitLines') + ' LIMIT 0, 20'
        cur.execute(sql_page % full)
        ok(cur, 'selectProfitLines.LIMIT分页语法')

        # 3) 出库成本聚合:Σ(-cost_amount)=75,sku 匹配防串行
        sql_cost = extract_statement('sumCostByOrderItemIds')
        cur.execute(sql_cost % {'orderItemIds': '(990001, 990002)'})
        rows = cur.fetchall()
        assert len(rows) == 1 and rows[0][0] == 990001 and rows[0][1] == 75.0, f'成本聚合不符: {rows}'
        ok(cur, 'sumCostByOrderItemIds.聚合与符号')

        # 4) 佣金聚合:同键两行 SUM=-8,SALE 行不混入;分组键含 shop_id
        sql_com = extract_statement('sumCommissionByPlatformItemIds')
        cur.execute(sql_com % {'platformItemIds': "('__val__AMI1')"})
        rows = cur.fetchall()
        assert len(rows) == 1 and rows[0][0] == 990001 and rows[0][1] == '__val__AMI1' and rows[0][2] == -8.0, f'佣金聚合不符: {rows}'
        ok(cur, 'sumCommissionByPlatformItemIds.同键SUM与类型过滤')

        # 5) 周期口径:结算侧分费种聚合(报告 990001 五类,报告原符号;SALE 100/佣金 -8/TRANSFER 120/FBA -7/广告 -2)
        sql_fees = extract_statement('sumSettlementFees', xml_path=PERIOD_XML)
        cur.execute(sql_fees % {'reportId': 990001})
        fees = {r[0]: float(r[1]) for r in cur.fetchall()}
        assert fees == {'COMMISSION': -8.0, 'SALE': 100.0, 'TRANSFER': 120.0,
                        'FBA_FEE': -7.0, 'ADVERTISING': -2.0}, f'分费种聚合不符: {fees}'
        ok(cur, 'sumSettlementFees.分费种带符号聚合')
        # 报告隔离:990002 只见自己的 COMMISSION -9,不串 990001
        cur.execute(sql_fees % {'reportId': 990002})
        fees2 = {r[0]: float(r[1]) for r in cur.fetchall()}
        assert fees2 == {'COMMISSION': -9.0}, f'报告间聚合串行: {fees2}'
        ok(cur, 'sumSettlementFees.报告隔离')

        # 6) 周期行 uk(shop_id,settlement_id):同源报告第二条派生必须被唯一键拦(幂等落库根本)
        period_insert = ("INSERT INTO profit_period_report (shop_id, settlement_id, period_start, period_end, currency) "
                         "VALUES (990001,990001,'2026-09-01 00:00:00','2026-09-30 00:00:00','USD')")
        cur.execute(period_insert)
        blocked = False
        try:
            cur.execute(period_insert)
        except pymysql.err.IntegrityError:
            blocked = True
            conn.rollback()
        assert blocked, 'profit_period_report uk(shop_id,settlement_id) 未拦截重复派生行'
        ok(cur, 'profit_period_report.uk(shop_id,settlement_id)派生幂等')

        # 7) #32 周期行 ODKU upsert(ProfitPeriodReportMapper.xml):VALUES 行别名 AS new(9.7.2 原生形态,
        #    ODKU 内列引用 new. 限定)——同 uk 二次执行整行覆盖不重复建行(重算刷新幂等,单语句原子)
        sql_upsert = extract_statement('upsertPeriod', xml_path=PERIOD_REPORT_XML, kind='insert')
        upsert_base = {'shopId': 990001, 'settlementId': 990001,
                       'periodStart': "'2026-09-01 00:00:00'", 'periodEnd': "'2026-09-30 00:00:00'",
                       'currency': "'USD'", 'rateUsed': 7.0, 'rateMissing': 0,
                       'orderIncome': 700.0, 'settleIncome': 595.0, 'settleCommission': -105.0,
                       'fbaFee': -7.0, 'otherFee': -2.0, 'orderCommission': -105.0, 'orderProfit': 195.0,
                       'diffIncome': 0.0, 'diffCommission': 0.0, 'diffFlag': 0,
                       'diffRemark': "'__val__平'", 'status': "'OK'"}
        cur.execute(sql_upsert % upsert_base)
        # 同 uk 重放=重算覆盖:status/diff 列被第二行刷掉
        cur.execute(sql_upsert % {**upsert_base, 'diffFlag': 1, 'diffIncome': 12.34,
                                  'diffRemark': "'__val__不平'", 'status': "'DIFF'"})
        cur.execute("SELECT status, diff_flag, diff_income, diff_remark, rate_used "
                    "FROM profit_period_report WHERE shop_id=990001 AND settlement_id=990001")
        rows = cur.fetchall()
        assert len(rows) == 1 and rows[0][0] == 'DIFF' and rows[0][1] == 1 \
            and abs(float(rows[0][2]) - 12.34) < 1e-9 and rows[0][3] == '__val__不平' \
            and abs(float(rows[0][4]) - 7.0) < 1e-9, f'ODKU upsert 幂等覆盖不符: {rows}'
        ok(cur, 'upsertPeriod.ODKU行别名AS new幂等覆盖')

        # ── 清理哑元(复用预清扫列表) ──
        for sql in CLEANUP_SQLS:
            cur.execute(sql)
        conn.commit()
        cur.execute("SELECT COUNT(*) FROM shop_order_item WHERE order_id IN (990001,990002)")
        assert cur.fetchone()[0] == 0, '哑元清理不彻底'
        ok(cur, 'cleanup.哑元行清零')

        expected = 12
        print(f'\nALL GREEN ({len(PASSED)} checks)' if len(PASSED) == expected
              else f'\nCHECK FAILED ({len(PASSED)}/{expected})')
    finally:
        conn.close()


if __name__ == '__main__':
    main()
