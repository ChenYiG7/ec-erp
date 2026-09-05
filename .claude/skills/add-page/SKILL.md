---
name: add-page
description: 为 erp-web/ 前端新增一个业务页面(CRUD 列表页/只读列表页)时使用,如店铺管理、采购单列表等。凡"给后端某域做前端页面"的任务先加载本流程;生成器优先,禁从零手写同构样板。
---

# 新增前端页面流程(erp-web)

## 前置
- **后端 8088 已起**(api:sync 需要它);后端域还没建 → 先回仓库根会话走 add-domain/add-table,前端等契约落定
- **契约唯一查询源 = `erp-web/tools/openapi.json`**(api:sync 产出);前端会话禁读后端 Java 源码,字段注释/类型一切以快照为准
- 先读 `docs/09-前端开发规范守则.md`(§11 生成器优先、§2 api 收口、§4 ProTable 范式)

## 步骤(顺序不可换)
1. `pnpm api:sync` 刷新契约快照;git diff 核对接口增减(移除路径的存量生成物需人工复核)
2. 写 `erp-web/tools/specs/<domain>.txt` 行式拍板表:头段必填六件(module/domain/entity/nameZh/permPrefix/todoId)+
   字段行(role 四选一,dict/enum 二选一,money/required/hide/tag 裸标记);语法与示例见 `erp-web/tools/README.md`,
   现成样板 `tools/specs/shop.txt`(带 auth-url 动作)/ `tools/specs/shop-product.txt`(readonly 只读域)
3. `pnpm gen:page --spec tools/specs/<domain>.txt` 生成四件 + 菜单 SQL(存在即跳过;重生成用 `--force`,先 diff 人工改动);
   守卫报错(缺头/未知键/todoId 未登记)按行号改 spec,禁绕过守卫手写
4. 人工补槽位:页面动作按钮/字典联动/复杂校验等生成器留的 `TODO(#编号)` 注释,**编号先登记 TODO.md**(铁律 1);
   简单 CRUD 无槽位则此步跳过
5. 菜单 SQL:核对打印的 `INSERT IGNORE INTO sys_menu` id 未占用后执行,并回写 `docs/sql/01_schema_init.sql` 种子
   (component 权威源 = 前端 spec,防前后端菜单漂移);按钮级权限(menuType=3)顺手一并登记
6. 门禁:`pnpm type:check` + `pnpm lint`(+ `pnpm build` 提交前)全绿;erp-web 无独立 .git,husky 不生效,门禁手动跑
7. 交付前走 self-review 流程;纯 CRUD 同款页面不写 devlog,有拍板/坑级内容才写
