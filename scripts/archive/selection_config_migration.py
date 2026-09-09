#!/usr/bin/env python3
"""#17 智能选品工作流开发库对齐(2026-09-08):
   sys_config AI 组补 5 销售键(erp.ai.selection.sales-weight/trend-weight/margin-weight/
   llm-max-items/prompt)。全部幂等(INSERT IGNORE,已有行保留现值不覆盖),
   与 docs/sql/01_schema_init.sql 种子逐字同源。跑完打印行数自检。"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from menu_tool import connect  # noqa: E402

CONFIG_ROWS = [
    ("AI", "erp.ai.selection.sales-weight", "0.4",
     "选品:销量规模维度权重(三维按和归一化;销量规模=近30天销量/候选集最大销量)"),
    ("AI", "erp.ai.selection.trend-weight", "0.3",
     "选品:动销趋势维度权重(三维按和归一化;趋势=近7天日均 vs 前7天日均)"),
    ("AI", "erp.ai.selection.margin-weight", "0.3",
     "选品:毛利率维度权重(三维按和归一化;毛利=30天窗口利润/销售额,30%记满分,缺失记中性)"),
    ("AI", "erp.ai.selection.llm-max-items", "20",
     "选品:单轮送 LLM 写推荐理由的入选行上限(超限按综合分降序截断走模板,成本护栏)"),
    ("AI", "erp.ai.selection.prompt",
     "你是电商 ERP 的选品分析助手。根据给定的 SKU 评分明细(综合评分/销量趋势/毛利率/库存),"
     "为每个入选 SKU 写一句不超过 50 字的中文推荐理由(说明为什么值得重点关注)。"
     "只输出 JSON 数组,元素形如 {\"skuId\":1,\"summary\":\"...\"},不输出任何其他文字。",
     "选品摘要节点 system 提示词"),
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
        # 自检:选品组 5 键
        cur.execute("SELECT config_key, config_value FROM sys_config "
                    "WHERE config_key LIKE 'erp.ai.selection.%' ORDER BY config_key")
        cfg = cur.fetchall()
        print(f"selection_keys={len(cfg)}")
        for row in cfg:
            print(f"  {row[0]} = {row[1][:60]}")
        print("ALL GREEN" if len(cfg) == 5 else "CHECK FAILED")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
