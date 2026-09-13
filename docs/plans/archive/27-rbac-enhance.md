# #27 权限模块增强实施计划书

> ✅ 已于 2026-09-12 实施(三会话:13a 部门 / 13b 审计 / 13c 数据权限+④评估收口,devlog 见 `docs/devlog/archive/TODO27-权限增强(数据权限店铺轴收口).md`)。
> 13c 实施偏差登记(计划=拍板事实,同 commit 修订 §2.3):计划书撰写时假设「查询面集中收口在契约实现」,
> 代码事实是契约 18 个 Impl 的调用方为 AI 工具(+利润页),**其余前端查询直连各业务模块 Controller**——
> 故前端路径注入落点为各域查询 Controller(GET 绑定后强制覆盖 query.shopIds),授权集提供仍收口
> CurrentUserApiImpl(erp-api),无任何模块自建鉴权逻辑;补注 DeliveryQueryApi(发货单挂订单店铺轴)。
> null=不限 / 非空=IN 过滤 / 空列表=不可见 三态语义(计划书「admin 返空=不限」在非 admin 未授权场景有越权空洞,已按此收紧)。

| 元信息 | 值 |
|---|---|
| TODO 条目 | #27 权限模块增强:①数据权限 ②操作审计 ③部门组织架构 ④字段级权限评估 |
| 优先级 | P2;**内部顺序 ③→②→①→④**(部门为数据权限铺路,审计独立可并行) |
| 前置依赖 | 无外部依赖 |
| 目标一句话 | 部门树 + 用户归属、关键业务操作审计、按授权范围过滤业务数据(店铺轴为主)、字段级权限给出评估结论 |
| 明确不做 | 字段级权限引擎(④只做评估,见 2.5);人员业绩核算(四期,部门表为其铺路);JWT refresh token 机制(现有 24h 无 refresh 维持);菜单级权限模型重构(RBAC 现状保留) |

## 一、背景与现状(2026-09-10 代码事实)

- **RBAC 已投产**:`sys_user`(uk_username+deleted 逻辑删除)/`sys_role`(uk_role_key)/`sys_menu`(menu_type 1目录2菜单3按钮,种子页面 id 1-37+按钮 200+,admin 全量授权)/`sys_user_role`/`sys_role_menu`(联合主键);sys_dict 种子含 shop_platform 14 平台。
- **鉴权链**:`SecurityConfig`(@EnableMethodSecurity,401/403 手写 Result JSON)→ `JwtAuthenticationFilter`(Bearer→`LoginUser(userId, username, nickname, roleKeys)` + `ROLE_<role_key>` authorities)→ `JwtTokenService`(HS256,secret ≥32 字节构造期 fail-fast)→ `AuthContext.current()`。
- **授权方式=纯 `@PreAuthorize("hasRole('admin')")` 方法级注解**(SysUser/SysRole/SysMenu/SystemConfig/PurchaseOrder audit·close/PurchaseInbound confirm·cancel/ExchangeRate save/Kb 写侧等);**perm_key 仅前端 v-auth 展示层裁剪,后端无按钮级鉴权通道**(SysMenu 实体注释明示预留)。
- **契约**:`CurrentUserApi` 仅 `currentUserId()`(Job/系统链路禁用);**LoginUser 无 dept 字段;全仓 sys_dept/审计表/data_scope 零残留**。
- 唯一现存"审计"= AI 工具调用审计(`ai_chat_message` role=TOOL 行);docs/02 §12:部门 ❌/操作审计 ❌/数据权限 ❌(仅角色);docs/10 §2.12 同源。

## 二、方案设计

### 2.1 ③ 部门组织架构(先做,为①铺路)

- `sys_dept`(id/parent_id/dept_name/sort/status/deleted + uk(parent_id,dept_name,deleted))+ `sys_user` 加 `dept_id`。
- 环检测:parent 链成环校验抄 erp-goods 分类成环校验先例;删除引用校验(有用户/子部门不可删,抄仓库/店铺删除校验先例)。
- 端点:部门树 GET / CRUD(写侧 admin);前端:系统域新增部门管理页(add-page)+ 用户表单挂部门选择。
- **JWT 不动**:dept 归属实时查库(用户量级小,不走 token 冗余;避免 24h token 窗口内调部门后权限漂移)。

