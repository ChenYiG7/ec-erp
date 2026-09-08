# -*- coding: utf-8 -*-
"""Mapper SQL 真库兼容性验证(9.7.2 原生形态)。

开发库 = MySQL 9.7.2 LTS(TODO"SQL 兼容性红线"):mapper XML 自定义 SQL 一律 9.7.2 原生形态——
VALUES 行 ODKU 用行别名 `AS new`(列引用必须全限定:new.=插入值、表名前缀=冲突行现值);
INSERT...SELECT 行别名不支持(9.7.2 实测 1064),用源表/派生表别名;"每组取最新"用窗口函数。
本脚本从 mapper XML 原文提取 SQL(测的就是要发布的)逐条真库验证:

  1. ShopOrderMapper.upsert           行别名 AS new:插入→更新(值变化 affected=2)→清哑元行
  2. ShopProductMapper.upsert         行别名 + COALESCE(NULL 不覆盖)→清
  3. ShopProductSkuMapper.upsert      行别名 + 三列 COALESCE 保留语义→清(连带哑元 shop_product)
  4. AftersaleOrderMapper.upsert      行别名 + CASE 条件推进/不回退(表名限定=冲突行现值,new.列=插入值)→清
  5. OrderSalesDailyMapper.upsertWindow 派生表别名(INSERT...SELECT 不支持行别名,9.7.2 实测 1064),
     真跑 30 天窗口,核对行数/合计 vs 直接 SQL
  6. PurchaseOrderItemMapper.findLatestSupplierRows ROW_NUMBER() vs 逐 SKU 直查(oracle)等价
  7. InventorySnapshotDailyMapper     upsert(源表别名,9.7.2 规范形态)两遍幂等 + listSeries 单仓/跨仓形态

哑元行均用 '__val__' 后缀键,验证完删除,不碰业务数据;每步语句后 SHOW WARNINGS 汇总弃用告警。
用法: "C:/Users/lenovo/.workbuddy/binaries/python/envs/default/python.exe" scripts/validate_mapper_sql.py
"""
import io
import re
import sys
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parent.parent
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


def extract_xml_sql(xml_path, statement_id):
    text = xml_path.read_text(encoding='utf-8')
    m = re.search(r'<(insert|select|update)\s+id="%s"[^>]*>(.*?)</\1>' % statement_id, text, re.S)
    if not m:
        raise SystemExit(f'XML 中找不到语句 {statement_id}')
    body = re.sub(r'<!--.*?-->', ' ', m.group(2), flags=re.S)
    body = re.sub(r'</?if[^>]*>', ' ', body)                       # 去 <if> 标签留内容
    body = body.replace('<![CDATA[', '').replace(']]>', '')         # 去 CDATA 包裹
    body = body.replace('&gt;', '>').replace('&lt;', '<')
    return re.sub(r'\s+', ' ', body).strip()


def ok(cur, label):
    drain_warnings(cur, label.split('.')[0])
    PASSED.append(label)
    print(f'  ✅ {label}')


def q1(cur, sql, args=None):
    cur.execute(sql, args or ())
    return cur.fetchone()


WARNINGS = []


def drain_warnings(cur, label):
    """收走上一条语句的服务器告警(重点探测 ODKU 弃用),不吞错误。"""
    try:
        ws = cur.show_warnings() or ()
    except Exception:
        ws = ()
    for level, code, msg in ws:
        WARNINGS.append((label, int(code), str(msg)))
        print(f'    ⚠️ [{label}] {level} {code}: {str(msg)[:110]}')


