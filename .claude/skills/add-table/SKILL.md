---
name: add-table
description: 新增或修改数据库表/字段时使用(建表、加列、加索引、改字段含义)。凡涉及 schema 变更的任务先加载本流程,防止 脚本↔docs/03↔实体 三方漂移。
---

# 表结构变更流程(三方一致:脚本 ↔ docs/03 ↔ 实体)

## 步骤(顺序不可换)
1. **先在 `docs/03-数据库设计.md` 登记表设计**(表/列/COMMENT/唯一键/索引),后动手
2. 改 `docs/sql/01_schema_init.sql`,保持幂等(`CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE`)
3. **已建库环境**:ALTER 补丁登记进 TODO.md 对应条目(写明"此前已建库需手工执行")
4. 实体与 Mapper:实体字段=表字段驼峰;库 `is_xxx` ↔ 实体去 is 前缀;改字段含义必须同步 COMMENT

## 建表规约(细则见 docs/07 §6)
- 必带三列:`id / created_at / updated_at`(注意:是 created_at,非《手册》的 create_time——项目偏差已声明)
- **每列必须 COMMENT 中文释义**:erp-codegen 拿 COMMENT 生成实体 javadoc,业务列缺失会打警告(标准列 id/created_at/updated_at 才有默认兜底);含义变更必须同步 COMMENT
- 小数 `decimal` 禁 float/double;金额 DECIMAL(12,4)、汇率 DECIMAL(12,8);状态 `status` TINYINT 且注释写明枚举含义
- utf8mb4;**禁外键与级联**;业务唯一键必须建(本项目幂等的根本,如 `uk_shop_platform_order`)
- 表命名 `业务_作用`,模块前缀对齐(shop_/product_/shop_order_/inventory_…)
