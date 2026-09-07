# -*- coding: utf-8 -*-
"""sys_menu 菜单工具:只读打印菜单树 / 幂等执行 2026-09-07 菜单整理。

用法(在项目根目录执行,连接信息自动读 local.properties,勿硬编码密码):
    python scripts/menu_tool.py dump     # 打印当前菜单树(目录+菜单+按钮统计)
    python scripts/menu_tool.py reorg    # 幂等执行 2026-09-07 菜单整理(见 docs/sql/01_schema_init.sql 注释段)

背景(2026-09-07 菜单整理,两次合并最终态):
  1. AI助手(25) 顶级置顶 sort=1;系统管理(1) 排最后 sort=7;
  2. 系统管理瘦身:店铺管理(5) -> 商品中心,拉单日志(21) -> 订单中心,通知中心(22) -> 顶级 sort=6;
  3. 受影响子菜单 sort 绝对值重排。path/perm_key/component 均不变,仅动 parent_id/sort。
  最终顶级排序:AI助手/商品中心/订单中心/采购管理/库存管理/通知中心/系统管理。
"""
import sys
import io
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parent.parent


def load_props():
    """解析项目根 local.properties(KEY=VALUE,# 注释行跳过)。"""
    props = {}
    for line in (ROOT / 'local.properties').read_text(encoding='utf-8', errors='ignore').splitlines():
        line = line.strip()
        if not line or line.startswith('#') or '=' not in line:
            continue
        k, v = line.split('=', 1)
        props[k.strip()] = v.strip()
    return props


def connect():
    p = load_props()
    return pymysql.connect(host=p['MYSQL_HOST'], port=int(p.get('MYSQL_PORT', 3306)),
        user=p['MYSQL_USERNAME'], password=p['MYSQL_PASSWORD'], database='erp', charset='utf8mb4')


def dump_tree(cur):
    cur.execute("""
        SELECT id, parent_id, menu_name, menu_type, path, component, sort, visible, status
        FROM sys_menu WHERE menu_type IN (1, 2) ORDER BY parent_id, sort, id
    """)
    rows = cur.fetchall()
    children = {}
    for r in rows:
        children.setdefault(r['parent_id'], []).append(r)

    def walk(pid, depth):
        for r in children.get(pid, []):
            tag = '[DIR] ' if r['menu_type'] == 1 else '[MENU]'
            marks = ''
            if r['visible'] != 1:
                marks += ' (hidden)'
            if r['status'] != 1:
                marks += ' (DISABLED)'
            print('  ' * depth + f"{tag} id={r['id']:<4} sort={r['sort']:<2} {r['menu_name']} path={r['path']}{marks}")
            walk(r['id'], depth + 1)

    walk(0, 0)
    cur.execute("SELECT parent_id, COUNT(*) cnt FROM sys_menu WHERE menu_type = 3 GROUP BY parent_id")
    btn = {r['parent_id']: r['cnt'] for r in cur.fetchall()}
    print('\nbutton counts:')
    for r in rows:
        if r['id'] in btn:
            print(f"  id={r['id']:<4} {r['menu_name']}: {btn[r['id']]}")


REORG_UPDATES = [
    # 顶级: AI助手第1, 通知中心第6, 系统管理第7(最后)
    "UPDATE sys_menu SET sort = 1 WHERE id = 25",
    "UPDATE sys_menu SET parent_id = 0, sort = 6 WHERE id = 22",
    "UPDATE sys_menu SET sort = 7 WHERE id = 1",
    # 商品中心: 店铺管理第1(店铺是商品/平台商品/SKU 的上游), 其余绝对值顺移
    "UPDATE sys_menu SET parent_id = 6, sort = 1 WHERE id = 5",
    "UPDATE sys_menu SET sort = 2 WHERE id = 7",
    "UPDATE sys_menu SET sort = 3 WHERE id = 101",
    "UPDATE sys_menu SET sort = 4 WHERE id = 16",
    "UPDATE sys_menu SET sort = 5 WHERE id = 23",
    "UPDATE sys_menu SET sort = 6 WHERE id = 24",
    # 订单中心: 拉单日志排 订单管理/发货单/售后单 之后
    "UPDATE sys_menu SET parent_id = 8, sort = 4 WHERE id = 21",
    # 系统管理剩余: 用户 角色 菜单 字典 系统设置(1-5 无空缺)
    "UPDATE sys_menu SET sort = 4 WHERE id = 100",
    "UPDATE sys_menu SET sort = 5 WHERE id = 29",
]


def reorg(cur):
    # 已整理标志:通知中心已是顶级(第二次调整完成的标志)
    cur.execute("SELECT id FROM sys_menu WHERE id = 22 AND parent_id = 0")
    if cur.fetchone():
        print('already reorganized, nothing to do')
        return
    try:
        for sql in REORG_UPDATES:
            cur.execute(sql)
            print(f"[ok] rows={cur.rowcount}  {sql}")
        conn.commit()
        print('COMMITTED')
    except Exception:
        conn.rollback()
        print('ROLLED BACK')
        raise
    dump_tree(cur)


if __name__ == '__main__':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    cmd = sys.argv[1] if len(sys.argv) > 1 else 'dump'
    conn = connect()
    cur = conn.cursor(pymysql.cursors.DictCursor)
    if cmd == 'dump':
        dump_tree(cur)
    elif cmd == 'reorg':
        reorg(cur)
    else:
        print(f'unknown command: {cmd} (use dump | reorg)')
    conn.close()
