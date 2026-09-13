# TODO(#27) 权限增强:数据权限(店铺轴)落地收口

- 日期: 2026-09-12
- 关联: docs/plans/27-rbac-enhance.md(计划书);TODO.md #27 条目;13a 部门/13b 审计已先行

## 拍板

- **数据权限主轴 = 店铺轴(计划书方案A,2026-09-10 预拍板确认)**:ERP 数据的自然边界是店铺(订单/listing/售后/利润全挂 shop_id),`sys_user_shop` 联合主键授权表;部门轴(方案B)在库存/采购/仓库域无归属列,只作组织架构数据面不作数据隔离。授权语义**收紧为三态**:admin 返 `null`=不限(实现按角色短路,不查授权表)/非空列表=IN 过滤/**空列表=不可见任何店铺数据**——计划书原文「admin 返空=不限」在"非 admin 未授权"场景会退化成全量放行的越权空洞,null 哨兵是安全必需,消费方(域 Service/契约实现)对三态全判,禁把 null 当空集或反之。
- **注入点偏差(计划书前提与代码不符,已修订计划书同 commit)**:计划书假设「查询面集中收口在契约实现 18 个 Impl」,代码事实是契约 QueryApi 的调用方只有 AI 工具(+利润页恰好经契约),**其余前端查询直连各业务模块 Controller**。落点改为两层:①契约实现层强制覆盖装配(OrderQueryApiImpl/ShopQueryApiImpl/AftersaleQueryApiImpl/DeliveryQueryApiImpl/ProfitQueryApiImpl——ProfitController 注入契约接口,一处覆盖前端利润页与 AI 工具双路径);②前端路径各域查询 Controller 装配(GET 绑定后 `query.setShopIds(currentUserApi.currentShopIds())` 覆盖,不信任前端自带值)。授权集的**提供**收口 CurrentUserApiImpl(erp-api),各域零自建鉴权逻辑、零 sys_user_shop 直查——「禁散落各业务模块自查」的本意(鉴权判断不散落)保持;MP `DataPermissionInterceptor` 按计划禁用,登记二期评估。
- **注入范围**:计划书六优先域落地(order/shop/shop_product/aftersale/finance 利润四端点),**补注 DeliveryQueryApi**(发货单挂订单店铺轴,漏注即明洞);未注入域全部显式注释(erp-goods 商品库无店铺轴/Inventory 系与 Purchase 挂仓库轴/Sales·Report 全店聚合无店铺列——order_sales_daily 加店铺列随需求另立,不动现有日表口径)。
- **④ 字段级权限评估结论(不开工)**:不建列级权限引擎——敏感字段现状已两层防护(Response 不建字段物理隔离 + Service 读出口语义脱敏,docs/07 §2.1/§7),「利润成本列按角色可见」无真实需求方;个别需求出现时用 Response 视图按角色裁剪的点状实现(profit 成本列即此模式)。复评触发点:多商户激活/外部客户登录/越权查看审计案例,任一出现再立项。

## 改动

- **DDL**:sys_user_shop(user_id,shop_id 联合主键,+created_at/updated_at 同 sys_user_role 惯例,无逻辑删除列不入 LogicalDeleteAnnotationTest 口径)+ sys_menu 按钮 205(system:user:shops)+ sys_role_menu (1,205);01_schema_init.sql 幂等重放,docs/03 同步。
- **erp-system**:SysUserShopMapper(注解 SQL,同 SysUserRoleMapper 先例:selectShopIdsByUserId/deleteByUserId/insert)+ SysUserShopService(先删后插全量重绑 @Transactional,入参 distinct)+ GET/PUT `/api/system/users/{id}/shops`(类级 admin 双闸继承)+ SysUserService.deleteUser 同事务清授权防孤儿。
- **契约**:CurrentUserApi 扩 `currentShopIds()`(javadoc 写死三态语义与「仅 HTTP 链路」铁律);OrderFilter/ShopFilter/AftersaleFilter/DeliveryFilter/OrderProfitQuery 加 `shopIds` 组件(契约演进只加不改)。
- **域内过滤**:ShopOrderQuery/ShopQuery(店铺域按 Shop::getId IN)/ShopProductQuery/AftersaleOrderQuery/DeliveryOrderQuery + 各 Service 空集短路零结果(空 IN 禁落 SQL);ProfitQueryService 四方法 shopScopeEmpty 短路 + XML `profitLines` 片段加 shopIds IN foreach 条件(空集调用方短路,`size() > 0` 双保险)。
- **前端**:UserShopDialog.vue(镜像 UserRoleDialog:并行拉已授权集+店铺全量,el-alert 写明 admin 例外与清空语义)+ 用户页「店铺授权」按钮(v-auth='system:user:shops',操作列加宽)+ user api getShops/putShops + SysUserShopsRequest 类型;openapi.json 手改同步(新路径+UserShopAssignRequest+五个域 Query 的 shopIds,oxfmt 归一,最小 diff)。
- **验证**:全仓 mvn test 绿——CurrentUserApiImplTest 5 例(真实 SecurityContextHolder 三态)/SysUserShopServiceTest 4 例(重绑顺序/去重/null 只删)/ProfitQueryApiImplTest 3 例(强制覆盖/全方法携带/admin null)/ProfitQueryServiceTest 扩 2 例(空集短路不触库/null 不过滤)/ShopOrderServiceTest 扩 1 例(空集短路)/四个既有 QueryApiImplTest 适配新构造;前端门禁四件绿。

## 坑

- **计划书现状断言必须实查**:「18 个 Impl 即查询面收口」想当然会做出一个"AI 工具受控、Web 界面全裸"的摆设功能——执行前 grep Controller 注入形态(本例发现 ProfitController 经契约、其余直连域 Service)才定得出真实注入面;此类"收口点"断言与代码冲突时按执行协议修订计划书而非硬套。
- **「admin 返空=不限」与「空授权=待授权」语义冲突**:两处都用"空列表"表达会合并成越权通道;null 哨兵 + 消费方三态全判的约定写进契约 javadoc,后续扩轴(按人/按部门)照抄此形。
- MP `LambdaQueryWrapper.in(condition, col, list)` 生产可用(急切解析坑只炸纯单测环境,docs/07 §10),单测覆盖走空集短路分支 + ArgumentCaptor 断言参数传递,不直测 in 分支——与 #5/#11/#8 三度规避口径一致。

## 未尽

- **真库验证未做**(会话环境无 MySQL):存量库重跑 01_schema_init.sql 的 sys_user_shop CREATE 段 + sys_menu 205/sys_role_menu (1,205) 段;OrderProfitQuery XML 的 shopIds IN 条件随下次真库回归验证(validate 脚本已归档,届时按 validate 家族惯例复活再跑)。
- **详情按 ID 直取与写动作端点不在本期注入面**(计划书 Query record 过滤口径):受限用户可按 ID 直取详情(getOrderDetail/getShop 等),多用户真实使用后按域补"授权集包含校验";写动作(审核/发货等)本身有 RBAC+状态机守卫,未叠加店铺轴。
- api:sync 重抓契约快照(后端未起,同 #19/#29/#30 手改口径);审计保留期/清理 Job 拍板挂起(13b 遗留,默认全量保留)。
