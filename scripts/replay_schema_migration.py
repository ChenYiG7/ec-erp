# -*- coding: utf-8 -*-
"""存量库幂等重放:把 docs/sql/01_schema_init.sql 唯一正本安全重放到已建库(TODO"存量库重放"统一 runner)。

背景:正本全部为 CREATE IF NOT EXISTS / INSERT IGNORE(2026-09-12 核对,无顶层 UPDATE/ALTER),
但 ALTER 不可幂等(MySQL 无 ADD COLUMN IF NOT EXISTS),历史加列只以注释登记在正本里;
且种子串内含分号(如 sys_config remark '...RustFS;false...'),裸分号切分会截断语句。
本脚本按"剥离整行 -- 注释 → 引号感知切分 → 逐条执行"重放正本,再按正本注释段补齐历史加列
(information_schema 判存,缺则 ALTER,幂等),最后对照正本段校验表/列/索引/菜单/授权/config 全就位。

覆盖余量(2026-09-12):#19/#25/#27①②③/#29/#30(3808/3809)/#31(菜单+加列)/#32/#33/fba-shipment 五表与菜单 46 段;
    #11 回传余量四列(sync_status/sync_retry_count/sync_fail_reason/sync_time)+idx_sync(2026-09-12 追加)。
哑元:无——只做 DDL/种子重放,不触碰业务数据。用法:python scripts/replay_schema_migration.py
"""
import io
import re
import sys
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parent.parent
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
PASSED = []

# 历史加列(来源 = 正本内"已建库手工补齐"注释段,本脚本是其可执行形态):
#   (表, [(列, 列定义), ...])  —— 逐列判存,只补缺失列,已存在跳过
HISTORY_COLUMNS = {
    'sys_user': [("dept_id", "BIGINT NULL COMMENT '部门ID(sys_dept.id,#27③)'")],
    'shop_order': [
        ("order_source", "VARCHAR(16) NOT NULL DEFAULT 'PLATFORM' COMMENT '订单来源:PLATFORM平台拉单/MANUAL内销手工录单(#29)'"),
        ("review_status", "TINYINT NOT NULL DEFAULT 0 COMMENT '审核状态:0无需审核/1待审核/2已通过/3已驳回(#29)'"),
        ("review_remark", "VARCHAR(500) NULL COMMENT '审核/风控备注(审核动作写入,#29)'"),
        ("reviewed_by", "BIGINT NULL COMMENT '审核人(sys_user.id,#29)'"),
        ("reviewed_at", "DATETIME NULL COMMENT '审核时间(#29)'"),
        ("risk_flag", "VARCHAR(255) NULL COMMENT '命中风控规则摘要(#29)'"),
    ],
    'supplier': [("settle_days", "INT NULL COMMENT '账期天数(V1仅展示,到期提醒随预警引擎评估,TODO#31)'")],
    'purchase_order': [("audit_time", "DATETIME NULL COMMENT '审核时间(audit 动作落,#31 账期起算锚点;2026-09-12 加列,已建库跑 replay_schema_migration.py 补齐)'")],
    'transfer_order': [("transit_mode", "VARCHAR(16) NOT NULL DEFAULT 'DIRECT' COMMENT '动账模式(#30 余量①):DIRECT确认即达(V1默认)/IN_TRANSIT在途(OUT→到货IN);2026-09-12 加列,已建库跑 replay_schema_migration.py 补齐'")],
    'product_sku': [
        ("length_mm", "INT NULL COMMENT '外长(毫米),头程/FBA装箱属性(#33)'"),
        ("width_mm", "INT NULL COMMENT '外宽(毫米),头程/FBA装箱属性(#33)'"),
        ("height_mm", "INT NULL COMMENT '外高(毫米),头程/FBA装箱属性(#33)'"),
    ],
    'ai_kb_document': [
        ("original_file_key", "VARCHAR(512) NULL COMMENT 'OSS 对象键(原文存储,#25;NULL=未存原文:粘贴文本或OSS未启用)'"),
        ("original_file_size", "BIGINT NULL COMMENT '原文字节数(#25,与original_file_key成对)'"),
    ],
    'delivery_order': [
        ("sync_status", "VARCHAR(32) NULL COMMENT '平台回传状态(#11 激活期余量 2026-09-12):NULL未发货无关(存量已发货单不回溯)/PENDING待回传/SUCCESS回传成功/FAILED回传失败'"),
        ("sync_retry_count", "INT NOT NULL DEFAULT 0 COMMENT '回传补偿重试次数(失败路径+1,达上限停扫待人工;#11 激活期余量 2026-09-12)'"),
        ("sync_fail_reason", "VARCHAR(500) NULL COMMENT '最近一次回传失败原因(成功即清空;#11 激活期余量 2026-09-12)'"),
        ("sync_time", "DATETIME NULL COMMENT '最近一次回传尝试时间(补偿扫退避基准:下次重试需距此 N×backoff 分钟;#11 激活期余量 2026-09-12)'"),
    ],
}
# 历史加索引:表 → [(索引名, 列)]
HISTORY_INDEXES = {
    'shop_order': [('idx_review', 'review_status')],
    'delivery_order': [('idx_sync', 'sync_status, status')],
}


