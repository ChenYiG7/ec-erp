# ec-erp 项目指南

自研电商 ERP(国内 7 平台 + 跨境 7 平台),模块化单体。**信息源优先级:本文件 > README.md > TODO.md > docs/01~07 > 代码注释。**

## 必读

- `TODO.md` — 全部待办的编号清单,代码内 `TODO(编号)` 注释与其一一对应。**改代码前先看对应条目。**
- `docs/sql/01_schema_init.sql` — 建表脚本(权威,与 docs/03 已对齐)
- `docs/04-平台对接层设计.md` — 写 adapter 前必读
- `docs/07-开发规范守则.md` — 写任何代码前必读(**基线:阿里巴巴Java开发手册** + 项目铁律;只写项目裁剪/偏差/铁律,通用规约不复述)
- `docs/09-前端开发规范守则.md` — 写任何前端代码(erp-web/)前必读(Vue 侧裁剪/铁律;契约唯一查询源 = `erp-web/tools/openapi.json`,**前端会话禁读后端 Java 源码**)
- `.claude/skills/` — 流程 skill:write-adapter(平台对接)/ add-table(表结构变更)/ add-domain(模块内新增业务域)/ add-page(erp-web 新增前端页面,生成器优先)/ self-review(提交前自查)/ reading-list(代码精读清单维护:好代码/好设计登记与行号校准,清单 `docs/08-代码精读清单.md`,条目带 `路径:行号` 可 Ctrl+点击跳转);流程步骤在 skill 里,本文档与 docs 不重复
- `erp-codegen/README.md` — **CRUD 八件套生成器**(开发工具,非运行时依赖):从 01_schema_init.sql 生成 Entity/Mapper/Query/SaveRequest/Response/Service/Controller/单测骨架(API 模型收口 docs/07 §1:入参 request/query + request/command(CQRS 读写分包)、出参 response,entity 不出 Service 层;Response/SaveRequest 产 **record+@Builder**,模型可变性分级 docs/07 §1【2026-09-04 #13】,Query 保持 class 继承例外、Entity 保持 @Data MP 例外);新业务域标准流程 = add-table 走表变更 → 跑生成器 → 人工删 Response/Request 不对外字段 + 补业务规则(TODO 编号);另有**状态机守卫测试生成器**(StateMachineTestGenerator,`-Dcodegen.mainClass` 切换):spec 文件驱动产 `XxxStateMachineTest.java`("条件更新即守卫"四类用例,#10/#11/#12 守卫用例四连沉淀,铁律 #8),spec 即拍板表文档化,样板 `erp-aftersale/testgen-aftersale.txt`;跨域动账断言/发足判定/复合事务动作不在射程,留 TODO 槽位人工

## 构建与运行

```bash
mvn -DskipTests compile          # 编译验证(改完必须跑通)
mvn clean install
java -jar erp-api/target/erp-api-0.1.0-SNAPSHOT.jar   # :8088
```

JDK 21 · MySQL 8 · Redis 7。包名 `com.own.erp`。artifact 版本 `0.1.0-SNAPSHOT`。

前端(erp-web/,纯 Node 工程**不进根 pom**,Node ≥22.12 + pnpm 11.8):

```bash
cd erp-web
pnpm dev              # :5173,vite proxy /api -> 8088(禁 rewrite)
pnpm type:check && pnpm lint   # 门禁(无独立 .git,husky 不生效,手动跑)
pnpm api:sync         # 抓 8088 /v3/api-docs -> tools/openapi.json 契约快照(后端须已起)
pnpm gen:page --spec tools/specs/<domain>.txt   # 新页面生成器(存在即跳过)
```

前端门禁四件(type:check/lint/lint:stylelint/build)全绿才算完成;新 CRUD 页走 add-page skill(生成器优先)。

## 技术栈与版本(2026-09-02 定版)

| 依赖 | 版本 | 说明 |
|---|---|---|
| Spring Boot | 4.0.6 (GA) | 根 pom 的 parent,升级只动这一处 |
| Spring AI | 2.0.1 (GA) | `${spring-ai.version}`,2026-09-06 M5→GA 定版 |
| Spring AI Alibaba | 2.0.0-M1.1 | `${spring-ai-alibaba.version}`,graph-core(2.0 线仅此里程碑,1.1.2.3 GA 对齐 Boot3 不可降级) |
| AgentScope Java | 2.0.2 (GA) | `${agentscope.version}`,2026-09-06 RC5→GA 定版;四期才用 |
| MyBatis-Plus | 3.5.17 | boot4-starter |
| Redisson | 4.7.0 | 分布式锁唯一中间件(#13,2026-09-04 定型):核心包手工装配(RedissonConfig,不用 starter),`LockService` 收口 per-key 互斥(拉单防重入;二期 Token 刷新 single-flight 同走),`erp.lock.fail-open` 降级可配;数据正确性不走锁,库存走 DB 原子 UPDATE |
| Hutool | 5.8.47 | hutool-core,根 pom 全模块继承;isBlank/集合判空用 `StrUtil`/`CollUtil` |
| springdoc | 3.1.0 | Boot 4 专用 3.x 线;Swagger UI 预览 + `/v3/api-docs` 供 Apifox;Controller 必标 @Tag/@Operation(中文),生产 yml 可关 |

Spring AI 2.0.1 与 AgentScope 2.0.2 已 GA(2026-09-06 定版);SAA 无 GA 保持 M1.1。回滚方案:Boot 3.5.14 + Spring AI 1.1.5 + SAA 1.1.2.3。

## ⚠️ MyBatis-Plus 3.5.9+ 硬约束

- **`IService`/`ServiceImpl` 已被移除**。本项目统一写法:**Mapper 做通用 CRUD,Service 只装业务逻辑(plain @Service)**,禁止引用 MP 泛型 Service 基类。
- boot4-starter 不默认携带分页,需 `mybatis-plus-extension` + `mybatis-plus-jsqlparser`(根 pom 已配,勿删)。

## 开发铁律

1. **TODO(编号) 约定**:复杂逻辑/AI 代码**只写 `TODO(编号)` 注释 + 实现指引,不实现**,由人工补齐;只有简单 CRUD 直接生成。新 TODO 要登记进 TODO.md。
2. **模块间禁止横向依赖**,跨域调用在 erp-api 编排。依赖方向:erp-shop / erp-fulfill / erp-worker → erp-platform-sdk(调平台 API);erp-order → erp-platform-sdk 仅消费 UnifiedOrder 落库模型(2026-09-04,#4,禁调平台 API);erp-goods / erp-shop / erp-purchase / erp-warehouse / erp-fulfill / erp-ai → erp-contract 跨域契约接口(#5/#10/#7/#11/#6 逐步扩容,实现收口 erp-api);全部业务模块聚合进 erp-api。
3. **adapter 内只做报文翻译**,出现 `if (业务)` 即防腐失败。
4. **库存变更唯一入口** `InventoryService.change()`,同事务写 inventory_flow,禁止旁路 update。
5. **幂等靠唯一键**:订单 `(shop_id, platform_order_id)`、店铺 `(platform, seller_id)`,upsert 落库。
6. **金额一律 DECIMAL(12,4)**;汇率 DECIMAL(12,8)。
7. **安全红线**:平台凭证 AES-GCM 加密(密钥走环境变量 `ERP_TOKEN_KEY` 或本地 git-ignored `local.properties` 同名键,env 优先,禁入提交配置文件,无默认值启动强制校验;凭证写入唯一入口 ShopService,实体凭证字段 @ToString.Exclude);JWT 密钥走环境变量 `ERP_JWT_SECRET` 或 local.properties 同名键(≥32字节);接口返回脱敏;密码 BCrypt;AI 工具只读,写操作需人工确认。
8. **Token 纪律**:同构样板不走 AI 手写——八件套跑 erp-codegen,批量同款修改用脚本/正则;前端同构页面跑 erp-web/tools 生成器(add-page);同一条路径第二次出现时,主动提议沉淀成生成器模板或脚本,由人工拍板。判断归 AI、执行归程序、业务逻辑归人工。

## 核心流程(SKU 映射 = 系统心脏)

```
拉取 listing → shop_product / shop_product_sku(product_id / sku_id 先为 NULL)
    → 自动匹配: seller_sku == product_sku.sku_code → 回填 sku_id, match_status=1
    → 匹配不中: 保持 NULL + match_status=0 进待匹配列表 → 人工绑定 = 2
    → 订单落库时按映射翻译 shop_order_item.sku_id(未绑定则 NULL,订单照常入库)
```

拉单游标:`pull_log` 取该店该类型最近一次成功记录的 `window_end`,左叠 5 分钟重拉,靠唯一键去重。

## 目录速查

| 模块 | 职责 |
|---|---|
| erp-common | 返回体/异常/分页(PageQuery + MP Page) |
| erp-contract | 跨域契约接口(✅ #5/#3,2026-09-04 拍板引入):GoodsReferenceApi(删 SKU/SPU 引用计数)/GoodsSkuApi(sku_id 存在性)/ShopReferenceApi(店铺删除引用计数)/InventoryChangeApi(动库存唯一通道,#10,命令经 erp-api 委托 InventoryService.change,同事务由调用方 @Transactional 保证)/WarehouseApi(仓库存在性 #10 + 删除引用计数 #7)/ShopOrderApi(发货视图 findDeliveryView 未绑定行过滤 + casOrderStatus 订单状态条件推进,#11)/CurrentUserApi(当前登录用户,单据 createdBy 服务端回填,#10/#11 遗留 2026-09-06 收口,SaveRequest 同步剔除 createdBy 入参)/**只读查询契约五件(#6,2026-09-06~07):OrderQueryApi/InventoryQueryApi/GoodsQueryApi/AftersaleQueryApi/SalesQueryApi(销量日统计读侧,order_sales_daily 支付日×SKU 已支付态口径)——过滤 record + 行视图 record + QueryPage(list/total),全 record 不引 MP 类型,分页归一收口在 filter record(page()/size() 默认 1/20 钳 100);erp-ai 工具取数唯一正道,只读无写方法(铁律 7)**,零实现,实现收口 erp-api;域 Service 注入契约接口一律 @Lazy 断构造环(2026-09-05,显式构造器标注,docs/07 §2.2);lombok 仅编译期(provided,契约命令/视图模型 @Builder,#10 偏差已声明 docs/07 §2.2) |
| erp-system | 用户/角色/菜单/字典(JWT 登录 + RBAC 已落地,TODO #1 完成;按 `role_key` 鉴权);站内通知(✅ #14:系统告警扇出只读+本人已读状态) |
| erp-shop | 店铺/授权/凭证加密(✅ #2:AES-256-GCM `CryptoService`,密钥 `ERP_TOKEN_KEY` 环境变量无默认值启动强制校验;写侧加密+掩码回写防护,读侧 `getShopSession` 解密装配、`pageShops`/`getShopById` 脱敏唯一出口——凭证表例外,Controller 不直连 Mapper,docs/07 §2.1)/pull_log/**OAuth 授权中心(✅ #3 脱机部分 2026-09-04:auth-url 签发加密 state 10 分钟 TTL + /oauth/callback 换码入库复用加密链路 + getShopSession 读取时刷新,过期前 10 分钟,DB CAS 守卫防跨实例双刷新,失败走 pull_log 告警)** |
| erp-goods | 商品库 + **SKU 映射**(TODO #5) |
| erp-order / erp-inventory / erp-fulfill / erp-aftersale / erp-purchase / erp-warehouse / erp-finance / erp-ads / erp-report | 订单(#4 落库已落地:saveUnifiedOrder upsert + OrderPullJob 调度,对外只读、详情带明细;连续失败告警已接 #14 站内通知;casOrderStatus 条件推进=#11 "本系统操作"状态出口)/ 库存(change() 唯一入口 + inventory_flow 已落地;**按 flow_type 差异化列语义 ✅ 2026-09-06 #7 收口**:FlowOps 矩阵——IN_TRANSIT 采购审核占在途/关闭释放、IN_PURCHASE 核销在途转可用、LOCK_SHIP 发货建单占用/取消释放、OUT_SHIP 占用转出库,每类型一条原子 UPDATE 守卫下推 WHERE,transfer() 收口跨仓组合;存量行一条原子 UPDATE 防丢更新,首建并发捕 DuplicateKeyException 回退重试,docs/07 §1①)/ 发货单(✅ #11 激活:状态机 PENDING→ship→SHIPPED→DELIVERED 条件更新即守卫;
  **占用模型 2026-09-06 升级:建单即占库存(LOCK_SHIP,取代"建单不动账"旧口径)**——save 占用(缺货建单即拦)/
  ship 同事务 = 状态占位 + OUT_SHIP 核销占用(在库/占用双降,可用不变)+ 回写 shipped_at + 发足判定 casOrderStatus 推进订单(部分发货不推进)/
  cancel·delete 释放占用(delete 先 cas 防 ship 竞态)/ update 行锁读(FOR UPDATE)+释放旧占+重占新占;
  发货明细子表 delivery_order_item = 发货进度事实源(不可存 shop_order_item,拉单先删后插会冲掉),未绑定 SKU 行不参与发货与发足判定;delivery_order 加 warehouse_id/ship_by_time/created_by,**已建库需手工 ALTER(清单存 TODO.md #11)**)/ 售后单(✅ #12 激活:状态机五动作人工处理入口 agree/reject/receive-return/refund/complete,条件更新即守卫、同一 UPDATE 原子回填 result;8 态含新增 RETURN_RECEIVED已收退件,refund 前置白名单即类型血缘(退货类强制已收退件);
  收退件升级复合事务动作(#10/#11 同款):校验链(仓存在+明细非空正数+归属复用 findDeliveryView 仅已绑定行可退+历史累计数量预校验)→ RETURNING→RETURN_RECEIVED 占位回填 warehouse_id → 逐行 IN_RETURN 动账 → aftersale_return_item 实收明细落库(人工录入,可≠平台申明),失败整体回滚;已建库需手工 ALTER(清单存 TODO.md #12);平台售后同步 upsert 已落地(saveUnifiedRefund #12 2026-09-05:契约翻译 order_id+平台状态首插映射+仅平台终态回传条件推进,拉单 Job 接线随 #3 真凭证))/ 采购四域(✅ #10 激活:状态机 DRAFT→审核→(部分)入库→关闭,条件更新即守卫;
  audit 升级复合事务动作(cas + 逐行 IN_TRANSIT 占在途)/ close 升级复合(cas + 释放未到货在途),#7 2026-09-06;
  入库单核销 confirm 同事务 = 状态占位 + InventoryChangeApi 逐行动账(IN_PURCHASE=核销在途,守卫=在途充足) + arrived_qty 原子累加防超收;删除校验三处;
  入库明细子表 purchase_inbound_item,已建库重跑 01_schema_init.sql 即补建)/ 仓储(删除引用校验已接 #7)/财务/营销/BI(待开发) |
| erp-platform-sdk | 防腐层 SPI:`PlatformClient` / `AdapterRegistry`(构造统一套 PlatformGateway 限流装饰)/ `ShopSession` / unified 模型 / **gateway 横切(#3 2026-09-04):PlatformRateGuard = Redisson RRateLimiter 按 platform+shop+bucket 令牌桶,窗口状态持久化 Redis 防重启丢,`erp.rate.*` 可配,Redis 故障 fail-open、配额等待超时抛 429**;**#3 Amazon 接入中**(2026-09-04~06:LWA 授权/刷新 + OAuth 回调/Token 刷新(过期前 10 分钟)+ 限流 + AWS SigV4 签名器/STS 临时凭证 + getOrders 拉单接线(SpApiOrdersClient)已落地,**2026-09-06 联调预备骨架:pullProducts=Reports 全量快照(SpApiReportsClient 三步链+AmazonListingTranslator)/pullRefunds=Finances 记账窗(SpApiFinancesClient+AmazonRefundTranslator,组合幂等键),选型拍板进 docs/04**,翻译 fixture 官方模板推导、真凭证样本到位后校准;uploadTracking 脱机已落地(2026-09-06,MFN confirmShipment + SPI PlatformShipment 签名收口,站点↔币种映射 AmazonMarketplace 23 站同步收口;#11 ship 编排接线随联调);`adapter/amazon` 包,默认不注册 Bean `erp.adapter.amazon.enabled`;剩余真凭证联调 + 限流真值按 x-amzn-RateLimit-Limit 校准);其余 adapter 待实现 |
| erp-ai | AI 能力层(✅ #6 三期开工 2026-09-06:AI 地基 + graph/预警/异常三件):tools/ 只读 @Tool 首批四类(OrderTools/InventoryTools/GoodsTools/AftersaleTools,一类一文件,取数走 erp-contract 查询契约,@Lazy 断环)+ ErpChatService(chat 同步 + chatStream Flux 流式,system prompt 集中 ErpAiProperties,无 AI_API_KEY 调用友好报错启动不炸)+ ErpChatController(/api/ai/chat:会话新建/列表/历史归属校验仅本人可见 + POST sessions/{id}/chat SSE + chat-sync)+ 会话持久化(ai_chat_session 首条消息截断作 title / ai_chat_message USER·AI 双行,同步带 usage 流式置 NULL;TOOL 行经 AuditingToolCallback 装饰器 invoke 前落审计,双通道统一生效)+ ai_suggestion 确认闭环(cas 0→1/0→2 守卫 + adopt/ignore 端点 + AI 产出内部 save 唯一入口,testgen-ai.txt 产守卫四类测试)+ **graph/ 补货建议工作流**(SAA Graph Core 四节点,拍板:程序取数+程序计算、LLM 只写报告;摘要三重降级模板兜底;落 ai_suggestion 不碰业务单据;POST /api/ai/replenishment/run,定时接线待拍板)+ **alert/ 库存预警引擎**(V1 五规则:低库存/发货超时/退款异常/滞销/积压,后两者 2026-09-07 随销量数据面接入;护栏 scanPageSize/scanMaxRows;推送收口 erp-api AlertJob,静默期按 notifyType 查 sys_notification 免去重表,erp.alert.* 可配)+ **graph/ 订单异常检测工作流**(2026-09-07 两段式照 ReplenishWorkflow 母本:scan 四规则先筛——UNPAID_TIMEOUT/BIG_AMOUNT/ZERO_AMOUNT/HIGH_DISCOUNT 收口 AnomalyRule 枚举,金额类 paidTime 判空防 Amazon Pending 0 元单误报,只扫 WAIT_PAY/WAIT_SHIP 两态,单态钳制+失败隔离,无可疑单条件边直达 END 零 LLM 成本;score LLM 批量评分按 orderId 对齐,llmMaxItems=20 超限按基线风险降序截断且未送评不算降级,三重降级规则回落+模板 summary;落 ai_suggestion type=ANOMALY/refType=SHOP_ORDER;POST /api/ai/anomaly/run,定时接线/去重语义待拍板;两工作流补货公式 2026-09-07 重估换真实动销(order_sales_daily,零动销/库存充足剔除不硬补);定时接线 2026-09-07 拍板落地:erp-api ReplenishJob(cron 默认 02:00)/AnomalyJob(cron 默认 02:30 错峰,erp.ai.*.cron/enabled 可配),**去重语义=同键(异常按 refId/补货按 skuId)存在待确认建议即跳过**,收口 scan 段 LLM 评分前,eq 全量+内存交集规避 .in() 坑);余量:agent/(四期 AgentScope 多 Agent)、SSE 帧格式真模型联调、inventory_snapshot_daily 库存快照/周转报表面、更多 tools(ACOS 无数据不开) |
| erp-api | 主应用入口 :8088(配置:GlobalExceptionHandler / MybatisPlusConfig) |
| erp-worker | 拉单 worker :8089(预留,一期跑在 erp-api 进程内) |
| erp-codegen | 开发工具(非运行时依赖):DDL→CRUD 八件套脚手架(#8 起含 Query/SaveRequest/Response,09-03 CQRS 分包),存在即跳过,pom 缺依赖报错守卫;状态机守卫测试生成器(09-04,spec 驱动产守卫四类用例,#10/#11/#12 四连沉淀) |
| erp-web | 前端(#16,2026-09-05 立项):Geeker-Admin v2 底座(Apache-2.0),Vue3.5/Vite8(Rolldown)/TS6/EP2.14/Pinia3/oxlint;登录/动态菜单/通知轮询已接线(P2);**tools 生成器**(api:sync 契约快照 + gen:page 行式 spec 产页面四件+菜单 SQL,存在即跳过);规范 docs/09;纯 Node 工程不进根 pom,日常前端会话在本目录开 |

## 落地记录与收尾纪律(2026-09-04 起)

- **docs/ 分层入库(2026-09-05 定稿)**:`docs/07-开发规范守则.md`、`docs/09-前端开发规范守则.md` 与 `docs/sql/` 随仓库发布(公开面;docs/sql 只放可公开 DDL,07/09 只放可公开规约);其余 docs(01~06 设计文档、08 精读清单、design/、devlog/)商密本地留存,.gitignore 已声明,**严禁 `git add -f` 入库**。

- 历史落地记录(拍板/改动/坑/未尽)在 `docs/devlog/`,按任务归档;做对应任务时才读相关篇,本文件**不再追加日期段落**,只更新上方目录速查表状态。
- 任务收尾(2026-09-05 降频):仅**有拍板/坑级内容的大任务**写 devlog——`scripts/devlog-new.sh <TODO编号> "<标题>"` 生成骨架后补拍板/坑/未尽各 2-3 句;纯 CRUD/同款批量任务不写。
- 会话内跑构建/测试一律 `scripts/mvn-quiet.sh`(全量日志落盘 target/,只回显摘要),不用裸 mvn。
- 分层收口与只读规约(Controller 不直连 Mapper/系统写入表对外只读)以 docs/07 §2.1 为准;省 token:读文件优先子代理与 offset/limit。
