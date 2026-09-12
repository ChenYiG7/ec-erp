# -*- coding: utf-8 -*-
"""FBA 发货单(fba-shipment V1 数据面)SQL 真库验证 —— 2026-09-12 docs/plans/fba-shipment.md §五验收。

为什么必须真库:mapper 的 @Update cas 守卫与分页 XML 的店名/仓名 LEFT JOIN,单测 mock 永远测不出
形态错误(TODO "SQL 兼容性红线");本脚本把**要发布的 SQL 原文**从 Java 注解/XML 抠出来(不是手抄,
防漂移),在开发库(MySQL 9.7.2 LTS)上逐条真跑:

  0. 前置:fba_shipment/fba_shipment_item/fba_box/fba_box_item/fba_shipment_diff CREATE IF NOT EXISTS
     + 重放 sys_menu/sys_role_menu 的 INSERT IGNORE 段(幂等;存量库对齐菜单 46/4601~4608)
  1. cas 五守卫真库:DRAFT→BOXED→SHIPPED→RECEIVING→CLOSED 逐态命中/重复拦截;
     casShip 前置态不符(非 BOXED)拒;caserive 重复登记在 RECEIVING 态拒(覆盖不经 cas);
     casCancel DRAFT 可取消、SHIPPED(动账后)禁取消
  2. 行锁读 selectByIdForUpdate(@Select FOR UPDATE)语法真库可用
  3. 分页 XML pageRows:店名/仓名 LEFT JOIN 取名 + shipmentNo LIKE/status/shopId/marketplace/warehouseId 过滤形态

哑元行用 9 亿段 id(shop 900000001/warehouse 900000002/单据 900000009~11),验证完删除,不碰业务数据。
用法:python scripts/validate_fba_sql.py
"""
import io
import re
import sys
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parent.parent
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
PASSED = []

DUMMY_SHOP = 900000001
DUMMY_WH = 900000002
DUMMY_A, DUMMY_B, DUMMY_C, DUMMY_D = 900000009, 900000010, 900000011, 900000012
XML = ROOT / 'erp-fulfill' / 'src' / 'main' / 'resources' / 'mapper' / 'FbaQueryMapper.xml'


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


def extract_annotation_sql(java_text, method, annotation='@Update'):
    """从 @Update/@Select("..." + "...") 注解抠出 SQL 原文(去引号与 + 连接符),按方法名定位。"""
    mi = java_text.find(method + '(')
    if mi < 0:
        raise SystemExit(f'Java 源中找不到方法 {method}')
    ai = java_text.rfind(annotation + '(', 0, mi)
    if ai < 0:
        raise SystemExit(f'方法 {method} 前找不到 {annotation} 注解')
    i = ai + len(annotation + '(')
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
    return re.sub(r'\s+', ' ', ''.join(out)).strip()


def extract_page_rows(drop_ifs=False):
    """FbaQueryMapper.pageRows 原文提取:<where> 全条件在 <if> 内——
    drop_ifs=True 模拟 MyBatis 全不成立(无 WHERE 直查);False=全条件成立(手拼 WHERE 并剥首个 AND)。"""
    text = XML.read_text(encoding='utf-8')
    m = re.search(r'<select\s+id="pageRows"[^>]*>(.*?)</select>', text, re.S)
    if not m:
        raise SystemExit('FbaQueryMapper.xml 中找不到 pageRows')
    body = re.sub(r'<!--.*?-->', ' ', m.group(1), flags=re.S)
    if drop_ifs:
        body = re.sub(r'<if[^>]*>.*?</if>', ' ', body, flags=re.S)
        body = body.replace('<where>', ' ').replace('</where>', ' ')
    else:
        body = body.replace('<where>', ' WHERE ').replace('</where>', ' ')
        body = re.sub(r'</?if[^>]*>', ' ', body)
        body = re.sub(r'(WHERE\s+)AND\s+', r'\1', body, count=1)
    body = body.replace('&gt;', '>').replace('&lt;', '<')
    body = re.sub(r'#\{([\w.]+)\}', r'%(\1)s', body)  # #{query.status} → %(query.status)s(键含点,%-格式化按字面键查找)
    # LIKE CONCAT('%'...) 里的字面 % 与 %-格式化冲突:非 %(key)s 形态的 % 全部转义为 %%
    body = re.sub(r'%(?!\([\w.]+\)s)', '%%', body)
    return re.sub(r'\s+', ' ', body).strip()


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
    ids = (DUMMY_A, DUMMY_B, DUMMY_C, DUMMY_D)
    cur.execute('DELETE FROM fba_shipment_diff WHERE shipment_id IN (%s,%s,%s,%s)', ids)
    cur.execute('DELETE FROM fba_box_item WHERE box_id IN '
                '(SELECT id FROM fba_box WHERE shipment_id IN (%s,%s,%s,%s))', ids)
    cur.execute('DELETE FROM fba_box WHERE shipment_id IN (%s,%s,%s,%s)', ids)
    cur.execute('DELETE FROM fba_shipment_item WHERE shipment_id IN (%s,%s,%s,%s)', ids)
    cur.execute('DELETE FROM fba_shipment WHERE id IN (%s,%s,%s,%s)', ids)
    cur.execute('DELETE FROM warehouse WHERE id=%s', (DUMMY_WH,))
    cur.execute('DELETE FROM shop WHERE id=%s', (DUMMY_SHOP,))


