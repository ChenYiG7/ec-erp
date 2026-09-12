#!/usr/bin/env python3
"""#31 收付款/回款 开发库对齐(2026-09-11):
   ① payment_record/payment_alloc 建表(与 docs/sql/01_schema_init.sql 逐字同源,CREATE IF NOT EXISTS 幂等);
   ② supplier.settle_days 加列(information_schema 判存,幂等);
   ③ sys_menu 40/4001~4003 + sys_role_menu admin 绑定(INSERT IGNORE 幂等);
   ④ 存量 PARSED 结算报告补派生回款流水(NOT EXISTS + uk_ref 双幂等,重跑安全)。
   连接信息读 local.properties(键=环境变量名),不含硬编码密码。跑完打印自检计数。

   真库派生/聚合 SQL 验证另见 scripts/validate_payment_sql.py。
"""
import sys
import io
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from menu_tool import connect  # noqa: E402

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

DDL = [
    """CREATE TABLE IF NOT EXISTS payment_record (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    payment_no    VARCHAR(32) NOT NULL COMMENT '流水号(PAY+yyyyMMdd+4位seq,服务端生成)',
    direction     VARCHAR(8)  NOT NULL COMMENT '资金方向:EXPENSE付款/INCOME回款',
    biz_type      VARCHAR(32) NOT NULL COMMENT '业务类型:PURCHASE_PAYMENT采购付款/SETTLEMENT_RECEIPT结算回款/MANUAL_ADJUST手工调整',
    party_type    VARCHAR(16) NOT NULL COMMENT '往来方类型:SUPPLIER供应商/PLATFORM平台/OTHER其他',
    party_id      BIGINT      NULL COMMENT '往来方ID(supplier.id / shop.id;OTHER 可空)',
    amount        DECIMAL(12,4) NOT NULL COMMENT '原币金额(恒正,收付方向看 direction)',
    currency      CHAR(3)     NOT NULL DEFAULT 'CNY' COMMENT '币种(ISO 4217;采购付款强制CNY与本位币采购总额勾稽)',
    exchange_rate DECIMAL(12,8) NULL COMMENT '折算汇率快照(1 currency=rate CNY;落库时 resolveRate 按 paid_at 回溯冻结,CNY=1,无报价NULL)',
    amount_cny    DECIMAL(12,4) NULL COMMENT '折算本位币金额(缺汇率为NULL,查询面缺口计数不静默)',
    paid_at       DATETIME    NOT NULL COMMENT '收付款时间',
    method        VARCHAR(32) NULL COMMENT '结算方式(银行转账/支付宝/平台打款等,走 sys_dict)',
    ref_type      VARCHAR(32) NULL COMMENT '源单据类型:SETTLEMENT_REPORT结算报告(派生流水幂等键)',
    ref_id        BIGINT      NULL COMMENT '源单据ID(settlement_report.id)',
    status        VARCHAR(8)  NOT NULL DEFAULT 'NORMAL' COMMENT '状态:NORMAL正常/VOIDED已作废(作废留痕禁物理删;分摊随作废经 join NORMAL 失效)',
    remark        VARCHAR(255) NULL COMMENT '备注',
    created_by    BIGINT      NULL COMMENT '创建人(sys_user.id;系统派生流水为NULL)',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0=正常,非0=已删(值=被删行id),见 TODO#7',
    UNIQUE KEY uk_payment_no (payment_no),
    UNIQUE KEY uk_ref (ref_type, ref_id, deleted),
    KEY idx_party (party_type, party_id, paid_at),
    KEY idx_paid_at (paid_at)
) COMMENT='资金流水(收付款/回款统一账;一单多付/一付多单经 payment_alloc 分摊)'""",
    """CREATE TABLE IF NOT EXISTS payment_alloc (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    payment_id     BIGINT NOT NULL COMMENT '资金流水ID(payment_record.id)',
    alloc_biz_type VARCHAR(16) NOT NULL COMMENT '分摊业务类型:PURCHASE采购单',
    alloc_biz_id   BIGINT NOT NULL COMMENT '分摊业务单据ID(purchase_order.id)',
    amount         DECIMAL(12,4) NOT NULL COMMENT '分摊金额(同流水原币;Σ分摊≤流水金额允许部分挂账;按单已付+本次≤采购总额超额拦截)',
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_payment_alloc (payment_id, alloc_biz_type, alloc_biz_id),
    KEY idx_alloc_biz (alloc_biz_type, alloc_biz_id)
) COMMENT='资金流水分摊(一付多单;采购单已付=Σ本表关联NORMAL流水,查询时聚合不冗余存储)'""",
]

