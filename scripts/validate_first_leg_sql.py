# -*- coding: utf-8 -*-
"""#33 头程运费分摊 真库验证(TODO"SQL 兼容性红线":mapper XML 自定义 SQL 必须真库验证)。

一次跑完三件事:
  1. 建表/加列/菜单种子幂等重放(first_leg_shipment/first_leg_box/first_leg_box_item/
     first_leg_alloc CREATE IF NOT EXISTS 从正本 SQL 逐字提取;product_sku 长宽高 information_schema 判存;
     sys_menu 43/4301~4308 + sys_role_menu INSERT IGNORE)——已建库对齐等价于存量迁移;
  2. 分页联表(FirstLegQueryMapper.xml pageRows):双仓名 join 投影 + 构造映射参列数对齐
     (record 明细 List NULL 占位,缺列=有行即 500,#26 七轮);
     SKU 维度分摊聚合(listSkuAllocSummary):只计 ALLOCATED/CLOSED,Σ 头程运费 CNY;
  3. 唯一键(uk_shipment_box/uk_shipment_sku)+ 状态机 cas 条件更新守卫(DRAFT→BOXED/已分摊不可再分/已分摊不可取消)。

聚合语句从 erp-finance/src/main/resources/mapper/FirstLegQueryMapper.xml 原文提取
(<if> 动态段剥离、<where> 补 1=1,测的就是要发布的);哑元 id 统一 990033/99003x 段,
验证完逆序删除业务哑元行,不碰业务数据;建表/菜单/加列保留(对齐用)。
用法:python scripts/validate_first_leg_sql.py
"""
import io
import re
import sys
from pathlib import Path

import pymysql

sys.path.insert(0, str(Path(__file__).resolve().parent))
from menu_tool import connect  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
XML = ROOT / 'erp-finance' / 'src' / 'main' / 'resources' / 'mapper' / 'FirstLegQueryMapper.xml'
DDL_PATH = ROOT / 'docs' / 'sql' / '01_schema_init.sql'
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
PASSED = []

# 哑元段
WH_SELF = 990033
WH_OVERSEAS = 990034
S1 = 990033          # ALLOCATED:分摊 60/40 = 100
S2 = 990034          # CLOSED:分摊 25/25 = 50
S3 = 990035          # DRAFT:无分摊(用于 casBox 守卫)
SKU1, SKU2, SKU3 = 990031, 990032, 990033
BOX1, BOX2, BOX3 = 99003301, 99003401, 99003501
ITEM1, ITEM2, ITEM3, ITEM4, ITEM5 = 99003301, 99003302, 99003401, 99003402, 99003501
AL1, AL2, AL3, AL4 = 990031, 990032, 990033, 990034


def ok(label):
    PASSED.append(label)
    print(f'  ✅ {label}')


def extract_statement(statement_id):
    text = XML.read_text(encoding='utf-8')
    m = re.search(r'<select\s+id="%s"[^>]*>(.*?)</select>' % statement_id, text, re.S)
    if not m:
        raise SystemExit(f'XML 中找不到语句 {statement_id}')
    body = re.sub(r'<!--.*?-->', ' ', m.group(1), flags=re.S)
    # 动态段剥离:验证无过滤口径;统一用 foreach 翻译(本文件无 foreach,留同族处理)
    body = re.sub(r'(?s)<if[^>]*>.*?</if>', ' ', body)
    body = body.replace('<where>', ' WHERE 1=1 ').replace('</where>', ' ')
    body = re.sub(r'<foreach[^>]*>', '(', body)
    body = body.replace('</foreach>', ')')
    body = body.replace('&gt;', '>').replace('&lt;', '<')
    return re.sub(r'\s+', ' ', body).strip()


def column_exists(cur, table, column):
    cur.execute("SELECT COUNT(*) FROM information_schema.columns "
                "WHERE table_schema='erp' AND table_name=%s AND column_name=%s", (table, column))
    return cur.fetchone()[0] > 0


