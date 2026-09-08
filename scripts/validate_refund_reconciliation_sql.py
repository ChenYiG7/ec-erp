# -*- coding: utf-8 -*-
"""#19④ 退款勾稽 Mapper SQL 真库验证(TODO"SQL 兼容性红线":mapper XML 自定义 SQL 必须真库验证)。

对象 = erp-finance/src/main/resources/mapper/RefundReconciliationMapper.xml 两条聚合语句
(纯静态 SQL 无动态标签,从 XML 原文提取,测的就是要发布的;#{p} 文本注入规则同 validate_profit_sql.py):

  1. sumAftersaleRefundByPlatformOrder      售后侧聚合:REFUNDED/COMPLETED 且金额非空,
                                            PENDING 未决态排除,经 shop_order 翻译平台订单号
  2. sumSettlementRefundByPlatformOrder     结算侧聚合:仅 PARSED 报告 REFUND 行,FAILED 报告
                                            与非 REFUND 费用行排除,SUM(−amount) 转正

哑元行键统一 '__val__' 前缀/shop_id=990001,验证完删除,不碰业务数据。
用法:python scripts/validate_refund_reconciliation_sql.py
"""
from decimal import Decimal
import io
import re
import sys
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parent.parent
XML = ROOT / 'erp-finance' / 'src' / 'main' / 'resources' / 'mapper' / 'RefundReconciliationMapper.xml'
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


def extract_statement(statement_id):
    text = XML.read_text(encoding='utf-8')
    m = re.search(r'<select\s+id="%s"[^>]*>(.*?)</select>' % statement_id, text, re.S)
    if not m:
        raise SystemExit(f'XML 中找不到语句 {statement_id}')
    body = re.sub(r'<!--.*?-->', ' ', m.group(1), flags=re.S)
    body = body.replace('&gt;', '>').replace('&lt;', '<')
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
    """同 validate_profit_sql.py 形态:只显式传 pymysql 认可的键。"""
    p = load_props()
    return pymysql.connect(host=p['MYSQL_HOST'], port=int(p.get('MYSQL_PORT', 3306)),
        user=p['MYSQL_USERNAME'], password=p['MYSQL_PASSWORD'], database='erp',
        charset='utf8mb4', autocommit=False)


# 哑元清理(逆序依赖):入口先预清扫保证幂等重跑
CLEANUP_SQLS = [
    "DELETE FROM settlement_detail WHERE shop_id=990001",
    "DELETE FROM settlement_report WHERE shop_id=990001",
    "DELETE FROM aftersale_order WHERE shop_id=990001",
    "DELETE FROM shop_order WHERE id IN (990001,990002)",
    "DELETE FROM shop WHERE id=990001",
]


