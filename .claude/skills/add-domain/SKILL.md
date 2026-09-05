---
name: add-domain
description: 在某个业务模块内新增一个业务域(新表+实体/Mapper/Service/Controller+菜单权限+测试)时使用,如订单查询、库存流水、采购单等新功能。凡"开始开发某个休眠模块/新增一套接口"的任务先加载本流程。
---

# 新增业务域流程(休眠模块开工)

## 前置
- 17 个模块骨架已在根 pom 注册、已聚合进 erp-api,**无需新建 maven 模块**;只检查目标模块 pom 依赖够不够(生成器有 pom 守卫,缺依赖会报错;参照 erp-shop:web + mybatis-plus boot4-starter + extension + jsqlparser;**禁引 Security starter**,安全收口 erp-api)
- **标准五件套(Entity/Mapper/Service/Controller/单测骨架)一律先跑 erp-codegen 生成器**(存在即跳过),AI 只在骨架上补业务规则与 TODO 槽位;**禁止手写五件套样板**
- 涉及建表 → 先走 add-table 流程(docs/03 登记 → 01_schema_init.sql → 实体)
- 依赖方向:业务模块可依赖 erp-common / erp-platform-sdk;**模块间禁止横向依赖**,跨域调用在 erp-api 编排

## 步骤(顺序不可换)
1. 建表完成后跑生成器出五件套:`mvn -q -pl erp-codegen compile exec:java -Dtable=<表名> -Dmodule=<模块名> -DnameZh=<中文名> [-DtodoId=N]`(参数与边界见 erp-codegen/README.md);包结构 `com.own.erp.<module>` 下 entity/mapper/service/controller,`@MapperScan("com.own.erp.**.mapper")` 已全覆盖,无需登记
2. Service 在生成骨架上用 plain @Service 补业务逻辑(**禁 IService/ServiceImpl**,MP 3.5.9+ 已移除;生成器本就不产出)
3. Controller:@Tag/@Operation 中文;返回体 `Result<T>`;分页 `PageQuery`;管理类接口 `@PreAuthorize("hasRole('...')")` 按 role_key;**Controller 不直连 Mapper**(整域收口 Service,docs/07 §2.1)
4. 字段细则(金额 DECIMAL(12,4)/实体驼峰/is_xxx 去前缀)按 add-table 与 docs/07 §6
5. 复杂逻辑/金额计算/状态机/AI:只写 `TODO(编号)` 注释 + 实现指引并登记 TODO.md(铁律 1);简单 CRUD 直接生成
6. 菜单权限:`INSERT IGNORE INTO sys_menu (...)` 幂等种子登记进 01_schema_init.sql,含 admin 角色绑定;按 role_key 鉴权,perm_key 预留按钮级
7. 单测:生成器已出 Mockito 骨架,AI 只补业务断言;每个 Service ≥1 个(src/test/java,AIR 原则);金额计算/状态机/幂等 upsert/库存 change 必测(docs/07 §10)
8. `mvn -DskipTests compile` 必须通过;交付前走 self-review 流程