### 2.2 ② 操作审计

- `sys_oper_log`(见 §四草案)+ **AOP 切面 `@OperLog(module, action)` 注解**挂 Controller 方法(不用 URL 正则,显式声明可读)。
- 首批注解覆盖(TODO 原文五类):订单状态推进(订单审核/review 端点)、库存动账(盘点确认/调拨确认——盘点/调拨域落地后补挂)、发货(发货单 ship/cancel/delete)、发货回传(ShipmentSync 人工触发入口如有)、财务勾稽(结算重拉/退款勾稽人工触发)、采购(audit/close/confirm)。**系统 Job 自动动作不记操作审计**(那是系统行为,traceId+日志已覆盖;操作审计=人工动作)。
- 异步落库:@EventListener(AFTER_COMMIT)或独立线程池,审计失败不阻塞业务(**不回滚业务**);params_json 截断 2KB+敏感字段脱敏(凭证/密钥类字段名黑名单过滤,复用 SECRET 掩码口径)。
- trace_id 取 MDC(与九 Job 同款);保留策略:默认全量保留,V2 评估定期清理 Job(拍板点①)。
- 前端:审计查询页(系统域,只读)。

### 2.3 ① 数据权限(核心拍板项)

- **主轴拍板(拍板点②,本计划最大决策)**:
  - **方案 A(推荐):店铺轴数据权限**——ERP 数据的自然边界是店铺(订单/商品/结算/售后全部挂 shop_id);模型=`sys_user_shop` 授权表(user_id+shop_id,admin 角色绕过全量)。用户可看店铺=授权集。部门轴留作后续报表/业绩维度。
  - 方案 B:部门轴(经典 OA 式,dept 数据隔离)——但库存/采购/仓库数据不挂部门,注入点稀碎,不适配 ERP 数据形态。
  - 结论建议:A 为主,B 的部门结构(2.1)已铺路不冲突;devlog 拍板。
- **注入点拍板(拍板点③)**:**应用层过滤注入(推荐)**——`CurrentUserApi` 扩 `currentShopIds()`(授权店铺集,admin 返空=不限);各域 QueryApi 的 Query record 加 `shopIds` 过滤条件,ApiImpl 实现处装配(调用方是前端/工具时强制注入用户授权集,系统内部调用不变)。理由:本项目查询面集中收口在契约实现(erp-api contract/impl 18 个 Impl),注入点可控、可测、无魔法;MP `DataPermissionInterceptor` 拦截器方案作为二期评估登记(覆盖面难对齐四量/流水/快照杂表,禁现在硬上)。
- 后端按钮级鉴权(perm_key)顺带收口:perm_key 已预留,**本期不加**(展示层 v-auth + admin 方法注解已覆盖当前风险面;按钮级鉴权通道登记 TODO 待需求)。

### 2.4 契约与安全边界

- `CurrentUserApi` 扩方法(全 record 不引 Security 类型):`currentShopIds()`(①);Job/系统链路禁用铁律不变(仅 HTTP 登录链路可用)。
- 前端:登录后 perms 机制不变;数据权限是后端过滤,前端不做店铺白名单裁剪(防绕过靠后端,前端只影响体验)。

### 2.5 ④ 字段级权限(只评估,不开工)

- 现状:敏感字段脱敏已在 Service 读出口(凭证/密钥掩码,docs/07 §7);利润成本字段可见性等按角色控制场景**当前无真实需求方**。
- 结论建议:不建列级权限引擎;个别字段需求出现时用"Response 视图按角色裁剪"的点状实现(现 profit 成本列即此模式)。评估结论写回 TODO.md 收口。

## 三、实施步骤

