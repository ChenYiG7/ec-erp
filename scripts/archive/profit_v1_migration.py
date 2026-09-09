#!/usr/bin/env python3
"""#19③ 利润核算 V1 开发库对齐(2026-09-08):
   inventory_flow 加成本两列(unit_cost/cost_amount,移动加权动账快照)+ sku_cost_state 建表
   (SKU 移动加权成本账)。全部幂等:建表 CREATE IF NOT EXISTS,加列先查 information_schema
   (MySQL 无 ADD COLUMN IF NOT EXISTS);与 docs/sql/01_schema_init.sql 逐字同源。
   连接信息读 local.properties(键=环境变量名),不含硬编码密码。跑完打印自检。"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from menu_tool import connect  # noqa: E402

DDL_FLOW_COLUMNS = [
    ("unit_cost",
     "ALTER TABLE inventory_flow ADD COLUMN unit_cost DECIMAL(18,8) NULL "
     "COMMENT '动账单价快照(CNY,移动加权 #19③):IN_PURCHASE=采购单价(缺价暂估当时加权价)/OUT_SHIP=结转时加权价/"
     "IN_RETURN·ADJUST=当时加权价;IN_TRANSIT·LOCK_SHIP·TRANSFER_OUT·TRANSFER_IN 不进成本账为NULL' AFTER biz_id"),
    ("cost_amount",
     "ALTER TABLE inventory_flow ADD COLUMN cost_amount DECIMAL(18,2) NULL "
     "COMMENT '动账成本额(CNY,带符号=quantity×unit_cost方向随动账:入库正/出库负;不进成本账类型为NULL;"
     "Σ可重放校验 sku_cost_state)' AFTER unit_cost"),
]

DDL_TABLES = [
    """CREATE TABLE IF NOT EXISTS sku_cost_state (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    sku_id       BIGINT NOT NULL COMMENT 'SKU ID(product_sku.id)',
    total_qty    INT NOT NULL DEFAULT 0 COMMENT '账本结存数量(全局跨仓,移动加权分母;与 inventory 在库量口径一致可核对)',
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '账本结存金额(CNY,移动加权分子)',
    avg_cost     DECIMAL(18,8) NOT NULL DEFAULT 0 COMMENT '当前移动加权单价(CNY)=total_amount/total_qty;结存清零时保留末次价作下次入库暂估基准',
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_sku (sku_id)
) COMMENT='SKU移动加权成本账(出库成本事实源:OUT_SHIP 行 unit_cost 快照落 inventory_flow)'""",
]


def main():
    conn = connect()
    try:
        cur = conn.cursor()
        # 1) inventory_flow 缺列补列(幂等)
        cur.execute("SELECT column_name FROM information_schema.columns "
                    "WHERE table_schema='erp' AND table_name='inventory_flow'")
        existing = {row[0] for row in cur.fetchall()}
        for col, ddl in DDL_FLOW_COLUMNS:
            if col not in existing:
                cur.execute(ddl)
                print(f"added inventory_flow.{col}")
            else:
                print(f"inventory_flow.{col} exists, skip")
        conn.commit()
        # 2) sku_cost_state 建表(幂等)
        for sql in DDL_TABLES:
            cur.execute(sql)
        conn.commit()
        # 自检:两列存在 + 表存在 + uk_sku 索引
        cur.execute("SELECT COUNT(*) FROM information_schema.columns "
                    "WHERE table_schema='erp' AND table_name='inventory_flow' "
                    "AND column_name IN ('unit_cost','cost_amount')")
        cols = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM information_schema.tables "
                    "WHERE table_schema='erp' AND table_name='sku_cost_state'")
        tables = cur.fetchone()[0]
        cur.execute("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                    "WHERE table_schema='erp' AND table_name='sku_cost_state' AND index_name='uk_sku'")
        indexes = cur.fetchone()[0]
        print(f"flow_columns={cols}/2 tables={tables}/1 indexes={indexes}/1")
        print("ALL GREEN" if cols == 2 and tables == 1 and indexes == 1 else "CHECK FAILED")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