def replay_ddl_and_menus(cur):
    """从正本 SQL 截取头程四表 DDL 原样执行(逐字同源),加列/菜单幂等重放。"""
    sql_text = DDL_PATH.read_text(encoding='utf-8')
    for table in ('first_leg_shipment', 'first_leg_box', 'first_leg_box_item', 'first_leg_alloc'):
        m = re.search(r"CREATE TABLE IF NOT EXISTS %s \(.*?\n\) COMMENT '[^']*'" % table, sql_text, re.S)
        if not m:
            raise SystemExit(f'正本 SQL 中找不到建表段 {table}')
        cur.execute(m.group(0))
    for col in ('length_mm', 'width_mm', 'height_mm'):
        if not column_exists(cur, 'product_sku', col):
            mm = {'length_mm': '外长', 'width_mm': '外宽', 'height_mm': '外高'}[col]
            cur.execute(f"ALTER TABLE product_sku ADD COLUMN {col} INT NULL "
                        f"COMMENT '{mm}(毫米),头程/FBA装箱属性(#33)'")
    cur.execute("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, perm_key, path, component, icon, sort) "
                "VALUES (43, 31, '头程发货单', 2, 'finance:first-leg:list', '/finance/first-leg-shipments', "
                "'finance/first-leg/index', 'Promotion', 7)")
    cur.execute("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, perm_key) VALUES "
                "(4301, 43, '新增', 3, 'finance:first-leg:add'),"
                "(4302, 43, '编辑', 3, 'finance:first-leg:edit'),"
                "(4303, 43, '删除', 3, 'finance:first-leg:remove'),"
                "(4304, 43, '装箱完成', 3, 'finance:first-leg:box'),"
                "(4305, 43, '确认发货', 3, 'finance:first-leg:ship'),"
                "(4306, 43, '运费分摊', 3, 'finance:first-leg:allocate'),"
                "(4307, 43, '关闭', 3, 'finance:first-leg:close'),"
                "(4308, 43, '取消', 3, 'finance:first-leg:cancel')")
    cur.execute("INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES "
                "(1,43),(1,4301),(1,4302),(1,4303),(1,4304),(1,4305),(1,4306),(1,4307),(1,4308)")


CLEANUP_SQLS = [
    "DELETE FROM first_leg_alloc WHERE id BETWEEN 990031 AND 990099 OR shipment_id IN (990033,990034,990035)",
    f"DELETE FROM first_leg_box_item WHERE id IN ({ITEM1},{ITEM2},{ITEM3},{ITEM4},{ITEM5})",
    f"DELETE FROM first_leg_box WHERE id IN ({BOX1},{BOX2},{BOX3},99003399)",
    "DELETE FROM first_leg_shipment WHERE id IN (990033,990034,990035)",
    f"DELETE FROM product_sku WHERE id IN ({SKU1},{SKU2},{SKU3})",
    f"DELETE FROM warehouse WHERE id IN ({WH_SELF},{WH_OVERSEAS})",
]


def insert_fixtures(cur):
    cur.execute(
        f"INSERT INTO warehouse (id, wh_name, wh_type, country, status) VALUES "
        f"({WH_SELF},'__val__FL国内仓','SELF','CN',1),({WH_OVERSEAS},'__val__FL海外仓','OVERSEAS','US',1)")
    cur.execute(
        f"INSERT INTO product_sku (id, product_id, sku_code, status) VALUES "
        f"({SKU1},990033,'__val__FLSKU1',1),({SKU2},990033,'__val__FLSKU2',1),({SKU3},990033,'__val__FLSKU3',1)")

    def ship(sid, no, status, freight_cny):
        cur.execute(
            f"INSERT INTO first_leg_shipment "
            f"(id, shipment_no, from_warehouse_id, to_warehouse_id, freight_amount, currency, exchange_rate, "
            f"freight_cny, shipped_at, allocate_strategy, status, created_at, updated_at) "
            f"VALUES (%s,%s,{WH_SELF},{WH_OVERSEAS},%s,'CNY',1,%s,'2026-09-10 10:00:00','QTY',%s,"
            f"'2026-09-10 10:00:00','2026-09-10 10:00:00')",
            (sid, no, freight_cny, freight_cny, status))

    ship(S1, '__val__FL0001', 'ALLOCATED', '100.0000')
    ship(S2, '__val__FL0002', 'CLOSED', '50.0000')
    ship(S3, '__val__FL0003', 'DRAFT', None)
    cur.execute(
        f"INSERT INTO first_leg_box (id, shipment_id, box_no) VALUES "
        f"({BOX1},{S1},'B1'),({BOX2},{S2},'B1'),({BOX3},{S3},'B1')")
    cur.execute(
        f"INSERT INTO first_leg_box_item (id, box_id, sku_id, quantity) VALUES "
        f"({ITEM1},{BOX1},{SKU1},6),({ITEM2},{BOX1},{SKU2},4),"
        f"({ITEM3},{BOX2},{SKU1},2),({ITEM4},{BOX2},{SKU3},2),"
        f"({ITEM5},{BOX3},{SKU1},1)")
    cur.execute(
        f"INSERT INTO first_leg_alloc (id, shipment_id, sku_id, alloc_amount, alloc_base, strategy) VALUES "
        f"({AL1},{S1},{SKU1},60.0000,6.0000,'QTY'),({AL2},{S1},{SKU2},40.0000,4.0000,'QTY'),"
        f"({AL3},{S2},{SKU1},25.0000,2.0000,'QTY'),({AL4},{S2},{SKU3},25.0000,2.0000,'QTY')")


