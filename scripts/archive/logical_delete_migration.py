#!/usr/bin/env python3
"""TODO#7 逻辑删除开发库对齐(2026-09-08):
   11 张人工域表(sys_user/sys_role/sys_menu/sys_dict/brand/product/product_category/product_sku/shop/warehouse/supplier)
   补 `deleted BIGINT NOT NULL DEFAULT 0` 列;6 个业务唯一键重建为含 deleted
   (删时置主键 id,deleted=0 与各已删行 id 天然互异 → 删后同键可重建且可重复删)。
   全部幂等(information_schema 判列判索引,已就位即跳过);平台同步正本/单据/流水/AI/settlement 域不在此列(拍板见 TODO#7)。
   连接信息读 local.properties(menu_tool.connect),不含硬编码密码。跑完打印自检。"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from menu_tool import connect  # noqa: E402

SCHEMA = "erp"
COLUMN_SQL = ("ADD COLUMN deleted BIGINT NOT NULL DEFAULT 0 "
              "COMMENT '逻辑删除:0=正常,非0=已删(值=被删行id),见 TODO#7' AFTER updated_at")

TABLES = ["sys_user", "sys_role", "sys_menu", "sys_dict",
          "brand", "product", "product_category", "product_sku",
          "shop", "warehouse", "supplier"]

# 表 -> (索引名, 重建后列序)
UK_REBUILDS = {
    "sys_user": ("uk_username", ("username", "deleted")),
    "sys_role": ("uk_role_key", ("role_key", "deleted")),
    "product": ("uk_spu", ("spu_code", "deleted")),
    "product_sku": ("uk_sku", ("sku_code", "deleted")),
    "shop": ("uk_platform_seller", ("platform", "seller_id", "deleted")),
    "supplier": ("uk_name", ("name", "deleted")),
}


def column_exists(cur, table):
    cur.execute(
        "SELECT COUNT(*) FROM information_schema.columns "
        "WHERE table_schema=%s AND table_name=%s AND column_name='deleted'",
        (SCHEMA, table))
    return cur.fetchone()[0] > 0


def uk_columns(cur, table, index_name):
    """返回该索引当前列序(索引不存在返回 None;information_schema.statistics 按 索引×列 逐行)。"""
    cur.execute(
        "SELECT column_name FROM information_schema.statistics "
        "WHERE table_schema=%s AND table_name=%s AND index_name=%s ORDER BY seq_in_index",
        (SCHEMA, table, index_name))
    rows = cur.fetchall()
    return None if not rows else tuple(r[0] for r in rows)


def main():
    conn = connect()
    try:
        cur = conn.cursor()
        added, rebuilt, skipped = [], [], []
        for table in TABLES:
            if column_exists(cur, table):
                skipped.append(f"{table}.deleted")
            else:
                cur.execute(f"ALTER TABLE {table} {COLUMN_SQL}")
                added.append(f"{table}.deleted")
        for table, (index_name, cols) in UK_REBUILDS.items():
            current = uk_columns(cur, table, index_name)
            if current == cols:
                continue  # 已是目标形态,幂等跳过
            col_list = ", ".join(cols)
            if current is None:
                cur.execute(f"ALTER TABLE {table} ADD UNIQUE KEY {index_name} ({col_list})")
            else:
                cur.execute(f"ALTER TABLE {table} DROP INDEX {index_name}, "
                            f"ADD UNIQUE KEY {index_name} ({col_list})")
            rebuilt.append(f"{table}.{index_name} -> ({col_list})")
        conn.commit()

        # 自检:11 列 + 6 唯一键含 deleted
        col_ok = sum(1 for t in TABLES if column_exists(cur, t))
        uk_ok = sum(1 for t, (idx, cols) in UK_REBUILDS.items() if uk_columns(cur, t, idx) == cols)
        print(f"added={added}")
        print(f"rebuilt={rebuilt}")
        print(f"自检 columns={col_ok}/11 uks_with_deleted={uk_ok}/6 "
              f"(本次新增 {len(added)} 列 / 重建 {len(rebuilt)} 键 / 原已就位 {len(skipped)} 列)")
        print("ALL GREEN" if col_ok == 11 and uk_ok == 6 else "CHECK FAILED")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