1. **③部门**:add-table(sys_dept + sys_user.dept_id)→ 八件套 → 成环/引用校验 → Controller/前端页 → admin 菜单种子 SQL(menu_tool.py 惯例)。
2. **②审计**:add-table(sys_oper_log)→ @OperLog 注解+切面+AFTER_COMMIT 落库 → 首批端点挂注解(逐域核对清单)→ 审计查询页。
3. **①数据权限**:add-table(sys_user_shop)→ CurrentUserApi 扩 currentShopIds + impl → 各域 Query record/ApiImpl 注入(逐域:order/shop/goods/aftersale/sales/finance 优先;inventory/purchase/warehouse 挂 shop 弱的数据域**显式决定不注入**并在注释说明)→ 用户授权管理页(用户表单多选店铺)。
4. **④**:评估结论回写 TODO.md(不开工)。
5. 测试:成环校验/审计脱敏/授权过滤(有授权 vs 无授权 vs admin 三态)单测;真库回归;前后端门禁。
6. devlog:数据权限主轴 A/B + 注入点应用层/拦截器两拍板。

## 四、表结构草案(最终以 add-table skill 落正本)

```sql
CREATE TABLE IF NOT EXISTS sys_dept (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_id BIGINT NOT NULL DEFAULT 0,
  dept_name VARCHAR(64) NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  created_at/updated_at DATETIME, deleted BIGINT DEFAULT 0,
  UNIQUE KEY uk_dept (parent_id, dept_name, deleted)
) COMMENT '部门组织架构(#27③,数据权限/业绩核算铺路)';

CREATE TABLE IF NOT EXISTS sys_user_shop (
  user_id BIGINT NOT NULL,
  shop_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, shop_id)
) COMMENT '用户-店铺数据授权(#27①方案A;不在此表的用户按角色语义:admin 全量,其余待授权)';

ALTER TABLE sys_user ADD COLUMN dept_id BIGINT NULL COMMENT '部门(#27③)';

CREATE TABLE IF NOT EXISTS sys_oper_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NULL, username VARCHAR(64) NULL COMMENT '快照,防用户删除后断链',
  module VARCHAR(32) NOT NULL, action VARCHAR(32) NOT NULL COMMENT '@OperLog 声明',
  biz_type VARCHAR(32) NULL, biz_id BIGINT NULL,
  params_json TEXT NULL COMMENT '截断 2KB+脱敏',
  result_status VARCHAR(8) NOT NULL COMMENT 'OK/FAIL',
  error_msg VARCHAR(500) NULL, ip VARCHAR(64) NULL, trace_id VARCHAR(32) NULL,
  cost_ms INT NULL, created_at DATETIME NOT NULL,
  KEY idx_user_time (user_id, created_at), KEY idx_module (module, created_at)
) COMMENT '操作审计(#27②,人工业务动作)';
```

## 五、验收标准

- 部门:树接口成环防护(自父/跨枝成环被拦);有用户/子部门的部门删除被拦。
- 审计:首批端点动作全落 sys_oper_log(成功/失败两态);params 含凭证字段名时已脱敏;审计异常不影响业务事务(注入故障演练或单测)。
- 数据权限:受限用户(授权 2 店铺)查订单/商品/售后/结算只见授权店铺数据;admin 全量;系统 Job 不受影响;**未注入域有显式注释**。
- 前端:部门管理页/审计页/用户授权表单可用;门禁四件;openapi 快照同步。
- mvn 全量绿;现有登录/鉴权回归(旧 token 24h 内自然过渡,无强制失效)。

## 六、红线提醒

- JWT 结构变更最小化(本期不动 token payload;dept/shop 授权实时查库),避免 token 兼容问题。
- 审计禁敏感明文(params 脱敏黑名单);sys_config 禁入权限相关凭证键。
- 数据权限注入必须落在契约实现层收口(erp-api contract/impl),**禁散落各业务模块自查**;@Lazy 断环惯例。
- Job/系统链路禁调 CurrentUserApi(既有铁律);CurrentUserApi 扩方法同样标注。
- 前端 perms 空放行语义不变(docs/09 §7);v-auth 与后端鉴权双保险,前端裁剪不作为安全边界。

## 七、交接边界

1. 数据权限主轴(推荐店铺轴 A)——最大拍板项,devlog 落笔。
2. 注入点(推荐应用层契约注入;MP 拦截器二期评估)。
3. 审计保留期与清理 Job(拍板点,默认不清理)。
4. 部门轴的应用场景(业绩核算四期)确认不在本期。
5. perm_key 按钮级后端鉴权通道:登记 TODO 新编号,本期不做。