def main():
    props = load_props()
    conn = pymysql.connect(host=props['MYSQL_HOST'], port=int(props['MYSQL_PORT']),
                           user=props['MYSQL_USERNAME'], password=props['MYSQL_PASSWORD'],
                           database='erp', charset='utf8mb4', autocommit=True)
    cur = conn.cursor()
    cur.execute('SELECT VERSION()')
    ver = cur.fetchone()[0]
    cur.execute('SELECT @@version_comment')
    comment = cur.fetchone()[0]
    print(f'{comment} {ver} —— 逐条验证 9.7.2 原生形态 mapper SQL(弃用告警探测开启)\n')

    # 历史哑元键一次性清理(旧脚本命名 '__57val__' 残留,若上次运行中断遗留)
    for dsql in ("DELETE FROM shop_order WHERE platform_order_id='__57val__'",
                 "DELETE FROM shop_product WHERE platform_product_id IN ('__57val__','__57val3__')",
                 "DELETE FROM shop_product_sku WHERE seller_sku='__57val__'",
                 "DELETE FROM aftersale_order WHERE platform_refund_id='__57val__'"):
        cur.execute(dsql)

    # ---------- 1. ShopOrderMapper.upsert ----------
    xml = ROOT / 'erp-order/src/main/resources/mapper/ShopOrderMapper.xml'
    up = extract_xml_sql(xml, 'upsert')
    assert 'AS new' in up and 'VALUES(' not in up.split('ON DUPLICATE')[1], '形态应为行别名 AS new'
    shop_id = q1(cur, 'SELECT id FROM shop ORDER BY id LIMIT 1')[0]
    cur.execute("DELETE FROM shop_order WHERE platform_order_id='__val__'")  # 残留清理
    sql_ins = (up.replace('#{shopId}', str(shop_id)).replace('#{platform}', "'AMAZON'")
               .replace('#{platformOrderId}', "'__val__'").replace('#{orderStatus}', "'WAIT_PAY'")
               .replace('#{fulfillmentChannel}', "'SELF_FULFILL'").replace('#{orderTime}', "'2026-09-08 10:00:00'")
               .replace('#{paidTime}', 'NULL').replace('#{buyerNote}', 'NULL').replace('#{receiverName}', "'测试'")
               .replace('#{receiverPhone}', 'NULL').replace('#{receiverCountry}', 'NULL')
               .replace('#{receiverState}', 'NULL').replace('#{receiverCity}', 'NULL')
               .replace('#{receiverAddress}', 'NULL').replace('#{receiverZip}', 'NULL')
               .replace('#{currency}', "'USD'").replace('#{exchangeRate}', '7.2')
               .replace('#{orderAmount}', '100.00').replace('#{shippingFee}', '0').replace('#{discountAmount}', '0')
               .replace('#{rawJson}', "'{}'"))
    a1 = cur.execute(sql_ins)
    assert a1 == 1, f'首插 affected={a1}'
    sql_upd = sql_ins.replace('100.00', '188.00')  # 数值字面量(无引号),order_amount 唯一命中
    a2 = cur.execute(sql_upd)
    assert a2 == 2, f'更新路径(值变化)affected={a2},预期 2'
    val = q1(cur, "SELECT order_amount FROM shop_order WHERE platform_order_id='__val__'")[0]
    assert val == 188, f'更新后值异常 {val}'  # DECIMAL(12,4) 回读 Decimal('188.0000')
    cur.execute("DELETE FROM shop_order WHERE platform_order_id='__val__'")
    ok(cur, '1. ShopOrderMapper.upsert 行别名 插入/更新/清哑元')

    # ---------- 2. ShopProductMapper.upsert ----------
    xml = ROOT / 'erp-shop/src/main/resources/mapper/ShopProductMapper.xml'
    up = extract_xml_sql(xml, 'upsert')
    assert 'AS new' in up and 'VALUES(' not in up.split('ON DUPLICATE')[1], '形态应为行别名 AS new'
    sql_ins = (up.replace('#{shopId}', str(shop_id)).replace('#{platformProductId}', "'__val__'")
               .replace('#{platformSkuId}', "'VAL_SKU_A'").replace('#{listingStatus}', "'ACTIVE'")
               .replace('#{lastSyncAt}', "'2026-09-08 10:00:00'"))
    cur.execute("DELETE FROM shop_product WHERE platform_product_id='__val__'")  # 残留清理
    assert cur.execute(sql_ins) == 1
    sql_upd = (sql_ins.replace("'VAL_SKU_A'", 'NULL').replace("'ACTIVE'", "'INACTIVE'")
               .replace("'2026-09-08 10:00:00'", "'2026-09-08 11:00:00'"))
    cur.execute(sql_upd)
    sku_id_col, status_col = q1(cur, "SELECT platform_sku_id, listing_status FROM shop_product WHERE platform_product_id='__val__'")
    assert sku_id_col == 'VAL_SKU_A', f'COALESCE 应保留原 platform_sku_id,实际 {sku_id_col}'
    assert status_col == 'INACTIVE', f'非空新值应覆盖,实际 {status_col}'
    cur.execute("DELETE FROM shop_product WHERE platform_product_id='__val__'")
    ok(cur, '2. ShopProductMapper.upsert COALESCE(NULL 不覆盖)+非空覆盖')

    # ---------- 3. ShopProductSkuMapper.upsert ----------
    xml = ROOT / 'erp-shop/src/main/resources/mapper/ShopProductSkuMapper.xml'
    up = extract_xml_sql(xml, 'upsert')
    assert 'AS new' in up and 'VALUES(' not in up.split('ON DUPLICATE')[1], '形态应为行别名 AS new'
    # shop_product 哑元行已在第 2 步删除,重建一个专用于本步
    # 残留清理(上轮失败可能留下哑元行)
    cur.execute("DELETE FROM shop_product_sku WHERE seller_sku='__val__'")
    cur.execute("DELETE FROM shop_product WHERE platform_product_id='__val3__'")
    cur.execute("INSERT INTO shop_product (shop_id, platform_product_id) VALUES (%s,'__val3__')", (shop_id,))
    prod_id = q1(cur, "SELECT id FROM shop_product WHERE platform_product_id='__val3__'")[0]
    sql_ins = (up.replace('#{shopProductId}', str(prod_id)).replace('#{sellerSku}', "'__val__'")
               .replace('#{quantity}', '5').replace('#{price}', '10.00').replace('#{currency}', "'USD'"))
    assert cur.execute(sql_ins) == 1
    sql_upd = sql_ins.replace('#{quantity}', 'NULL').replace('10.00', '12.00').replace("'USD'", 'NULL')
    cur.execute(sql_upd)
    qty, price, ccy = q1(cur, "SELECT quantity, price, currency FROM shop_product_sku WHERE seller_sku='__val__'")
    assert qty == 5 and price == 12 and ccy == 'USD', f'NULL 不覆盖语义异常: {qty},{price},{ccy}'
    cur.execute("DELETE FROM shop_product_sku WHERE seller_sku='__val__'")
    cur.execute("DELETE FROM shop_product WHERE id=%s", (prod_id,))
    ok(cur, '3. ShopProductSkuMapper.upsert COALESCE 三列保留语义')

    # ---------- 4. AftersaleOrderMapper.upsert ----------
    xml = ROOT / 'erp-aftersale/src/main/resources/mapper/AftersaleOrderMapper.xml'
    up = extract_xml_sql(xml, 'upsert')
    assert 'AS new' in up and 'VALUES(' not in up.split('ON DUPLICATE')[1], '形态应为行别名 AS new'
    order_id = q1(cur, 'SELECT id FROM shop_order ORDER BY id LIMIT 1')[0]
    cur.execute("DELETE FROM aftersale_order WHERE platform_refund_id='__val__'")  # 残留清理
    base = (up.replace('#{aftersaleNo}', "'__val__'").replace('#{shopId}', str(shop_id))
            .replace('#{platformRefundId}', "'__val__'").replace('#{orderId}', str(order_id))
            .replace('#{type}', "'REFUND_ONLY'").replace('#{refundAmount}', '50.00')
            .replace('#{currency}', "'USD'").replace('#{reason}', "'val'"))
    cur.execute(base.replace('#{status}', "'PENDING'"))
    cur.execute(base.replace('#{status}', "'REJECTED'"))
    st = q1(cur, "SELECT status FROM aftersale_order WHERE platform_refund_id='__val__'")[0]
    assert st == 'REJECTED', f'未决态应被平台终态推进,实际 {st}'
    cur.execute(base.replace('#{status}', "'PENDING'"))
    st = q1(cur, "SELECT status FROM aftersale_order WHERE platform_refund_id='__val__'")[0]
    assert st == 'REJECTED', f'终态/人工已决不应被回退,实际 {st}'
    cur.execute("DELETE FROM aftersale_order WHERE platform_refund_id='__val__'")
    ok(cur, '4. AftersaleOrderMapper.upsert CASE 条件推进/不回退(表名限定=现值,new.列=插入值)')

    # ---------- 5. OrderSalesDailyMapper.upsertWindow ----------
    xml = ROOT / 'erp-order/src/main/resources/mapper/OrderSalesDailyMapper.xml'
    up = extract_xml_sql(xml, 'upsertWindow')
    assert ') AS s' in up and 'AS new' not in up, 'INSERT...SELECT 聚合应经派生表别名 s(行别名不适用于 INSERT...SELECT)'
    sql = up.replace('#{startDate}', "'2026-08-10 00:00:00'").replace('#{endDate}', "'2026-09-09 00:00:00'")
    affected = cur.execute(sql)
    rows, total = q1(cur, 'SELECT COUNT(*), COALESCE(SUM(qty_sold),0) FROM order_sales_daily')
    direct = q1(cur, ("SELECT COUNT(*), COALESCE(SUM(q.sq),0) FROM (SELECT DATE(o.paid_time) d, i.sku_id, "
                      "SUM(i.quantity) sq FROM shop_order o JOIN shop_order_item i ON i.order_id=o.id "
                      "WHERE o.order_status IN ('WAIT_SHIP','SHIPPED','COMPLETED') AND o.paid_time IS NOT NULL "
                      "AND o.paid_time >= '2026-08-10 00:00:00' AND o.paid_time < '2026-09-09 00:00:00' "
                      "AND i.sku_id IS NOT NULL GROUP BY d, i.sku_id) q"))
    assert (rows, str(total)) == (direct[0], str(direct[1])), f'upsert 结果 {rows},{total} != 直查 {direct}'
    ok(cur, f'5. OrderSalesDailyMapper.upsertWindow 派生表别名(affected={affected}, 行数={rows}, 合计={total})')

    # ---------- 6. PurchaseOrderItemMapper.findLatestSupplierRows ----------
    xml = ROOT / 'erp-purchase/src/main/resources/mapper/PurchaseOrderItemMapper.xml'
    sel = extract_xml_sql(xml, 'findLatestSupplierRows')
    assert 'OVER (PARTITION BY' in sel and 'NOT EXISTS' not in sel, '应为 ROW_NUMBER() 窗口函数形态'
    cur.execute("SELECT DISTINCT i.sku_id FROM purchase_order_item i "
                "JOIN purchase_order po ON po.id=i.po_id WHERE po.status != 'DRAFT' ORDER BY i.sku_id")
    sku_ids = [r[0] for r in cur.fetchall()]
    if not sku_ids:
        ok(cur, '6. PurchaseOrderItemMapper 窗口函数无非 DRAFT 采购明细可核对,跳过等价断言')
    else:
        in_list = ','.join(str(x) for x in sku_ids)
        sql_new = re.sub(r'<foreach[^>]*>.*?</foreach>', f'({in_list})', sel, flags=re.S)
        sql_new = re.sub(r'\s+', ' ', sql_new)
        cur.execute(sql_new)
        got = {r[0]: (r[1], r[2], r[3], r[4], r[5]) for r in cur.fetchall()}
        # 逐 SKU 直查 oracle:ORDER BY created_at DESC, id DESC LIMIT 1(与窗口取 rn=1 同序)
        assert set(got.keys()) == set(sku_ids), f'窗口结果 SKU 集合不一致: {set(got.keys()) ^ set(sku_ids)}'
        for sid in sku_ids:
            exp = q1(cur, ("SELECT po.supplier_id, s.name, i.purchase_price, po.po_no, po.created_at "
                           "FROM purchase_order_item i JOIN purchase_order po ON po.id=i.po_id "
                           "JOIN supplier s ON s.id=po.supplier_id "
                           "WHERE po.status != 'DRAFT' AND i.sku_id=%s "
                           "ORDER BY po.created_at DESC, i.id DESC LIMIT 1"), (sid,))
            g = got[sid]
            assert g[0] == exp[0] and g[1] == exp[1] and float(g[2]) == float(exp[2]) \
                and g[3] == exp[3] and str(g[4])[:10] == str(exp[4])[:10], \
                f'sku={sid} 不一致: got={g} expect={exp}'
        ok(cur, f'6. PurchaseOrderItemMapper ROW_NUMBER 与逐 SKU 直查等价({len(sku_ids)} SKU 逐行核对)')

    # ---------- 7. InventorySnapshotDailyMapper ----------
    xml = ROOT / 'erp-inventory/src/main/resources/mapper/InventorySnapshotDailyMapper.xml'
    up = extract_xml_sql(xml, 'upsertSnapshot')
    assert 'src.qty_on_hand' in up and 'AS new' not in up, 'INSERT...SELECT 直传应为源表别名引用形态(9.7.2 规范)'
    sql = up.replace('#{statDate}', "'2026-09-08'")
    cur.execute(sql)
    cur.execute(sql)  # 幂等复跑
    inv = q1(cur, 'SELECT COUNT(*), COALESCE(SUM(qty_available),0) FROM inventory')
    snap = q1(cur, "SELECT COUNT(*), COALESCE(SUM(qty_available),0) FROM inventory_snapshot_daily WHERE stat_date='2026-09-08'")
    assert inv == snap and inv[0] > 0, f'快照 {snap} != inventory {inv}'
    # listSeries 单仓(<if warehouseId != null> 分支)
    sku_id_r, wh_id_r = q1(cur, 'SELECT sku_id, warehouse_id FROM inventory LIMIT 1')
    sel = extract_xml_sql(xml, 'listSeries')
    # 保留 != null 分支内容,剔除 == null 分支的常量 0
    one = sel.replace('#{skuId}', str(sku_id_r)).replace('#{warehouseId}', str(wh_id_r))
    one = one.replace("#{from}", "'2026-09-01'").replace("#{to}", "'2026-09-08'").replace("#{limit}", '10')
    one = re.sub(r'warehouse_id\s+0\s+AS warehouseId', 'warehouse_id AS warehouseId', one)  # 展开两分支为单仓分支
    cur.execute(re.sub(r'\s+', ' ', one))
    r = cur.fetchone()
    exp = q1(cur, 'SELECT qty_available FROM inventory WHERE sku_id=%s AND warehouse_id=%s', (sku_id_r, wh_id_r))[0]
    assert r and r[6] == exp, f'单仓形态异常 rows={r} expect={exp}'
    # 跨仓聚合(== null 分支:常量 0 顶位 + 去 AND 条件)
    agg = (f"SELECT stat_date AS statDate, sku_id AS skuId, 0 AS warehouseId, SUM(qty_on_hand) AS qtyOnHand, "
           f"SUM(qty_locked) AS qtyLocked, SUM(qty_transit) AS qtyTransit, SUM(qty_available) AS qtyAvailable "
           f"FROM inventory_snapshot_daily WHERE sku_id={sku_id_r} AND stat_date >= '2026-09-01' "
           f"AND stat_date <= '2026-09-08' GROUP BY stat_date, sku_id ORDER BY stat_date LIMIT 10")
    cur.execute(agg)
    r2 = cur.fetchone()
    exp2 = q1(cur, 'SELECT COALESCE(SUM(qty_available),0) FROM inventory WHERE sku_id=%s', (sku_id_r,))[0]
    assert r2 and r2[6] == exp2, f'跨仓聚合异常 rows={r2} expect={exp2}'
    ok(cur, f'7. InventorySnapshotDailyMapper upsert 幂等({snap[0]} 行)+listSeries 单仓/跨仓')

    print(f'\n全部通过:{len(PASSED)} 项 —— 9.7.2 原生形态真库验证收口(引擎 {ver})')
    dep = [w for w in WARNINGS if 'deprecat' in w[2].lower()]
    if dep:
        print(f'弃用告警 {len(dep)} 条(语句级,功能可用):')
        for label, code, msg in dep[:6]:
            print(f'  [{label}] {code}: {msg[:130]}')
    else:
        print('无弃用告警。')
    conn.close()


if __name__ == '__main__':
    main()
