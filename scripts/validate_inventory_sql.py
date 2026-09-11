# -*- coding: utf-8 -*-
"""仓内作业(盘点/调拨)动账 SQL 真库验证 —— 2026-09-11 docs/plans/warehouse-ops.md §六。

为什么必须真库:mapper 的 @Update 守卫条件(算术与 WHERE 全下 SQL)单测 mock 永远测不出形态错误
(TODO "MyBatis-Plus 的坑" / "SQL 兼容性红线");本脚本把**要发布的 SQL 原文**从 Java 注解里抠出来
(不是手抄,防漂移),在开发库(MySQL 9.7.2 LTS)上逐条真跑:

  0. 前置:补建 stocktake_order/stocktake_item/transfer_order/transfer_order_item(CREATE IF NOT EXISTS)
     + 重放 sys_menu / sys_role_menu 的 INSERT IGNORE 段(幂等,补齐 #29/仓内作业菜单)
  1. 盘点差异调整(ADJUST)口径:可用量原子增减 + 余额守卫下推 WHERE
     —— 盘盈 +3 命中(affected=1,四量正确位移,不变量 可用=在库-占用 成立)
     —— 盘亏 -20 超可用命中 0 行(守卫拒绝,存量不动)
  2. 三方差:inventory 数量 = Σ inventory_flow.quantity;sku_cost_state.total_amount = Σ flow.cost_amount
     (移动加权「同价进出不动加权」:盘盈后 avg 不变)
  3. 调拨两腿:TRANSFER_OUT 负 + TRANSFER_IN 正(目标仓无行走 change 首建形态),两仓镜像;流水两条
     biz_type=TRANSFER_ORDER、biz_id 同指
  4. 状态机守卫真库:casStatus DRAFT→(X) 命中 1 次、重复执行 affected=0(重复确认/重复开始被拦)

哑元行用 9 亿段 sku/warehouse id,验证完删除,不碰业务数据。
用法: "C:/Users/lenovo/.workbuddy/binaries/python/envs/default/Scripts/python.exe" scripts/validate_inventory_sql.py
"""
import io
import re
import sys
from decimal import Decimal
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parent.parent
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
PASSED = []

DUMMY_SKU = 900000001
DUMMY_WH_A = 900000002
DUMMY_WH_B = 900000003
DUMMY_ORDER_ID = 900000009


def load_props():
    props = {}
    for line in (ROOT / 'local.properties').read_text(encoding='utf-8', errors='ignore').splitlines():
        line = line.strip()
        if not line or line.startswith('#') or '=' not in line:
            continue
        k, v = line.split('=', 1)
        props[k.strip()] = v.strip()
    return props


def extract_create(schema_text, table):
    m = re.search(r'CREATE TABLE IF NOT EXISTS %s\s*\(.*?\n\)\s*COMMENT\s*=?\s*\'[^\']*\'\s*;' % table,
                  schema_text, re.S)
    if not m:
        raise SystemExit(f'01_schema_init.sql 中找不到 {table} 建表段')
    return m.group(0)


def extract_inserts(schema_text, table):
    return [m.group(0) for m in re.finditer(r'INSERT IGNORE INTO %s\b.*?;' % table, schema_text, re.S)]


def extract_annotation_sql(java_text, method):
    """从 @Update("... " + "...") 注解抠出 SQL 原文(去引号与 + 连接符),按方法名定位。"""
    mi = java_text.find(method + '(')
    if mi < 0:
        raise SystemExit(f'Java 源中找不到方法 {method}')
    ai = java_text.rfind('@Update(', 0, mi)
    if ai < 0:
        raise SystemExit(f'方法 {method} 前找不到 @Update 注解')
    i = ai + len('@Update(')
    depth, in_str, out = 1, False, []
    while i < len(java_text):
        c = java_text[i]
        if in_str:
            if c == '\\':
                out.append(java_text[i:i + 2])
                i += 2
                continue
            if c == '"':
                in_str = False
            else:
                out.append(c)
        else:
            if c == '"':
                in_str = True
            elif c == '(':
                depth += 1
            elif c == ')':
                depth -= 1
                if depth == 0:
                    break
        i += 1
    return ''.join(out)


