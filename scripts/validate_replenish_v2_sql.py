# -*- coding: utf-8 -*-
"""#6 补货算法 V2 Mapper SQL 真库验证(TODO"SQL 兼容性红线":mapper XML 自定义 SQL 必须真库验证)。

对象 = erp-order/src/main/resources/mapper/OrderSalesDailyMapper.xml 新增读侧语句
(纯静态 SQL 无动态标签,从 XML 原文提取,测的就是要发布的;#{p} 文本注入规则同 validate_profit_sql.py):

  1. listQtySince   各 SKU 逐日销量序列(补货 V2 需求波动 σ 数据面):sku_id IN + stat_date range,
                    走 uk_sku_date(sku_id, stat_date) 前缀索引;与 sumQtySince 合计交叉核对

哑元行键 sku_id=990001(含一个零销日——表无行,验证调用方补零口径),验证完删除,不碰业务数据。
用法:python scripts/validate_replenish_v2_sql.py
"""
import io
import re
import sys
from datetime import date, timedelta
from pathlib import Path

import pymysql
import pymysql.cursors

ROOT = Path(__file__).resolve().parent.parent
XML = ROOT / 'erp-order' / 'src' / 'main' / 'resources' / 'mapper' / 'OrderSalesDailyMapper.xml'
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


def extract_statement(statement_id):
    text = XML.read_text(encoding='utf-8')
    m = re.search(r'<select\s+id="%s"[^>]*>(.*?)</select>' % statement_id, text, re.S)
    if not m:
        raise SystemExit(f'XML 中找不到语句 {statement_id}')
    body = m.group(1)
    body = re.sub(r'<!--.*?-->', ' ', body, flags=re.S)
    # foreach → 集合占位(open/close 括号在标签属性里,随标签消失;集合值以含括号 SQL 字面量注入,
    # validate_profit_sql.py 提取器三坑同款)
    body = re.sub(r'<foreach[^>]*\bcollection="(\w+)"[^>]*>.*?</foreach>', r'%(\1)s', body, flags=re.S)
    body = re.sub(r'</?(if|where|foreach|trim|choose|when|otherwise)[^>]*>', ' ', body)
    body = body.replace('&gt;', '>').replace('&lt;', '<')
    body = re.sub(r'#{(\w+)}', r'%(\1)s', body)  # #{p} → %(p)s 文本注入
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
    """只显式传 pymysql 认可的键(禁字典推导透传,validate_profit_sql.py 先例);DictCursor 按列名取数。"""
    p = load_props()
    return pymysql.connect(host=p['MYSQL_HOST'], port=int(p.get('MYSQL_PORT', 3306)),
        user=p['MYSQL_USERNAME'], password=p['MYSQL_PASSWORD'], database='erp',
        charset='utf8mb4', autocommit=False, cursorclass=pymysql.cursors.DictCursor)


DUMMY_SKU = 990001
DUMMY_DATES = [date(2026, 9, 1), date(2026, 9, 2), date(2026, 9, 3)]
DUMMY_QTYS = {DUMMY_DATES[0]: 3, DUMMY_DATES[2]: 6}  # 9-02 零销日不播种(表无行)


def sql_lit(d):
    return "'" + d.isoformat() + "'"


def main():
    list_sql = extract_statement('listQtySince')
    sum_sql = extract_statement('sumQtySince')
    print('== 验证 listQtySince(逐日销量序列)==')
    print(f'  SQL: {list_sql}')

    conn = connect()
    try:
        cur = conn.cursor()

        # 0) 入口预清扫(幂等重跑)
        cur.execute('DELETE FROM order_sales_daily WHERE sku_id=%s', (DUMMY_SKU,))
        conn.commit()

        # 1) 哑元播种:两天有行(3/6),一天零销
        for d, qty in DUMMY_QTYS.items():
            cur.execute('INSERT INTO order_sales_daily (stat_date, sku_id, qty_sold) VALUES (%s,%s,%s)',
                        (d, DUMMY_SKU, qty))
        conn.commit()

        # 2) 语句跑通:两 SKU 入参(含无数据的 990002);零销日不出现由调用方补零
        cur.execute(list_sql % {'startDate': sql_lit(DUMMY_DATES[0]), 'skuIds': '(990001,990002)'})
        rows = cur.fetchall()
        got = {(r['skuId'], r['statDate']): int(r['qtySold']) for r in rows}
        expect = {(DUMMY_SKU, DUMMY_DATES[0]), (DUMMY_SKU, DUMMY_DATES[2])}
        assert set(got.keys()) == expect, f'序列行集不符: {got}'
        assert got[(DUMMY_SKU, DUMMY_DATES[0])] == 3 and got[(DUMMY_SKU, DUMMY_DATES[2])] == 6
        ok(cur, 'listQtySince.序列行集与零销日排除')

        # 3) 同 SKU stat_date 升序
        cur.execute(list_sql, {'startDate': DUMMY_DATES[0], 'skuIds': [DUMMY_SKU]})
        dates = [r['statDate'] for r in cur.fetchall()]
        assert dates == sorted(dates), f'stat_date 未升序: {dates}'
        ok(cur, 'listQtySince.同SKU日序升序')

        # 4) 与 sumQtySince 交叉核对:哑元逐日合计相加 == 合计语句(3+6=9)
        cur.execute(sum_sql, {'startDate': DUMMY_DATES[0], 'skuIds': [DUMMY_SKU]})
        fr = cur.fetchall()
        total = int(fr[0]['qtySold']) if fr else 0
        assert total == sum(got.values()) == 9, f'合计交叉核对不符: {total} vs {sum(got.values())}'
        ok(cur, 'listQtySince.与sumQtySince合计交叉核对')

        # 5) 真库业务行全量交叉核对:所有真实 sku 近 7 天,逐日合计 == 合计语句
        week_ago = date.today() - timedelta(days=6)
        sku_list = []
        cur.execute('SELECT DISTINCT sku_id FROM order_sales_daily WHERE stat_date >= %s AND sku_id <> %s',
                    (week_ago, DUMMY_SKU))
        sku_list = sorted(int(r['sku_id']) for r in cur.fetchall())
        if sku_list:
            cur.execute(list_sql % {'startDate': sql_lit(week_ago), 'skuIds': '(' + ','.join(str(s) for s in sku_list) + ')'})
        series = cur.fetchall()
        by_sku = {}
        for r in series:
            by_sku[r['skuId']] = by_sku.get(r['skuId'], 0) + int(r['qtySold'])
        sum_ids = '(' + ','.join(str(s) for s in sku_list) + ')' if sku_list else '(990001)'
        cur.execute(sum_sql % {'startDate': sql_lit(week_ago), 'skuIds': sum_ids})
        for r in cur.fetchall():
            assert by_sku.get(r['skuId'], 0) == int(r['qtySold']), \
                f"真库 sku={r['skuId']} 交叉核对不符: {by_sku.get(r['skuId'], 0)} vs {r['qtySold']}"
        ok(cur, f'listQtySince.真库业务行交叉核对({len(by_sku)} 个 SKU,{len(series)} 行)')

        # 6) 哑元清理
        cur.execute('DELETE FROM order_sales_daily WHERE sku_id=%s', (DUMMY_SKU,))
        conn.commit()
        cur.execute('SELECT COUNT(*) AS n FROM order_sales_daily WHERE sku_id=%s', (DUMMY_SKU,))
        assert cur.fetchone()['n'] == 0
        ok(cur, '哑元清理')

        print(f'ALL GREEN({len(PASSED)} 项)')
    finally:
        conn.close()


if __name__ == '__main__':
    main()