def main():
    conn = connect()
    try:
        cur = conn.cursor()
        for sql in CLEANUP_SQLS:  # 防御性预清扫
            cur.execute(sql)
        conn.commit()

        # ── 哑元数据:店铺 990001;订单 990001(platform_order_id=__val__PO1)/990002(__val__PO2)──
        # 售后:990001 REFUNDED 29.99 USD(参与)/ 990002 PENDING 10.00(未决态排除)/
        #       990003 挂订单 990002 REFUNDED 20.00(结算侧无行 → 售后侧多出一键)
        # 结算:报告 990001 PARSED USD / 990002 FAILED USD(排除);
        #       明细 REFUND -29.99@PO1(PARSED,参与)/ REFUND -999.99@PO1(FAILED,排除)/
        #       COMMISSION -5.00@PO1(非 REFUND 类型排除)
        setup = [
            "INSERT INTO shop (id, shop_name, platform, status) VALUES (990001,'__val__勾稽店','AMAZON',1)",
            """INSERT INTO shop_order (id, shop_id, platform, platform_order_id, order_status, order_time,
               currency, exchange_rate) VALUES (990001,990001,'AMAZON','__val__PO1','COMPLETED','2026-09-01 10:00:00','USD',1),
               (990002,990001,'AMAZON','__val__PO2','COMPLETED','2026-09-02 10:00:00','USD',1)""",
            """INSERT INTO aftersale_order (id, aftersale_no, shop_id, platform_refund_id, order_id, type,
               status, refund_amount, currency) VALUES
               (990001,'__val__AS1',990001,'__val__R1',990001,'REFUND_ONLY','REFUNDED',29.9900,'USD'),
               (990002,'__val__AS2',990001,'__val__R2',990001,'REFUND_ONLY','PENDING',10.0000,'USD'),
               (990003,'__val__AS3',990001,'__val__R3',990002,'REFUND_ONLY','COMPLETED',20.0000,'USD')""",
            """INSERT INTO settlement_report (id, shop_id, settlement_id, period_start, period_end, currency, status)
               VALUES (990001,990001,'__val__S1','2026-09-01 00:00:00','2026-09-30 00:00:00','USD','PARSED'),
                      (990002,990001,'__val__S2','2026-09-01 00:00:00','2026-09-30 00:00:00','USD','FAILED')""",
            """INSERT INTO settlement_detail (id, report_id, shop_id, order_id, order_item_id, fee_type, amount, posted_at)
               VALUES (990001,990001,990001,'__val__PO1','__val__AMI1','REFUND',-29.99,'2026-09-05 00:00:00'),
                      (990002,990002,990001,'__val__PO1','__val__AMI1','REFUND',-999.99,'2026-09-05 00:00:00'),
                      (990003,990001,990001,'__val__PO1','__val__AMI1','COMMISSION',-5.00,'2026-09-05 00:00:00')""",
        ]
        for sql in setup:
            cur.execute(sql)
        conn.commit()

        # 1) 售后侧聚合:REFUNDED/COMPLETED 且金额非空,经 shop_order 翻译平台订单号
        # (真库可能存在业务数据行,断言只针对哑元键,业务行不碰不判)
        cur.execute(extract_statement('sumAftersaleRefundByPlatformOrder'))
        rows = {(r[0], r[1]): r for r in cur.fetchall() if r[0] == 990001}
        assert (990001, '__val__PO1') in rows and (990001, '__val__PO2') in rows, f'售后侧哑元行缺失: {rows}'
        assert rows[(990001, '__val__PO1')][2] == 'USD', f'币种列不符: {rows}'
        assert rows[(990001, '__val__PO1')][3] == Decimal('29.99'), f'售后侧 Σ 不符(应为 29.99,PENDING 排除): {rows}'
        assert rows[(990001, '__val__PO2')][3] == Decimal('20.00'), f'售后侧 PO2 Σ 不符: {rows}'
        ok(cur, 'sumAftersaleRefundByPlatformOrder(终态过滤/平台订单号翻译/金额聚合)')

        # 2) 结算侧聚合:仅 PARSED 报告 REFUND 行,SUM(−amount) 转正
        cur.execute(extract_statement('sumSettlementRefundByPlatformOrder'))
        rows = {(r[0], r[1]): r for r in cur.fetchall() if r[0] == 990001}
        assert (990001, '__val__PO1') in rows, f'结算侧哑元行缺失: {rows}'
        assert rows[(990001, '__val__PO1')][3] == Decimal('29.99'), f'结算侧 Σ 转正不符(应为 29.99): {rows}'
        assert (990001, '__val__PO2') not in rows, '结算侧不应有 PO2(无 REFUND 行)'
        ok(cur, 'sumSettlementRefundByPlatformOrder(PARSED 过滤/类型过滤/负值转正)')

    finally:
        cur = conn.cursor()
        for sql in reversed(CLEANUP_SQLS):  # 哑元清理,不碰业务数据
            cur.execute(sql)
        conn.commit()
        conn.close()

    print(f'\nALL GREEN: {len(PASSED)} 项验证通过(哑元已清理)')


if __name__ == '__main__':
    main()