def bind(sql, **params):
    for k, v in params.items():
        if v is None:
            sql = sql.replace('#{%s}' % k, 'NULL')
        elif isinstance(v, str):
            sql = sql.replace('#{%s}' % k, "'%s'" % v.replace("'", "''"))
        else:
            sql = sql.replace('#{%s}' % k, str(v))
    return re.sub(r'\s+', ' ', sql).strip()


def ok(cur, label):
    PASSED.append(label)
    print(f'  ✅ {label}')


def clear(cur):
    cur.execute('DELETE FROM inventory_flow WHERE sku_id=%s', (DUMMY_SKU,))
    cur.execute('DELETE FROM inventory WHERE sku_id=%s', (DUMMY_SKU,))
    cur.execute('DELETE FROM sku_cost_state WHERE sku_id=%s', (DUMMY_SKU,))
    cur.execute('DELETE FROM stocktake_order WHERE id=%s', (DUMMY_ORDER_ID,))
    cur.execute('DELETE FROM transfer_order WHERE id=%s', (DUMMY_ORDER_ID,))


def main():
    props = load_props()
    conn = pymysql.connect(host=props['MYSQL_HOST'], port=int(props['MYSQL_PORT']),
                           user=props['MYSQL_USERNAME'], password=props['MYSQL_PASSWORD'],
                           database='erp', charset='utf8mb4', autocommit=True)
    cur = conn.cursor()
    cur.execute('SELECT VERSION()')
    ver = cur.fetchone()[0]
    print(f'MySQL {ver} —— 仓内作业动账 SQL 真库验证(SQL 原文取自 Mapper @Update 注解)\n')

    schema = (ROOT / 'docs/sql/01_schema_init.sql').read_text(encoding='utf-8')
    # 先剥离整行 -- 注释:菜单段注释里含 `;`(如 "已读过滤;页面动作..."),不剥离会截断语句
    schema = '\n'.join(l for l in schema.splitlines() if not l.lstrip().startswith('--'))
    inv_java = (ROOT / 'erp-inventory/src/main/java/com/own/erp/inventory/mapper/InventoryMapper.java').read_text(encoding='utf-8')
    st_java = (ROOT / 'erp-inventory/src/main/java/com/own/erp/inventory/mapper/StocktakeOrderMapper.java').read_text(encoding='utf-8')
    tf_java = (ROOT / 'erp-inventory/src/main/java/com/own/erp/inventory/mapper/TransferOrderMapper.java').read_text(encoding='utf-8')

    # ---------- 0. 前置:建表 + 菜单种子(全幂等) ----------
    for t in ('stocktake_order', 'stocktake_item', 'transfer_order', 'transfer_order_item'):
        cur.execute(extract_create(schema, t))
    for stmt in extract_inserts(schema, 'sys_menu') + extract_inserts(schema, 'sys_role_menu'):
        cur.execute(stmt)
    cur.execute("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='erp' "
                "AND table_name IN ('stocktake_order','stocktake_item','transfer_order','transfer_order_item')")
    assert cur.fetchone()[0] == 4, '四张仓内作业表未全部就绪'
    ok(cur, '0. 建表 + 菜单种子幂等重放(4 表就绪)')

    clear(cur)

    # ---------- 1. 盘点差异调整:ADJUST 可用量原子增减 + 余额守卫 ----------
    adjust_sql = extract_annotation_sql(inv_java, 'updateAvailableDelta')
    assert 'qty_available + #{quantity} >= 0' in adjust_sql, f'ADJUST 守卫形态异常:{adjust_sql}'
    cur.execute('INSERT INTO inventory (sku_id, warehouse_id, qty_on_hand, qty_locked, qty_transit, qty_available) '
                'VALUES (%s,%s,10,0,0,10)', (DUMMY_SKU, DUMMY_WH_A))
    # 盘盈 +3:命中
    assert cur.execute(bind(adjust_sql, skuId=DUMMY_SKU, warehouseId=DUMMY_WH_A, quantity=3)) == 1, '盘盈应命中 1 行'
    row = cur.fetchone()
    cur.execute('SELECT qty_on_hand, qty_locked, qty_transit, qty_available FROM inventory '
                'WHERE sku_id=%s AND warehouse_id=%s', (DUMMY_SKU, DUMMY_WH_A))
    on_hand, locked, transit, avail = cur.fetchone()
    assert (on_hand, locked, transit, avail) == (13, 0, 0, 13), f'盘盈后四量异常:{(on_hand, locked, transit, avail)}'
    assert avail == on_hand - locked, '不变量 可用=在库-占用 不成立'
    # 盘亏 -20:超可用,守卫拒绝(affected=0,存量不动)
    assert cur.execute(bind(adjust_sql, skuId=DUMMY_SKU, warehouseId=DUMMY_WH_A, quantity=-20)) == 0, '超可用盘亏应被守卫拦'
    cur.execute('SELECT qty_on_hand, qty_available FROM inventory WHERE sku_id=%s AND warehouse_id=%s',
                (DUMMY_SKU, DUMMY_WH_A))
    assert cur.fetchone() == (13, 13), '守卫拒绝后存量不应变化'
    ok(cur, '1. ADJUST 盘盈 +3 命中 / 盘亏 -20 超可用守卫下推 WHERE 拦(affected=0)')

    # ---------- 2. 三方差:inventory ↔ inventory_flow ↔ sku_cost_state ----------
    # 模拟 change() 写流水的成本契约(InventoryCostService.ADJUST=settleAtAvgCost:按当时加权价同价进出,不动加权)
    cur.execute('INSERT INTO sku_cost_state (sku_id, total_qty, total_amount, avg_cost) VALUES (%s,10,100.00,10.00000000)',
                (DUMMY_SKU,))
    cur.execute('INSERT INTO inventory_flow (sku_id, warehouse_id, flow_type, quantity, before_qty, after_qty, '
                'biz_type, unit_cost, cost_amount, remark) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)',
                (DUMMY_SKU, DUMMY_WH_A, 'IN_RETURN', 10, 0, 10, 'VAL', Decimal('10.00000000'), Decimal('100.00'), '期初'))
    cur.execute('INSERT INTO inventory_flow (sku_id, warehouse_id, flow_type, quantity, before_qty, after_qty, '
                'biz_type, biz_id, unit_cost, cost_amount, remark) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)',
                (DUMMY_SKU, DUMMY_WH_A, 'ADJUST', 3, 10, 13, 'STOCKTAKE', DUMMY_ORDER_ID,
                 Decimal('10.00000000'), Decimal('30.00'), '盘点调整'))
    cur.execute('UPDATE sku_cost_state SET total_qty = total_qty + 3, total_amount = total_amount + 3 * avg_cost '
                'WHERE sku_id=%s', (DUMMY_SKU,))
    cur.execute('SELECT COALESCE(SUM(quantity),0) FROM inventory_flow WHERE sku_id=%s AND warehouse_id=%s',
                (DUMMY_SKU, DUMMY_WH_A))
    flow_qty = cur.fetchone()[0]
    cur.execute('SELECT COALESCE(SUM(cost_amount),0) FROM inventory_flow WHERE sku_id=%s', (DUMMY_SKU,))
    flow_amount = cur.fetchone()[0]
    cur.execute('SELECT total_qty, total_amount, avg_cost FROM sku_cost_state WHERE sku_id=%s', (DUMMY_SKU,))
    total_qty, total_amount, avg_cost = cur.fetchone()
    assert int(flow_qty) == avail, f'Σ流水数量 {flow_qty} != inventory 可用 {avail}'
    assert total_qty == avail, f'成本账结存 {total_qty} != inventory 可用 {avail}'
    assert Decimal(flow_amount) == Decimal(total_amount) == Decimal('130.00'), f'成本金额不一致 {flow_amount}/{total_amount}'
    assert Decimal(total_amount) / Decimal(total_qty) == Decimal(avg_cost), '盘盈后移动加权价应不变(同价进出)'
    assert str(avg_cost) == '10.00000000', f'盘盈不应改加权价,实际 {avg_cost}'
    ok(cur, '2. 三方差 inventory=Σflow=成本账结存;盘盈按当时加权价入账,加权价不变(avg=10)')

    # ---------- 3. 调拨两腿:TRANSFER_OUT 负 + TRANSFER_IN 正,两仓镜像 ----------
    assert cur.execute(bind(adjust_sql, skuId=DUMMY_SKU, warehouseId=DUMMY_WH_A, quantity=-5)) == 1, 'TRANSFER_OUT 应命中'
    # 目标仓无行 → change() 首建形态(仅限通用形正数:在库=Δ、可用=Δ)
    cur.execute('INSERT INTO inventory (sku_id, warehouse_id, qty_on_hand, qty_locked, qty_transit, qty_available) '
                'VALUES (%s,%s,5,0,0,5)', (DUMMY_SKU, DUMMY_WH_B))
    cur.execute('INSERT INTO inventory_flow (sku_id, warehouse_id, flow_type, quantity, before_qty, after_qty, '
                'biz_type, biz_id, remark) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)',
                (DUMMY_SKU, DUMMY_WH_A, 'TRANSFER_OUT', -5, 13, 8, 'TRANSFER_ORDER', DUMMY_ORDER_ID, '调拨单:TR001'))
    cur.execute('INSERT INTO inventory_flow (sku_id, warehouse_id, flow_type, quantity, before_qty, after_qty, '
                'biz_type, biz_id, remark) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)',
                (DUMMY_SKU, DUMMY_WH_B, 'TRANSFER_IN', 5, 0, 5, 'TRANSFER_ORDER', DUMMY_ORDER_ID, '调拨单:TR001'))
    cur.execute('SELECT warehouse_id, qty_on_hand, qty_available FROM inventory WHERE sku_id=%s ORDER BY warehouse_id',
                (DUMMY_SKU,))
    legs = {r[0]: (r[1], r[2]) for r in cur.fetchall()}
    assert legs[DUMMY_WH_A] == (8, 8), f'调出仓应减 5: {legs[DUMMY_WH_A]}'
    assert legs[DUMMY_WH_B] == (5, 5), f'调入仓应为 5: {legs[DUMMY_WH_B]}'
    cur.execute("SELECT flow_type, COUNT(*) FROM inventory_flow WHERE sku_id=%s AND biz_type='TRANSFER_ORDER' "
                "AND biz_id=%s GROUP BY flow_type", (DUMMY_SKU, DUMMY_ORDER_ID))
    legs_flow = dict(cur.fetchall())
    assert legs_flow == {'TRANSFER_OUT': 1, 'TRANSFER_IN': 1}, f'流水两腿异常: {legs_flow}'
    ok(cur, '3. 调拨两腿 TRANSFER_OUT(-5)/TRANSFER_IN(+5) 两仓镜像,流水 2 条 biz_type=TRANSFER_ORDER 同指')

    # ---------- 4. 状态机条件更新守卫真库 ----------
    st_cas = extract_annotation_sql(st_java, 'casStatus')
    tf_cas = extract_annotation_sql(tf_java, 'casStatus')
    assert 'AND status = #{fromStatus}' in st_cas and 'AND status = #{fromStatus}' in tf_cas, 'casStatus 守卫形态异常'
    cur.execute('INSERT INTO stocktake_order (id, stocktake_no, warehouse_id, scope_type, status) '
                "VALUES (%s,'__val__ST',%s,'SKU_SET','DRAFT')", (DUMMY_ORDER_ID, DUMMY_WH_A))
    assert cur.execute(bind(st_cas, id=DUMMY_ORDER_ID, fromStatus='DRAFT', toStatus='COUNTING')) == 1, '开始盘点应命中'
    assert cur.execute(bind(st_cas, id=DUMMY_ORDER_ID, fromStatus='DRAFT', toStatus='COUNTING')) == 0, '重复开始应被拦'
    cur.execute('INSERT INTO transfer_order (id, transfer_no, from_warehouse_id, to_warehouse_id, status) '
                "VALUES (%s,'__val__TR',%s,%s,'DRAFT')", (DUMMY_ORDER_ID, DUMMY_WH_A, DUMMY_WH_B))
    assert cur.execute(bind(tf_cas, id=DUMMY_ORDER_ID, fromStatus='DRAFT', toStatus='CONFIRMED')) == 1, '确认调拨应命中'
    assert cur.execute(bind(tf_cas, id=DUMMY_ORDER_ID, fromStatus='DRAFT', toStatus='CONFIRMED')) == 0, '重复确认应被拦'
    ok(cur, '4. casStatus 条件更新:首次 affected=1,重复 affected=0(重复开始/重复确认被拦)')

    clear(cur)
    conn.close()
    print(f'\n全部通过:{len(PASSED)} 项 —— 仓内作业动账 SQL 真库验证收口(引擎 {ver})')


if __name__ == '__main__':
    main()
