# -*- coding: utf-8 -*-
"""#19③ 利润核算 V1 Mapper SQL 真库验证(TODO"SQL 兼容性红线":mapper XML 自定义 SQL 必须真库验证)。

对象 = erp-finance/src/main/resources/mapper/ProfitQueryMapper.xml 四条语句
(从 XML 原文提取,测的就是要发布的;<include refid="profitLines"/> 原位展开,<if> 去标签留内容,
哑元参数值以文本注入——仅数字/带引号字面量,无注入面):

  1. selectProfitLinesAll        主查询(已支付三态过滤/时间窗/sku 过滤形态)行与排除断言
  2. selectProfitLines           同体分页形态(拦截器 LIMIT 语义)→ 手拼 LIMIT 验证语法
  3. sumCostByOrderItemIds       OUT_SHIP×发货单行聚合:SUM(-cost_amount) 与 sku 匹配防串行
  4. sumCommissionByPlatformItemIds COMMISSION 同键多行 SUM + 店铺分组键

哑元行键统一 '__val__' 后缀,验证完删除,不碰业务数据。用法:python scripts/validate_profit_sql.py
"""
import io
import re
import sys
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parent.parent
XML = ROOT / 'erp-finance' / 'src' / 'main' / 'resources' / 'mapper' / 'ProfitQueryMapper.xml'
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


def extract_statement(statement_id, drop_ifs=False):
    text = XML.read_text(encoding='utf-8')
    m = re.search(r'<select\s+id="%s"[^>]*>(.*?)</select>' % statement_id, text, re.S)
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
    body = re.sub(r'</?(if|where|foreach|trim|choose|when|otherwise)[^>]*>', ' ', body)  # 去残余动态标签留内容
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


# 哑元清理(逆序依赖):setup 带 commit 先于校验,失败运行会残留,故入口先预清扫一遍保证幂等重跑
CLEANUP_SQLS = [
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
                      (990003,990001,990001,'__val__PO1','__val__AMI1','SALE',100.00,'2026-09-05 00:00:00')""",
            "INSERT INTO settlement_report (id, shop_id, settlement_id, period_start, period_end, currency) VALUES (990001,990001,'__val__S1','2026-09-01 00:00:00','2026-09-30 00:00:00','USD')",
        ]
        for sql in setup:
            cur.execute(sql)
        conn.commit()

        # SQL 字面量注入(文本替换非参数化):字符串/日期自带引号,集合值含括号;禁 Python None(会渲染成 'None' 非法)
        full = {'shopId': 990001, 'platform': "'AMAZON'", 'skuId': 990001,
                'dateFrom': "'2026-09-01 00:00:00'", 'dateTo': "'2026-09-30 00:00:00'"}

        # 1) selectProfitLinesAll:全参数形态——已支付态过滤 + 店铺/平台/sku + 时间窗
        sql_all = extract_statement('selectProfitLinesAll')
        cur.execute(sql_all % full)
        rows = cur.fetchall()
        assert len(rows) == 1 and rows[0][0] == 990001 and rows[0][12] == 'USD', f'主查询行不符: {rows}'
        ok(cur, 'selectProfitLinesAll.已支付态+时间窗+sku过滤')
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

        # ── 清理哑元(复用预清扫列表) ──
        for sql in CLEANUP_SQLS:
            cur.execute(sql)
        conn.commit()
        cur.execute("SELECT COUNT(*) FROM shop_order_item WHERE order_id IN (990001,990002)")
        assert cur.fetchone()[0] == 0, '哑元清理不彻底'
        ok(cur, 'cleanup.哑元行清零')

        print(f'\nALL GREEN ({len(PASSED)} checks)' if len(PASSED) == 6 else f'\nCHECK FAILED ({len(PASSED)}/6)')
    finally:
        conn.close()


if __name__ == '__main__':
    main()
