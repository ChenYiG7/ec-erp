#!/usr/bin/env python3
"""#20/#21/#22 报表域+利润看板+商品分析 存量库菜单对齐(2026-09-08/09-09):
   财务中心加「利润看板」(34,/finance/profit-dashboard);新顶级「报表中心」(35) +「销售与库存报表」(36)
   +「商品分析」(37,/report/goods-analysis,#22 四期 BI 首个功能);
   顶级 sort 顺延:通知中心(22) 8,系统管理(1) 9(与 01_schema_init.sql 种子逐字同源)。
   INSERT IGNORE 幂等,UPDATE 天然幂等;admin 角色全绑;连接信息读 local.properties。跑完打印自检。"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from menu_tool import connect  # noqa: E402

MENUS = [
    (34, 31, '利润看板', 2, 'finance:profit:list', '/finance/profit-dashboard', 'finance/profit-dashboard/index', 'DataLine', 3),
    (35, 0, '报表中心', 1, None, '/report', None, 'DataAnalysis', 7),
    (36, 35, '销售与库存报表', 2, 'report:center:list', '/report/center', 'report/center/index', 'TrendCharts', 1),
    (37, 35, '商品分析', 2, 'report:goods:list', '/report/goods-analysis', 'report/goods-analysis/index', 'Histogram', 2),
]

UPDATES = [
    "UPDATE sys_menu SET sort = 8 WHERE id = 22 AND sort < 8",
    "UPDATE sys_menu SET sort = 9 WHERE id = 1 AND sort < 9",
]

ROLE_MENUS = [(1, 34), (1, 35), (1, 36), (1, 37)]


def main():
    conn = connect()
    try:
        cur = conn.cursor()
        for mid, parent, name, mtype, perm, path, comp, icon, sort in MENUS:
            cur.execute(
                "INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, perm_key, path, component, icon, sort) "
                "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)",
                (mid, parent, name, mtype, perm, path, comp, icon, sort),
            )
        for sql in UPDATES:
            cur.execute(sql)
        for role_id, menu_id in ROLE_MENUS:
            cur.execute("INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (%s,%s)", (role_id, menu_id))
        conn.commit()
        # 自检:4 菜单行 + 4 绑定 + 顶级顺延生效
        cur.execute("SELECT COUNT(*) FROM sys_menu WHERE id IN (34,35,36,37)")
        menus = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM sys_role_menu WHERE menu_id IN (34,35,36,37)")
        grants = cur.fetchone()[0]
        cur.execute("SELECT id, sort FROM sys_menu WHERE id IN (22,1) ORDER BY id")
        sorts = dict(cur.fetchall())
        print(f"menus={menus}/4 grants={grants}/4 sorts={sorts}(期望 22:8 1:9)")
        print("ALL GREEN" if menus == 4 and grants == 4 and sorts.get(22) == 8 and sorts.get(1) == 9 else "CHECK FAILED")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