def load_props():
    props = {}
    for line in (ROOT / 'local.properties').read_text(encoding='utf-8', errors='ignore').splitlines():
        line = line.strip()
        if not line or line.startswith('#') or '=' not in line:
            continue
        k, v = line.split('=', 1)
        props[k.strip()] = v.strip()
    return props


def split_statements(sql_text):
    """引号感知切分:整行 -- 注释已剥离;单引号串内分号不切,处理 '' 双写与 \\ 转义两种形态。"""
    statements, buf, in_str, i = [], [], False, 0
    while i < len(sql_text):
        ch = sql_text[i]
        if in_str:
            buf.append(ch)
            if ch == '\\' and i + 1 < len(sql_text):      # 反斜杠转义(如 \')
                buf.append(sql_text[i + 1])
                i += 2
                continue
            if ch == "'":
                if i + 1 < len(sql_text) and sql_text[i + 1] == "'":   # '' 双写=串内字面引号
                    buf.append("'")
                    i += 2
                    continue
                in_str = False
        else:
            if ch == "'":
                in_str = True
                buf.append(ch)
            elif ch == ';':
                stmt = ''.join(buf).strip()
                if stmt:
                    statements.append(stmt)
                buf = []
            else:
                buf.append(ch)
        i += 1
    tail = ''.join(buf).strip()
    if tail:
        statements.append(tail)
    return statements


def column_exists(cur, table, column):
    cur.execute("SELECT COUNT(*) FROM information_schema.columns "
                "WHERE table_schema=DATABASE() AND table_name=%s AND column_name=%s", (table, column))
    return cur.fetchone()[0] > 0


def index_exists(cur, table, index_name):
    cur.execute('SHOW INDEX FROM ' + table)
    return any(r[2] == index_name for r in cur.fetchall())


