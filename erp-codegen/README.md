# erp-codegen(CRUD 脚手架生成器)

开发工具,**不属于任何运行时依赖**(零业务依赖,不作为任何模块的 parent/dependency)。从权威建表脚本 `docs/sql/01_schema_init.sql` 解析 DDL,在目标业务模块内生成八件套骨架,替代手写样板。

## 生成物

| 文件 | 风格依据 |
|---|---|
| `entity/Xxx.java` | **@Data+@Builder+双构造 默认四注解**(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),@Builder 供纯构造装配位;`@Data/@TableName/@TableId(AUTO)`,列 COMMENT → javadoc(标准列 id/created_at/updated_at 无 COMMENT 时自动补默认中文注释),含 created_at/updated_at(表里有才生成) |
| `mapper/XxxMapper.java` | `extends BaseMapper<Xxx>` |
| `request/query/XxxQuery.java` | 继承 PageQuery(GET 查询串自动绑定,pageSize 钳制);过滤条件字段人工按需补。**保持 class**——record 不能继承 PageQuery(模型可变性分级的继承例外,docs/07 §1) |
| `response/XxxResponse.java` | **record+@Builder**(模型可变性分级 docs/07 §1):全字段 + 显式 `from(entity)` builder 命名传参映射(防相邻同类型字段错位);**敏感/服务端管理字段必须人工删减**(docs/07 §1) |
| `request/command/XxxSaveRequest.java` | **record+@Builder**;已剔除 id/created_at/updated_at;其余服务端管理列(如 merchant_id)人工删减;显式 `toEntity()` 用 entity builder 链一次成型(纯构造位,与 from 同风格防错位);@Valid 约束注解放组件上人工按需补;**含敏感字段(凭证/密码)必须手写 toString 脱敏**——record 自动 toString 无法排除组件 |
| `service/XxxService.java` | plain `@Service` + `@RequiredArgsConstructor` 整域收口(docs/07 §2.1);入参 query/Query+command/SaveRequest(CQRS 分包)、出参 response/Response,entity 不出本层;page/getById/save/update/delete |
| `controller/XxxController.java` | `@Tag`/`@Operation` 中文、`Result` 包装、`@Valid @RequestBody` 写侧、分页走 XxxQuery 钳制版 |
| `service/XxxServiceTest.java` | Mockito 单测(mock Mapper,不依赖 DB,AIR 原则);readOnly 模式只产 page/getById 两用例 |
| 控制台 | 可选打印 `sys_menu` 菜单注册 SQL |

> **系统写入表**(平台同步/任务落库,docs/03 设计要点 1)统一 `readOnly=true`:订单、拉取日志、listing、库存流水等
> 不开放人工 CRUD 写接口,写入口唯一(同步 upsert/落库方法留 TODO 由人工实现)。

**文件头**:八件套一律带文件头(`@author` / `@Date` / `@Description`),位于**最后一个 import 之后、类声明/类级注解之前**,与类注释合并成一块(`@Description` 即类职责说明);渲染唯一真相源 `CodeGenerator#fileHeader`;存量已于 2026-09-03 补齐(当时用一次性脚本,已弃置,格式真相源不变)。
**只产骨架不产业务**:复杂规则必须由人工补齐——生成器只留 `TODO(编号)` 注释槽位(`todoId` 参数),遵守 docs/07 §1(复杂逻辑/AI 只写 TODO 占位)与 §2.1(业务规则下沉 Service)。
**映射一律显式逐字段**(`from`/`toEntity`),禁反射拷贝(BeanUtil.copyProperties 漏字段/类型不匹配静默,docs/07 §12)。

## 用法

在项目根目录执行:

```bash
mvn -q -pl erp-codegen compile exec:java \
  -Dexec.args="table=inventory module=inventory path=/api/inventory/inventories todoId=7"
```

中文参数(如 `nameZh`)建议用 `-D` 系统属性传(`exec:java` 与 Maven 同 JVM,避免控制台编码问题):

```bash
mvn -q -pl erp-codegen compile exec:java -Dtable=inventory -Dmodule=inventory -DnameZh=库存 -DtodoId=7
```

## 参数

| 参数 | 必填 | 说明 |
|---|---|---|
| `table` | ✅ | `01_schema_init.sql` 中的表名 |
| `module` | ✅ | 目标模块名(= 包名段与 `erp-` 前缀模块名,如 `inventory`) |
| `path` | | REST 路径,默认 `/api/<module>/<复数kebab>`(inventory → `/api/inventory/inventories`) |
| `nameZh` | | 中文组名(@Tag 等),默认取表 COMMENT 去掉括号说明 |
| `todoId` | | 在 Service 留 `TODO(#编号)` 业务规则占位,编号须登记 TODO.md |
| `menuParent` | | 传了则打印 `sys_menu` 菜单注册 SQL(人工核对 ID 后执行) |
| `parts` | | 逗号分隔生成件子集(`entity,mapper,service,query,response,saveRequest,controller,test`),默认全部;单据子表典型用法 `parts=entity,mapper` |
| `readOnly` | | `true`=系统写入表只读模式:不产 SaveRequest,Service/Controller/Test 只渲染读侧(对外仅查询,写入口在 Service 类注释留 TODO 槽位);与 `parts` 显式含 `saveRequest` 冲突时报错 |
| `force` | | `true` = 覆盖已存在文件;**默认存在即跳过**(逐文件判断,绝不静默覆盖) |
| `author` | | 文件头 `@author`,默认 `chenyi` |
| `date` | | 文件头 `@Date`,默认今天(`yyyy/M/d`,月日不补零) |

