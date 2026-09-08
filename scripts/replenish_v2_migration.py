#!/usr/bin/env python3
"""#6 补货算法 V2 开发库对齐(2026-09-08):
   sys_config AI 组补 2 键(erp.ai.replenish.lead-time-days / erp.ai.replenish.service-level)。
   全部幂等(INSERT IGNORE),与 docs/sql/01_schema_init.sql 种子逐字同源;
   连接信息读 local.properties(键=环境变量名),不含硬编码密码。跑完打印行数自检。"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from menu_tool import connect  # noqa: E402

CONFIG_ROWS = [
    ("AI", "erp.ai.replenish.lead-time-days", "7",
     "补货:采购提前期(天,V2 补货点=提前期需求+安全库存)"),
    ("AI", "erp.ai.replenish.service-level", "0.95",
     "补货:服务水平(0~1,V2 安全库存=z×σ×√提前期,z 按 0.90/0.95/0.98/0.99 档位最近邻映射)"),
]


def main():
    conn = connect()
    try:
        cur = conn.cursor()
        for group, key, value, remark in CONFIG_ROWS:
            cur.execute(
                "INSERT IGNORE INTO sys_config (config_group, config_key, config_value, remark) VALUES (%s,%s,%s,%s)",
                (group, key, value, remark))
        conn.commit()
        # 自检:补货全组 7 键(原 5 + 本次 2;cron 走 yml 占位符不入 sys_config)
        cur.execute("SELECT config_key, config_value FROM sys_config "
                    "WHERE config_key LIKE 'erp.ai.replenish.%' ORDER BY config_key")
        cfg = cur.fetchall()
        print(f"replenish_keys={len(cfg)}")
        for row in cfg:
            print(f"  {row[0]} = {row[1]}")
        print("ALL GREEN" if len(cfg) == 7 else "CHECK FAILED")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