def main():
    props = load_props()
    conn = pymysql.connect(host=props['MYSQL_HOST'], port=int(props['MYSQL_PORT']),
                           user=props['MYSQL_USERNAME'], password=props['MYSQL_PASSWORD'],
                           database='erp', charset='utf8mb4', autocommit=True)
    cur = conn.cursor()
    cur.execute('SELECT VERSION()')
    ver = cur.fetchone()[0]
    print(f'MySQL {ver} —— FBA 发货单 cas 守卫/分页 join SQL 真库验证(SQL 原文取自 Mapper 注解与 XML)\n')

    schema = (ROOT / 'docs/sql/01_schema_init.sql').read_text(encoding='utf-8')
    # 先剥离整行 -- 注释:菜单段注释里含 `;`,不剥离会截断语句
    schema = '\n'.join(l for l in schema.splitlines() if not l.lstrip().startswith('--'))
    fba_java = (ROOT / 'erp-fulfill/src/main/java/com/own/erp/fulfill/mapper/FbaShipmentMapper.java').read_text(encoding='utf-8')

    # ---------- 0. 前置:五表 + 菜单种子(全幂等) ----------
    for t in ('fba_shipment', 'fba_shipment_item', 'fba_box', 'fba_box_item', 'fba_shipment_diff'):
        cur.execute(extract_create(schema, t))
    for stmt in extract_inserts(schema, 'sys_menu') + extract_inserts(schema, 'sys_role_menu'):
        cur.execute(stmt)
    cur.execute("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='erp' "
                "AND table_name IN ('fba_shipment','fba_shipment_item','fba_box','fba_box_item','fba_shipment_diff')")
    assert cur.fetchone()[0] == 5, '五张 FBA 表未全部就绪'
    cur.execute("SELECT COUNT(*) FROM sys_menu WHERE id IN (46,4601,4602,4603,4604,4605,4606,4607,4608)")
    assert cur.fetchone()[0] == 9, '菜单 46/4601~4608 未就位'
    ok(cur, '0. 五表 CREATE + 菜单 46/4601~4608 幂等重放')

    clear(cur)
    # 哑元四单:A 走全链 CLOSED;B 走取消;C 停在 SHIPPED 验禁取消;D 保持 DRAFT 供分页过滤形态
    cur.execute('INSERT INTO shop (id, shop_name, platform, status) VALUES (%s,%s,%s,1)',
                (DUMMY_SHOP, '__val__FBA店', 'AMAZON'))
    cur.execute('INSERT INTO warehouse (id, wh_name, wh_type) VALUES (%s,%s,%s)', (DUMMY_WH, '__val__FBA仓', 'SELF'))
    for sid, status in ((DUMMY_A, 'DRAFT'), (DUMMY_B, 'DRAFT'), (DUMMY_C, 'DRAFT'), (DUMMY_D, 'DRAFT')):
        cur.execute('INSERT INTO fba_shipment (id, shipment_no, shop_id, marketplace, warehouse_id, status, created_by) '
                    'VALUES (%s,%s,%s,%s,%s,%s,1)', (sid, f'__val__FBA{sid}', DUMMY_SHOP, 'US', DUMMY_WH, status))

    # ---------- 1. cas 五守卫:逐态命中/重复拦截/前置态不符 ----------
    cas_box = extract_annotation_sql(fba_java, 'casBox')
    cas_ship = extract_annotation_sql(fba_java, 'casShip')
    cas_receive = extract_annotation_sql(fba_java, 'casReceive')
    cas_close = extract_annotation_sql(fba_java, 'casClose')
    cas_cancel = extract_annotation_sql(fba_java, 'casCancel')
    for sql, froms in ((cas_box, "'DRAFT'"), (cas_ship, "'BOXED'"), (cas_receive, "'SHIPPED'"),
                       (cas_close, "'RECEIVING'"), (cas_cancel, "IN ('DRAFT','BOXED')")):
        assert f"status = {froms}" in sql or f"status IN" in sql, f'cas 守卫形态异常: {sql}'

    # A 单全链:BOXED→SHIPPED→RECEIVING→CLOSED,重复 cas 全部 affected=0
    assert cur.execute(bind(cas_box, id=DUMMY_A)) == 1, 'A 装箱完成应命中'
    assert cur.execute(bind(cas_box, id=DUMMY_A)) == 0, 'A 重复装箱应被拦'
    assert cur.execute(bind(cas_ship, id=DUMMY_A)) == 1, 'A 确认发出应命中'
    assert cur.execute(bind(cas_receive, id=DUMMY_A)) == 1, 'A 首次收货登记应命中'
    assert cur.execute(bind(cas_receive, id=DUMMY_A)) == 0, 'A RECEIVING 态重复 cas 应被拦(重复登记不经 cas 放行覆盖)'
    assert cur.execute(bind(cas_close, id=DUMMY_A)) == 1, 'A 关闭应命中'
    # B 单取消:DRAFT 可取消;casShip 前置态不符(DRAFT≠BOXED)应拒
    assert cur.execute(bind(cas_ship, id=DUMMY_B)) == 0, 'B 草稿态确认发出应被拦(前置态不符)'
    assert cur.execute(bind(cas_cancel, id=DUMMY_B)) == 1, 'B 草稿态取消应命中'
    # C 单:SHIPPED(动账后)禁取消
    assert cur.execute(bind(cas_box, id=DUMMY_C)) == 1 and cur.execute(bind(cas_ship, id=DUMMY_C)) == 1
    assert cur.execute(bind(cas_cancel, id=DUMMY_C)) == 0, 'C 已发出(动账)取消应被拦'
    cur.execute('SELECT id, status FROM fba_shipment WHERE id IN (%s,%s,%s) ORDER BY id', (DUMMY_A, DUMMY_B, DUMMY_C))
    states = dict(cur.fetchall())
    assert states == {DUMMY_A: 'CLOSED', DUMMY_B: 'CANCELED', DUMMY_C: 'SHIPPED'}, f'终态不符: {states}'
    ok(cur, '1. cas 五守卫:逐态命中/重复与前置态不符 affected=0;SHIPPED 动账后禁取消')

    # ---------- 2. 行锁读 selectByIdForUpdate(@Select ... FOR UPDATE) ----------
    lock_sql = extract_annotation_sql(fba_java, 'selectByIdForUpdate', annotation='@Select')
    assert 'FOR UPDATE' in lock_sql, f'行锁读形态异常: {lock_sql}'
    cur.execute(bind(lock_sql, id=DUMMY_A))
    row = cur.fetchone()
    assert row is not None and row[0] == DUMMY_A and row[6] == 'CLOSED', f'行锁读结果异常: {row}'
    ok(cur, '2. selectByIdForUpdate FOR UPDATE 行锁读语法与取值')

    # ---------- 3. 分页 XML:店名/仓名 LEFT JOIN + 过滤形态(D 保持 DRAFT 未被 cas 触碰) ----------
    cur.execute(extract_page_rows(drop_ifs=True))
    no_filter = {r[0]: r for r in cur.fetchall()}
    assert all(sid in no_filter for sid in (DUMMY_A, DUMMY_B, DUMMY_C, DUMMY_D)), \
        f'无过滤形态缺哑元行: {sorted(no_filter)[:4]}'
    assert no_filter[DUMMY_A][3] == '__val__FBA店' and no_filter[DUMMY_A][6] == '__val__FBA仓', \
        f'LEFT JOIN 店名/仓名缺失: {no_filter[DUMMY_A]}'
    # 字符串参数自带引号(文本注入约定,同 validate_profit_sql)
    params = {'query.shipmentNo': "'__val__FBA'", 'query.status': "'DRAFT'", 'query.shopId': DUMMY_SHOP,
              'query.marketplace': "'US'", 'query.warehouseId': DUMMY_WH}
    cur.execute(extract_page_rows() % params)
    filtered = {r[0] for r in cur.fetchall()}
    assert DUMMY_D in filtered and not (filtered & {DUMMY_A, DUMMY_B, DUMMY_C}), \
        f'status=DRAFT 过滤应仅留 D 单: {sorted(filtered)}'
    ok(cur, '3. pageRows 店名/仓名 LEFT JOIN + shipmentNo LIKE/status/shopId/marketplace/warehouseId 过滤形态')

    clear(cur)
    cur.execute('SELECT COUNT(*) FROM fba_shipment WHERE id IN (%s,%s,%s,%s)', (DUMMY_A, DUMMY_B, DUMMY_C, DUMMY_D))
    assert cur.fetchone()[0] == 0, '哑元清理不彻底'
    ok(cur, 'cleanup.哑元行清零')

    conn.close()
    print(f'\n全部通过:{len(PASSED)} 项 —— FBA 发货单 SQL 真库验证收口(引擎 {ver})')


if __name__ == '__main__':
    main()