## 守卫与边界

- **pom 守卫**:目标模块缺 `web / validation / MP 三件套 / starter-test` 依赖时报错退出(参照 `erp-goods/pom.xml` 补齐);
- **类型红线**:DDL 里出现 `float/double` 直接报错(禁用,docs/07 §6.1);未识别类型报错并提示扩展 `javaType()`,不静默猜;
- **COMMENT 警告**:业务列缺 COMMENT 会打印警告清单(表/列必须带中文释义的规约,实体 javadoc 全靠它),不阻断生成;标准列自动兜底默认注释;
- **不解析表达式列/自定义 SQL**:生成器只覆盖本仓 DDL 风格(`CREATE TABLE IF NOT EXISTS` + 列 COMMENT + UNIQUE/KEY);
- 生成的代码不自动注册菜单/权限,`menuParent` 打印的 SQL 需人工核对执行。

## 典型流程(新业务域)

1. 按 `.claude/skills/add-table` 走表变更流程(docs/03 登记 → 脚本 → ALTER)
2. 跑本生成器产出八件套骨架(人工先删 Response/SaveRequest 里不该对外的字段)
3. 人工补业务规则(替换 `TODO(编号)` 槽位),新编号登记 TODO.md
4. `mvn -DskipTests compile` + 目标模块 `mvn -pl erp-<module> test`

## 状态机守卫测试生成器(StateMachineTestGenerator)

"条件更新即守卫"状态机(#10 采购/#11 发货/#12 售后已四连)的 Mockito 守卫测试高度同构,按铁律 #8 沉淀为本工具:八件套的 `test` 件只产 CRUD 冒烟骨架,本工具补**业务动作的守卫用例**。spec 文件驱动(行式,UTF-8),spec 即状态机拍板表的文档化——状态机改了改 spec,重跑再生成。

```bash
mvn -q -pl erp-codegen compile exec:java \
  -Dcodegen.mainClass=com.own.erp.codegen.StateMachineTestGenerator \
  -Dspec=erp-aftersale/testgen-aftersale.txt [-Dforce=true]
```

产出 `<module>` 内 `service/<Entity>StateMachineTest.java`(与手写 `XxxServiceTest` 并存,存在即跳过),每行 action 产一个合并式 @Test,覆盖守卫四类:①cas 命中(thenReturn(1) 不抛)+ 可选成功路径实参核对(verify);②单不存在(preRead stub 404);③cas 脱靶(thenReturn(0) → assertThrows + "动词+失败"消息 contains);④尾参必填(required → 空白实参 assertThrows + never() 副作用校验)。

### spec 语法(样板:`erp-aftersale/testgen-aftersale.txt`)

| 行 | 说明 |
|---|---|
| `module` / `entity` / `var` | ✅ 目标模块、实体名、Service 字段名(产 `<Entity>StateMachineTest`) |
| `constClass` | 域常量类全名;裸 `STATUS_X/TYPE_X/FLOW_X` token 自动补简名前缀(引号内不动),缺省则字面量原样 |
| `mock` | ✅ 至少一行,按序生成字段+mock+构造注入;短名默认本模块 mapper 包,含点号按完整 fqcn |
| `todoId` + `manual` | 类尾 `TODO(#编号) 人工补齐:...` 槽位;**manual 必须配 todoId(禁裸 TODO)**,编号登记 TODO.md |
| `fixture<<` … `>>` | 造数辅助方法原样注入(领域私有,如 `order(String type)`);缩进相对块内首行 |
| `action=<方法> \| cas=<mapper方法> \| stub=<实参> \| call=<实参> \| fail=<消息前缀>` | ✅ 一行一动作;stub/call 是**逐字 Java 实参**(4 参含 result / 3 参 / 白名单型 2 参统一覆盖),长度 >60 按顶层逗号折行 |
| 可选项:`preRead=<stub表达式>` \| `missingMsg=<消息>` \| `required=<消息>` \| `verify` \| `name=<后缀>` | preRead 有则命中/脱靶同 id 重复 stub,无则脱靶换 2L;同动作多行(如 agree 类型分流)必须 `name=` 区分,重名报错 |

### 边界(必须人工,生成器只留 TODO 槽位)

- 跨域契约断言(InventoryChangeApi ArgumentCaptor 逐字段、发足/收齐判定、`casOrderStatus` boolean 包装);
- 类型分流的全量负例(如 refund 前置态白名单 SQL `IN` 之外的路径)、多行明细造数、DuplicateKey 翻译;
- 复合事务动作(delivery `ship`、inbound `confirm` 这类带动账编排的)整体不在射程,只适用"单条 cas 即守卫"的动作;
- spec 表达不了的直接不写 spec,人工用例继续落在手写 `XxxServiceTest`。
