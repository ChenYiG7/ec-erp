#!/usr/bin/env python3
"""#20 报表域 SQL 真库验证(2026-09-08,开发库 MySQL 9.7.2):
   验证 ReportQueryMapper.xml 四条聚合语句形态在真库可执行且口径正确(哑元键 990xxx,跑完清理,幂等可重跑):
   ①销售日报 GROUP BY stat_date ②销售周报 WEEKDAY 周起点 ③SKU 明细 join 翻译(不滤已删)+LIMIT 排序
   ④库存快照指定日全行 ⑤MAX 快照日。哑元日期取 2026-01-04~07(真库销量/快照数据在近 30 天窗口,互不干扰)。"""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).parent))
from menu_tool import connect  # noqa: E402

SKU_A, SKU_B, SKU_C = 990001, 990002, 990003


def setup(cur):
    """哑元数据:两个 SKU + 已删 SPU 挂的第三个 SKU(验 join 不滤已删)+ 三天销量 + 快照日两行。"""
    cur.execute("INSERT IGNORE INTO product (id, spu_code, name, status) VALUES (99001, '__RPT_SPU__', '报表哑元SPU', 1)")
    cur.execute("INSERT IGNORE INTO product_sku (id, product_id, sku_code, status) VALUES (%s, 99001, '__RPT_SKU_A__', 1)", (SKU_A,))
    cur.execute("INSERT IGNORE INTO product_sku (id, product_id, sku_code, status) VALUES (%s, 99001, '__RPT_SKU_B__', 1)", (SKU_B,))
    cur.execute("INSERT IGNORE INTO warehouse (id, wh_name, wh_type, status) VALUES (99001, '报表哑元仓', 'SELF', 1)")
    cur.execute("INSERT IGNORE INTO order_sales_daily (id, stat_date, sku_id, qty_sold) VALUES "
                "(990001, '2026-01-05', %s, 3), (990002, '2026-01-05', %s, 5), (990003, '2026-01-06', %s, 7)",
                (SKU_A, SKU_B, SKU_A))
    cur.execute("INSERT IGNORE INTO inventory_snapshot_daily (id, stat_date, sku_id, warehouse_id, "
                "qty_on_hand, qty_locked, qty_transit, qty_available) VALUES "
                "(990001, '2026-01-04', %s, 99001, 10, 2, 5, 8), (990002, '2026-01-04', %s, 99001, 20, 0, 0, 20)",
                (SKU_A, SKU_B))
    # 已删 SPU(deleted=被删行id,#7 逻辑删形态):验 join 不滤已删,历史销量名称仍可读
    cur.execute("INSERT IGNORE INTO product (id, spu_code, name, status, deleted) VALUES (99002, '__RPT_SPU_DEL__', '已删哑元SPU', 1, 99002)")
    cur.execute("INSERT IGNORE INTO product_sku (id, product_id, sku_code, status) VALUES (%s, 99002, '__RPT_SKU_C__', 1)", (SKU_C,))
    cur.execute("INSERT IGNORE INTO order_sales_daily (id, stat_date, sku_id, qty_sold) VALUES (990004, '2026-01-07', %s, 9)", (SKU_C,))
    cur.execute("COMMIT")


def cleanup(cur):
    cur.execute("DELETE FROM order_sales_daily WHERE id BETWEEN 990001 AND 990004")
    cur.execute("DELETE FROM inventory_snapshot_daily WHERE id BETWEEN 990001 AND 990002")
    cur.execute("DELETE FROM product_sku WHERE id IN (990001, 990002, 990003)")
    cur.execute("DELETE FROM product WHERE id IN (99001, 99002)")
    cur.execute("DELETE FROM warehouse WHERE id = 99001")
    cur.execute("COMMIT")


def main():
    conn = connect()
    cur = conn.cursor()
    ok = True
    try:
        setup(cur)
        # ① 销售日报:窗口聚合按日排序(哑元窗口内无真库数据,断言自包含)
        cur.execute("SELECT stat_date, SUM(qty_sold), COUNT(DISTINCT sku_id) FROM order_sales_daily "
                    "WHERE stat_date BETWEEN '2026-01-05' AND '2026-01-07' GROUP BY stat_date ORDER BY stat_date")
        rows = cur.fetchall()
        ok &= len(rows) == 3 and str(rows[0][0]) == '2026-01-05'
        ok &= (rows[0][1] == 8 and rows[0][2] == 2 and rows[1][1] == 7 and rows[2][1] == 9)
        # ② 销售周报:WEEKDAY 周一起点(1/5~1/7 同周,起点 2025-12-29?不——1/5 是周一,WEEKDAY=0,起点=1/5)
        cur.execute("SELECT DATE_SUB(stat_date, INTERVAL WEEKDAY(stat_date) DAY) w, SUM(qty_sold) "
                    "FROM order_sales_daily WHERE stat_date BETWEEN '2026-01-05' AND '2026-01-07' "
                    "GROUP BY DATE_SUB(stat_date, INTERVAL WEEKDAY(stat_date) DAY)")
        weeks = cur.fetchall()
        ok &= (len(weeks) == 1 and str(weeks[0][0]) == '2026-01-05' and weeks[0][1] == 24)
        # ③ SKU 明细 join 翻译 + 已删 SPU 名字仍可读 + 销量降序 LIMIT
        cur.execute("SELECT d.sku_id, ps.sku_code, p.name, SUM(d.qty_sold) FROM order_sales_daily d "
                    "LEFT JOIN product_sku ps ON d.sku_id = ps.id LEFT JOIN product p ON ps.product_id = p.id "
                    "WHERE d.stat_date BETWEEN '2026-01-05' AND '2026-01-07' "
                    "GROUP BY d.sku_id, ps.sku_code, p.name ORDER BY SUM(d.qty_sold) DESC, d.sku_id LIMIT 2")
        top2 = cur.fetchall()
        # 排序:SKU_A 合计 10 第一、SKU_C 合计 9 第二(已删 SPU 名字仍可读)
        ok &= (top2[0][0] == SKU_A and top2[0][3] == 10)
        ok &= (top2[1][0] == SKU_C and top2[1][2] == '已删哑元SPU' and top2[1][3] == 9)
        # ④ 库存快照指定日全行(join 名称翻译)
        cur.execute("SELECT s.sku_id, ps.sku_code, w.wh_name, s.qty_on_hand, s.qty_available "
                    "FROM inventory_snapshot_daily s LEFT JOIN product_sku ps ON s.sku_id = ps.id "
                    "LEFT JOIN warehouse w ON s.warehouse_id = w.id WHERE s.stat_date = '2026-01-04'")
        snaps = {r[0]: r for r in cur.fetchall()}
        ok &= (snaps[SKU_A][2] == '报表哑元仓' and snaps[SKU_A][3] == 10 and snaps[SKU_A][4] == 8)
        ok &= (snaps[SKU_B][4] == 20)
        # ⑤ MAX 快照日(真库若已有当日快照取 MAX 兼容;哑元 2026-01-04 必须被覆盖或并列)
        cur.execute("SELECT MAX(stat_date) FROM inventory_snapshot_daily")
        ok &= (str(cur.fetchone()[0]) >= '2026-01-04')
        print("ALL GREEN" if ok else "CHECK FAILED")
    finally:
        try:
            cleanup(cur)
        except Exception:
            pass
        conn.close()


if __name__ == "__main__":
    main()
