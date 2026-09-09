#!/usr/bin/env python3
"""#22 商品分析 SQL 真库验证(2026-09-09,开发库):
   验证 ReportQueryMapper.xml 新增四条语句形态在真库可执行且口径正确(哑元键 99xxxx,跑完清理,幂等可重跑):
   ①SKU 选项 UNION ALL 双数据面并集 + GROUP BY 去重 + ORDER BY 别名 + LIMIT
   ②单 SKU 翻译行(join 不滤已删同 #7 口径)
   ③日销量 GROUP BY stat_date 窗口过滤
   ④日库存跨仓 SUM(两仓两行合并)。
   哑元日期取 2026-01-04~06(真库销量/快照在近 30 天窗口,互不干扰);哑元 SKU 990011/990012 避开 #20 脚本 990001-3。"""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).parent))
from menu_tool import connect  # noqa: E402

SKU_A, SKU_B = 990011, 990012
WH_A, WH_B = 990011, 990012


def setup(cur):
    """哑元数据:SKU_A 有销量+跨仓快照(验跨仓 SUM),SKU_B 仅库存无销量(验选项并集)。"""
    cur.execute("INSERT IGNORE INTO product (id, spu_code, name, status) VALUES (99011, '__GA_SPU__', '商品分析哑元SPU', 1)")
    cur.execute("INSERT IGNORE INTO product_sku (id, product_id, sku_code, status) VALUES (%s, 99011, '__GA_SKU_A__', 1)", (SKU_A,))
    cur.execute("INSERT IGNORE INTO product_sku (id, product_id, sku_code, status) VALUES (%s, 99011, '__GA_SKU_B__', 1)", (SKU_B,))
    cur.execute("INSERT IGNORE INTO warehouse (id, wh_name, wh_type, status) VALUES (%s, '商品分析哑元仓A', 'SELF', 1)", (WH_A,))
    cur.execute("INSERT IGNORE INTO warehouse (id, wh_name, wh_type, status) VALUES (%s, '商品分析哑元仓B', 'SELF', 1)", (WH_B,))
    cur.execute("INSERT IGNORE INTO order_sales_daily (id, stat_date, sku_id, qty_sold) VALUES "
                "(990011, '2026-01-05', %s, 3), (990012, '2026-01-06', %s, 7)", (SKU_A, SKU_A))
    cur.execute("INSERT IGNORE INTO inventory_snapshot_daily (id, stat_date, sku_id, warehouse_id, "
                "qty_on_hand, qty_locked, qty_transit, qty_available) VALUES "
                "(990011, '2026-01-04', %s, %s, 11, 0, 0, 11), (990012, '2026-01-04', %s, %s, 12, 0, 0, 12), "
                "(990013, '2026-01-06', %s, %s, 8, 0, 0, 8), (990014, '2026-01-04', %s, %s, 5, 0, 0, 5)",
                (SKU_A, WH_A, SKU_A, WH_B, SKU_A, WH_A, SKU_B, WH_A))
    cur.execute("COMMIT")


def cleanup(cur):
    cur.execute("DELETE FROM order_sales_daily WHERE id IN (990011, 990012)")
    cur.execute("DELETE FROM inventory_snapshot_daily WHERE id BETWEEN 990011 AND 990014")
    cur.execute("DELETE FROM product_sku WHERE id IN (990011, 990012)")
    cur.execute("DELETE FROM product WHERE id = 99011")
    cur.execute("DELETE FROM warehouse WHERE id IN (990011, 990012)")
    cur.execute("COMMIT")


def main():
    conn = connect()
    cur = conn.cursor()
    ok = True
    try:
        setup(cur)
        # ① SKU 选项:UNION ALL 并集 + GROUP BY sku_id 去重 + ORDER BY SELECT 别名(与 XML 逐字同形态)
        cur.execute(
            "SELECT t.sku_id AS skuId, MAX(t.sku_code) AS skuCode, MAX(t.product_name) AS productName "
            "FROM ("
            " SELECT d.sku_id, ps.sku_code, p.name AS product_name FROM order_sales_daily d"
            " LEFT JOIN product_sku ps ON d.sku_id = ps.id LEFT JOIN product p ON ps.product_id = p.id"
            " UNION ALL"
            " SELECT s.sku_id, ps.sku_code, p.name AS product_name FROM inventory_snapshot_daily s"
            " LEFT JOIN product_sku ps ON s.sku_id = ps.id LEFT JOIN product p ON ps.product_id = p.id"
            ") t GROUP BY t.sku_id ORDER BY skuCode LIMIT 500")
        opts = {r[0]: r[1] for r in cur.fetchall()}
        ok1 = (opts.get(SKU_A) == '__GA_SKU_A__' and opts.get(SKU_B) == '__GA_SKU_B__')
        print(f"[1] options={opts} -> {'OK' if ok1 else 'FAIL'}")
        ok &= ok1
        # ② 单 SKU 翻译行
        cur.execute("SELECT ps.id, ps.sku_code, p.name FROM product_sku ps "
                    "LEFT JOIN product p ON ps.product_id = p.id WHERE ps.id = %s", (SKU_A,))
        row = cur.fetchone()
        ok2 = (row is not None and row[1] == '__GA_SKU_A__' and row[2] == '商品分析哑元SPU')
        print(f"[2] skuOption={row} -> {'OK' if ok2 else 'FAIL'}")
        ok &= ok2
        # ③ 日销量:窗口内 GROUP BY stat_date 排序(01-05:3 / 01-06:7)
        cur.execute("SELECT stat_date, SUM(qty_sold) FROM order_sales_daily WHERE sku_id = %s "
                    "AND stat_date >= '2026-01-05' AND stat_date <= '2026-01-06' "
                    "GROUP BY stat_date ORDER BY stat_date", (SKU_A,))
        sales = cur.fetchall()
        ok3 = (len(sales) == 2 and int(sales[0][1]) == 3 and int(sales[1][1]) == 7)
        print(f"[3] sales={sales} -> {'OK' if ok3 else 'FAIL'}")
        ok &= ok3
        # ④ 日库存跨仓 SUM:01-04 两仓 11+12=23 / 01-06 单仓 8
        cur.execute("SELECT stat_date, SUM(qty_on_hand) FROM inventory_snapshot_daily WHERE sku_id = %s "
                    "AND stat_date >= '2026-01-01' AND stat_date <= '2026-01-06' "
                    "GROUP BY stat_date ORDER BY stat_date", (SKU_A,))
        stock = cur.fetchall()
        ok4 = (len(stock) == 2 and int(stock[0][1]) == 23 and int(stock[1][1]) == 8)
        print(f"[4] stock={stock} -> {'OK' if ok4 else 'FAIL'}")
        ok &= ok4
        print("ALL GREEN" if ok else "CHECK FAILED")
    finally:
        try:
            cleanup(cur)
        except Exception:
            pass
        conn.close()


if __name__ == "__main__":
    main()