MENU_SQL = [
    "INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, perm_key, path, component, icon, sort) "
    "VALUES (40, 31, '资金流水', 2, 'finance:payment:list', '/finance/payments', 'finance/payment/index', 'CreditCard', 4)",
    "INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, perm_key) VALUES "
    "(4001, 40, '采购付款登记', 3, 'finance:payment:purchase'),"
    "(4002, 40, '手工登记', 3, 'finance:payment:manual'),"
    "(4003, 40, '作废', 3, 'finance:payment:void')",
    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES "
    "(1,40),(1,4001),(1,4002),(1,4003)",
]

# 存量 PARSED 报告补派生回款(uk_ref + NOT EXISTS 双幂等):
# paid_at 锚点取报告周期止(depositDate V1 未入库);汇率按周期止回溯,CNY 短路 1,无报价 NULL(缺口不静默)
BACKFILL_SQL = """
INSERT INTO payment_record
  (payment_no, direction, biz_type, party_type, party_id, amount, currency,
   exchange_rate, amount_cny, paid_at, method, ref_type, ref_id, status, remark,
   created_at, updated_at, deleted)
SELECT CONCAT('PAYBAK', DATE_FORMAT(sr.period_end, '%Y%m%d'), LPAD(sr.id, 6, '0')),
       'INCOME', 'SETTLEMENT_RECEIPT', 'PLATFORM', sr.shop_id, sr.transfer_amount, sr.currency,
       CASE WHEN sr.currency = 'CNY' THEN 1
            ELSE (SELECT er.rate FROM exchange_rate er
                  WHERE er.currency = sr.currency AND er.quoted_at <= sr.period_end
                  ORDER BY er.quoted_at DESC LIMIT 1) END,
       CASE WHEN sr.transfer_amount IS NULL THEN NULL
            WHEN sr.currency = 'CNY' THEN sr.transfer_amount
            ELSE ROUND(sr.transfer_amount * (
                  SELECT er.rate FROM exchange_rate er
                  WHERE er.currency = sr.currency AND er.quoted_at <= sr.period_end
                  ORDER BY er.quoted_at DESC LIMIT 1), 4) END,
       sr.period_end, '平台打款', 'SETTLEMENT_REPORT', sr.id, 'NORMAL',
       CONCAT('结算报告 ', sr.settlement_id, ' 存量补派生'),
       NOW(), NOW(), 0
FROM settlement_report sr
WHERE sr.status = 'PARSED'
  AND sr.transfer_amount > 0
  AND NOT EXISTS (SELECT 1 FROM payment_record pr
                  WHERE pr.ref_type = 'SETTLEMENT_REPORT' AND pr.ref_id = sr.id AND pr.deleted = 0)
"""


def column_exists(cur, table, column):
    cur.execute("SELECT COUNT(*) FROM information_schema.columns "
                "WHERE table_schema='erp' AND table_name=%s AND column_name=%s", (table, column))
    return cur.fetchone()[0] > 0


def main():
    conn = connect()
    try:
        cur = conn.cursor()
        for sql in DDL:
            cur.execute(sql)
        if not column_exists(cur, 'supplier', 'settle_days'):
            cur.execute("ALTER TABLE supplier ADD COLUMN settle_days INT NULL "
                        "COMMENT '账期天数(V1仅展示,到期提醒随预警引擎评估,TODO#31)'")
            print('supplier.settle_days added')
        for sql in MENU_SQL:
            cur.execute(sql)
        cur.execute(BACKFILL_SQL)
        backfilled = cur.rowcount
        conn.commit()

        # 自检:两表存在 + 菜单/授权行数 + 派生存量=PARSED有打款报告数
        cur.execute("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='erp' "
                    "AND table_name IN ('payment_record','payment_alloc')")
        tables = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM sys_menu WHERE id IN (40,4001,4002,4003)")
        menus = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM sys_role_menu WHERE role_id=1 AND menu_id IN (40,4001,4002,4003)")
        binds = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM settlement_report sr WHERE sr.status='PARSED' AND sr.transfer_amount>0")
        expect = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM payment_record WHERE biz_type='SETTLEMENT_RECEIPT' AND deleted=0")
        derived = cur.fetchone()[0]
        print(f"tables={tables}/2 menus={menus}/4 role_binds={binds}/4 "
              f"backfilled={backfilled} derived_total={derived}/expected_{expect}")
        print("ALL GREEN" if tables == 2 and menus == 4 and binds == 4 and derived == expect else "CHECK FAILED")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