def main():
    conn = connect()
    try:
        cur = conn.cursor()
        replay_ddl_and_menus(cur)
        conn.commit()
        ok('建表/加列/菜单种子幂等重放(first_leg 四表/product_sku 长宽高/43/4301~4308)')
        for sql in CLEANUP_SQLS:
            cur.execute(sql)
        conn.commit()

        insert_fixtures(cur)
        conn.commit()

        # 1) pageRows:双仓名 join 投影(无过滤口径,动态段已剥离)
        cur.execute(extract_statement('pageRows'))
        rows = {r[0]: r for r in cur.fetchall()}
        assert S1 in rows, f'头程单哑元行缺失: {list(rows)}'
        s1 = rows[S1]
        # 列序见 XML:id, shipmentNo, fromWarehouseId, toWarehouseId, fromWarehouseName, toWarehouseName...
        assert s1[4] == '__val__FL国内仓', f'发货仓名 join 异常: {s1}'
        assert s1[5] == '__val__FL海外仓', f'目的仓名 join 异常: {s1}'
        ok('pageRows(双仓名 LEFT JOIN 投影,单据头全列)')

        # 1b) 构造映射参列数对齐(#26 七轮 2026-09-13):record 无 setter,MyBatis 按列序构造自动映射,
        #     缺列=有行即 500,且空表/直跑 SQL 均不暴露(FbaQueryMapper.pageRows 同日实锚)——
        #     静态断言 pageRows 列数与 FirstLegShipmentResponse 构造参数数相等(明细 List NULL 占位)
        resp_java = (ROOT / 'erp-finance/src/main/java/com/own/erp/finance/response/'
                              'FirstLegShipmentResponse.java').read_text(encoding='utf-8')
        rm = re.search(r'public record FirstLegShipmentResponse\((.*?)\)\s*\{', resp_java, re.S)
        assert rm, 'FirstLegShipmentResponse record 头解析失败'
        args = re.sub(r'/\*\*.*?\*/', ' ', rm.group(1), flags=re.S)
        args = re.sub(r'List<[^<>]*>', 'LIST', args)
        arg_count = len([a for a in args.split(',') if a.strip()])
        select_part = extract_statement('pageRows').split(' FROM ')[0]
        col_count = len([c for c in select_part.split(',') if c.strip()])
        assert arg_count == col_count, f'pageRows 列数({col_count}) != record 构造参数数({arg_count}),有行即 500'
        ok(f'pageRows 构造映射参列数对齐(record {arg_count} 参 = SQL {col_count} 列)')

        # 2) listSkuAllocSummary:ALLOCATED 100(60/40)+ CLOSED 50(25/25);DRAFT 无分摊不计
        cur.execute(extract_statement('listSkuAllocSummary'))
        agg = {r[0]: r for r in cur.fetchall()}
        assert SKU1 in agg and SKU2 in agg and SKU3 in agg, f'分摊聚合行缺失: {agg}'
        assert float(agg[SKU1][3]) == 85.0 and agg[SKU1][2] == 2, f'SKU1 应=85/2单: {agg[SKU1]}'
        assert float(agg[SKU2][3]) == 40.0 and agg[SKU2][2] == 1, f'SKU2 应=40/1单: {agg[SKU2]}'
        assert float(agg[SKU3][3]) == 25.0 and agg[SKU3][2] == 1, f'SKU3 应=25/1单: {agg[SKU3]}'
        ok('listSkuAllocSummary(只计 ALLOCATED/CLOSED,Σ 分摊 CNY/单数)')

        # 2b) sumAllocBySkuIds(#33 利润第三层 enrichment 批量版):口径同上;动态 <if> 窗口段按本脚本
        #     口径剥离(验证无过滤形态),窗外过滤由同源 listSkuAllocSummary 的窗口形态等价覆盖
        id_list = f"{SKU1},{SKU2},{SKU3}"
        sql = (extract_statement('sumAllocBySkuIds')
               .replace("#{skuId}", id_list)
               .replace("#{shippedFrom}", "'2026-09-01 00:00:00'")
               .replace("#{shippedTo}", "'2026-09-30 23:59:59'"))
        cur.execute(sql)
        sums = {r[0]: float(r[1]) for r in cur.fetchall()}
        assert sums == {SKU1: 85.0, SKU2: 40.0, SKU3: 25.0}, f'批量合计不符: {sums}'
        ok('sumAllocBySkuIds(#33 第三层批量合计:口径同源/集合 IN 传参/7 列外两列出参)')

        # 3) uk_shipment_box:同单同箱号第二条必须被唯一键拦
        blocked = False
        try:
            cur.execute(f"INSERT INTO first_leg_box (id, shipment_id, box_no) VALUES (99003399,{S1},'B1')")
            conn.commit()
        except pymysql.err.IntegrityError:
            blocked = True
            conn.rollback()
        assert blocked, 'uk_shipment_box 未拦截同单重复箱号'
        ok('uk_shipment_box(shipment_id,box_no) 唯一键')

        # 4) uk_shipment_sku:同单同 SKU 第二条分摊必须被拦(分摊不可重算覆盖的库级兜底)
        blocked = False
        try:
            cur.execute(
                f"INSERT INTO first_leg_alloc (id, shipment_id, sku_id, alloc_amount, alloc_base, strategy) "
                f"VALUES (990099,{S1},{SKU1},1.0000,1.0000,'QTY')")
            conn.commit()
        except pymysql.err.IntegrityError:
            blocked = True
            conn.rollback()
        assert blocked, 'uk_shipment_sku 未拦截同单重复分摊'
        ok('uk_shipment_sku(shipment_id,sku_id) 唯一键(不可重算覆盖)')

        # 5) casBox 守卫:DRAFT→BOXED 命中 1 行,重复执行脱靶 0 行(与 Mapper @Update 逐字同形)
        cur.execute(f"UPDATE first_leg_shipment SET status='BOXED' WHERE id={S3} AND status='DRAFT'")
        assert cur.rowcount == 1, 'casBox 首次应命中 1 行'
        cur.execute(f"UPDATE first_leg_shipment SET status='BOXED' WHERE id={S3} AND status='DRAFT'")
        assert cur.rowcount == 0, 'casBox 重复(已 BOXED)应脱靶 0 行'
        ok('casBox 条件更新守卫(DRAFT→BOXED 命中/重复脱靶)')

        # 6) casAllocate 守卫:ALLOCATED 单再分摊脱靶(拦重算);casCancel 对 ALLOCATED 脱靶(仅 DRAFT/BOXED 可取消)
        cur.execute(f"UPDATE first_leg_shipment SET status='ALLOCATED' WHERE id={S1} AND status='SHIPPED'")
        assert cur.rowcount == 0, 'ALLOCATED 单重复分摊必须脱靶'
        cur.execute(f"UPDATE first_leg_shipment SET status='CANCELED' WHERE id={S1} AND status IN ('DRAFT','BOXED')")
        assert cur.rowcount == 0, 'ALLOCATED 单不应被取消'
        ok('状态机守卫(已分摊不可重算/已发货起不可取消)')

    finally:
        cur = conn.cursor()
        for sql in reversed(CLEANUP_SQLS):
            cur.execute(sql)
        conn.commit()
        conn.close()

    print(f'\nALL GREEN: {len(PASSED)} 项验证通过(业务哑元已清理,建表/加列/菜单保留)')


if __name__ == '__main__':
    main()
