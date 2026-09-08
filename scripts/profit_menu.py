#!/usr/bin/env python3
"""#19③ 利润核算 V1 存量库菜单对齐(2026-09-08):
   财务中心(31 目录)/实时销售利润(32)/汇率快照(33)/录入快照按钮(3301) + admin 角色绑定;
   顶级 sort 重排:财务中心 6,通知中心(22) 6→7,系统管理(1) 7→8。
   INSERT IGNORE 幂等,UPDATE 语句天然幂等;连接信息读 local.properties。跑完打印自检。"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from menu_tool import connect  # noqa: E402

MENUS = [
    (31, 0, '财务中心', 1, None, '/finance', None, 'wallet', 6),
    (32, 31, '实时销售利润', 2, 'finance:profit:list', '/finance/profit', 'finance/profit/index', 'Coin', 1),
    (33, 31, '汇率快照', 2, 'finance:rate:list', '/finance/exchange-rates', 'finance/exchange-rate/index', 'Money', 2),
    (3301, 33, '录入快照', 3, 'finance:rate:save', None, None, None, None),
]

UPDATES = [
    "UPDATE sys_menu SET sort = 7 WHERE id = 22 AND sort < 7",
    "UPDATE sys_menu SET sort = 8 WHERE id = 1 AND sort < 8",
]

ROLE_MENUS = [(1, 31), (1, 32), (1, 33), (1, 3301)]


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
        # 自检:四行菜单存在 + admin 绑定四行
        cur.execute("SELECT COUNT(*) FROM sys_menu WHERE id IN (31,32,33,3301)")
        menus = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM sys_role_menu WHERE role_id=1 AND menu_id IN (31,32,33,3301)")
        binds = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM sys_menu WHERE parent_id=0 AND sort=6 AND id=31")
        sort_ok = cur.fetchone()[0]
        print(f"menus={menus}/4 role_binds={binds}/4 top_sort_ok={sort_ok}/1")
        print("ALL GREEN" if menus == 4 and binds == 4 and sort_ok == 1 else "CHECK FAILED")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