def main():
    props = load_props()
    conn = pymysql.connect(host=props['MYSQL_HOST'], port=int(props.get('MYSQL_PORT', 3306)),
                           user=props['MYSQL_USERNAME'], password=props['MYSQL_PASSWORD'],
                           database='erp', charset='utf8mb4', autocommit=True)
    cur = conn.cursor()
    cur.execute('SELECT VERSION()')
    print(f"MySQL {cur.fetchone()[0]} —— 存量库幂等重放(正本 docs/sql/01_schema_init.sql)\n")

    schema = (ROOT / 'docs/sql/01_schema_init.sql').read_text(encoding='utf-8')
    # 剥离整行 -- 注释:注释段含 `;`(如 ALTER 示例与中文分号),不剥离会被切分误断
    schema = '\n'.join(l for l in schema.splitlines() if not l.lstrip().startswith('--'))
    statements = split_statements(schema)

    # ---------- 1. 正本逐条重放(全幂等;防呆:混入非幂等语句立即中止,人工核对) ----------
    n_create = n_insert = 0
    for stmt in statements:
        head = re.match(r'(\w+)', stmt, re.I).group(1).upper()
        if head == 'CREATE':
            if not re.match(r'CREATE\s+(DATABASE\s+IF\s+NOT\s+EXISTS|TABLE\s+IF\s+NOT\s+EXISTS|UNIQUE\s+INDEX|INDEX)',
                            stmt, re.I):
                raise SystemExit(f'正本混入非幂等 CREATE,中止人工核对:\n{stmt[:200]}')
            n_create += 1
        elif head == 'INSERT':
            if 'INSERT IGNORE' not in stmt.upper():
                raise SystemExit(f'正本混入裸 INSERT,中止人工核对:\n{stmt[:200]}')
            n_insert += 1
        elif head in ('UPDATE', 'DELETE', 'DROP', 'ALTER', 'TRUNCATE', 'REPLACE'):
            raise SystemExit(f'正本混入非幂等语句 {head},中止人工核对:\n{stmt[:200]}')
        else:
            continue  # SET/USE 等环境语句原样执行
        cur.execute(stmt)
    print(f'  ✅ 正本逐条重放:CREATE×{n_create} + INSERT IGNORE×{n_insert}(全幂等)')
    PASSED.append('replay')

    # ---------- 2. 历史加列/加索引(正本注释段的可执行形态,information_schema 判存幂等) ----------
    for table, cols in HISTORY_COLUMNS.items():
        missing = [(c, d) for c, d in cols if not column_exists(cur, table, c)]
        for col, ddl in missing:
            cur.execute(f'ALTER TABLE {table} ADD COLUMN {col} {ddl}')
        print(f'  ✅ {table} 加列:{len(cols) - len(missing)} 已就位,补 {len(missing)}({",".join(c for c, _ in missing) or "-"})')
    for table, idxs in HISTORY_INDEXES.items():
        for idx, col in idxs:
            if not index_exists(cur, table, idx):
                cur.execute(f'ALTER TABLE {table} ADD KEY {idx} ({col})')
                print(f'  ✅ {table} 补索引 {idx}({col})')
            else:
                print(f'  ✅ {table} 索引 {idx} 已就位')
    PASSED.append('alter')

    # ---------- 3. 就位校验:表 / 列 / 索引 / 菜单 / 授权 / config 对照正本段 ----------
    tables = re.findall(r'CREATE TABLE IF NOT EXISTS (\w+)', schema)
    cur.execute("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() "
                f"AND table_name IN ({','.join('%s' for _ in tables)})", tables)
    hit = cur.fetchone()[0]
    assert hit == len(tables), f'{len(tables) - hit} 张表未就位'
    print(f'  ✅ 正本 {len(tables)} 张表全部就位')
    for table, cols in HISTORY_COLUMNS.items():
        assert all(column_exists(cur, table, c) for c, _ in cols), f'{table} 加列校验未过'
    for table, idxs in HISTORY_INDEXES.items():
        assert all(index_exists(cur, table, idx) for idx, _ in idxs), f'{table} 索引校验未过'

    # 期望菜单 id / 授权对 / config 键,全部从正本语句解析(不是手抄,防漂移);
    # 复用引号感知切分结果——正本种子串内含分号(如 '...RustFS;false...'),裸正则按 `;` 取段会截断
    menu_stmts = [s for s in statements if re.match(r'INSERT IGNORE INTO sys_menu\b', s, re.I)]
    role_stmts = [s for s in statements if re.match(r'INSERT IGNORE INTO sys_role_menu\b', s, re.I)]
    cfg_stmts = [s for s in statements if re.match(r'INSERT IGNORE INTO sys_config\b', s, re.I)]
    menu_ids = {int(m.group(1)) for seg in menu_stmts for m in re.finditer(r'\((\d+),\s*\d+,', seg)}
    role_pairs = {(int(a), int(b)) for seg in role_stmts for a, b in re.findall(r'\((\d+),\s*(\d+)\)', seg)}
    config_pairs = {(g, k) for seg in cfg_stmts for g, k in re.findall(r"\('([^']+)',\s*'([^']+)',", seg)}
    cur.execute('SELECT id FROM sys_menu')
    have_menus = {r[0] for r in cur.fetchall()}
    assert menu_ids <= have_menus, f'菜单缺行:{sorted(menu_ids - have_menus)}'
    cur.execute('SELECT role_id, menu_id FROM sys_role_menu')
    have_pairs = set(cur.fetchall())
    assert role_pairs <= have_pairs, f'角色授权缺行:{sorted(role_pairs - have_pairs)}'
    cur.execute("SELECT config_group, config_key FROM sys_config")
    have_cfg = set(cur.fetchall())
    assert config_pairs <= have_cfg, f'系统参数缺行:{sorted(config_pairs - have_cfg)}'
    print(f'  ✅ 菜单 {len(menu_ids)} 行 / 角色-菜单授权 {len(role_pairs)} 对 / 系统参数 {len(config_pairs)} 键全部就位')
    PASSED.append('verify')

    print(f'\n全部通过:{len(PASSED)}/3 —— 存量库已对齐正本;菜单变更需重新登录生效')
    conn.close()


if __name__ == '__main__':
    main()
