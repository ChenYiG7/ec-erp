# ec-erp 项目指南

自研电商 ERP(国内 7 平台 + 跨境 7 平台),模块化单体。**信息源优先级:本文件 > README.md > TODO.md > docs/01~10 > 代码注释。**

## 必读

- `TODO.md` — 纯待办清单(未完成项按 P0~P3 优先级,与 docs/10 §5 同源)+ 红线与坑(SQL 兼容性/MP 坑);已完成功能全景见 `docs/02-功能模块规划.md` 各分域状态列。代码内 `TODO(编号)` 注释与编号对应(**#1~#23 历史编号不复用不重排**,已完成条目 2026-09-09 起从清单移除,全文在 git 历史),**改代码前先看对应条目**。
- `docs/10-对标差距分析.md` — 对标 wimoor/OmniTrade/qihang/领星/优麦云的功能矩阵、关键差距(G1~G10)、待完成清单与「不做」清单;**规划新功能前先读**。
- `docs/sql/01_schema_init.sql` — 建表脚本(权威,与 docs/03 已对齐)
- `docs/04-平台对接层设计.md` — 写 adapter 前必读
- `docs/07-开发规范守则.md` — 写任何代码前必读(**基线:阿里巴巴Java开发手册** + 项目铁律;只写项目裁剪/偏差/铁律,通用规约不复述)
- `docs/09-前端开发规范守则.md` — 写任何前端代码(erp-web/)前必读(Vue 侧裁剪/铁律;契约唯一查询源 = `erp-web/tools/openapi.json`,**前端会话禁读后端 Java 源码**)
- `docs/08-代码精读清单.md` — 值得逐行精读的代码索引(带 `路径:行号` 可 Ctrl+Shift+N 跳转),理解复杂设计(库存动账/成本账/工作流母本/事件编排)的入口
- `.claude/skills/` — 流程 skill:write-adapter(平台对接)/ add-table(表结构变更)/ add-domain(模块内新增业务域)/ add-page(erp-web 新增前端页面,生成器优先)/ self-review(提交前自查)/ reading-list(精读清单维护);流程步骤在 skill 里,本文档与 docs 不重复
- `erp-codegen/README.md` — **CRUD 八件套生成器**(开发工具,非运行时):从 01_schema_init.sql 生成 Entity/Mapper/Query/SaveRequest/Response/Service/Controller/单测骨架(API 模型收口 docs/07 §1:入参 request/query + request/command(CQRS 读写分包)、出参 response,entity 不出 Service 层;Response/SaveRequest 产 **record+@Builder**,Query 保持 class 继承例外、Entity 保持 @Data MP 例外);另有**状态机守卫测试生成器**(StateMachineTestGenerator,`-Dcodegen.mainClass` 切换):spec 文件驱动产 `XxxStateMachineTest.java`("条件更新即守卫"四类用例,spec 即拍板表文档化,样板 `erp-aftersale/testgen-aftersale.txt`);跨域动账断言/发足判定/复合事务动作不在射程,留 TODO 槽位人工

## 构建与运行

```bash
mvn -DskipTests compile          # 编译验证(改完必须跑通)
mvn clean install
java -jar erp-api/target/erp-api-0.1.0-SNAPSHOT.jar   # :8088
```

JDK 21 · MySQL 9.7.2 LTS(mapper XML 自定义 SQL 用 9.7.2 原生形态——ODKU 行别名 `AS new`/窗口函数,INSERT...SELECT 用源表/派生表别名——并**必须真库验证**,详见 TODO 第三节"SQL 兼容性红线")· Redis 7。包名 `com.own.erp`。artifact 版本 `0.1.0-SNAPSHOT`。会话内跑构建/测试一律 `scripts/mvn-quiet.sh`(全量日志落盘 target/,只回显摘要),不用裸 mvn。

前端(erp-web/,纯 Node 工程**不进根 pom**,Node ≥22.12 + pnpm 11.8):

```bash
cd erp-web
pnpm dev              # :5173,vite proxy /api -> 8088(禁 rewrite);hash 路由(外链必须 /#/路径 形态)
pnpm type:check && pnpm lint   # 门禁(无独立 .git,husky 不生效,手动跑)
pnpm api:sync         # 抓 8088 /v3/api-docs -> tools/openapi.json 契约快照(后端须已起)
pnpm gen:page --spec tools/specs/<domain>.txt   # 新页面生成器(存在即跳过)
```

前端门禁四件(type:check/lint/lint:stylelint/build)全绿才算完成;新 CRUD 页走 add-page skill(生成器优先)。

## 技术栈与版本(2026-09-06 定版)

| 依赖 | 版本 | 说明 |
|---|---|---|
| Spring Boot | 4.0.6 (GA) | 根 pom 的 parent,升级只动这一处;关注 Boot 4.1 GA(预计 2026-11) |
| Spring AI | 2.0.1 (GA) | `${spring-ai.version}`;victools 须钉 5.0.0(JsonSchemaGenerator 依赖,根 pom 注释) |
| Spring AI Alibaba | 2.0.0-M1.1 | `${spring-ai-alibaba.version}`,graph-core(2.0 线仅此里程碑,GA 后升 BOM 即可) |
| AgentScope Java | 2.0.2 (GA) | `${agentscope.version}`;agent 双角色已投产 |
| MyBatis-Plus | 3.5.17 | boot4-starter |
| Redisson | 4.7.0 | 分布式锁唯一中间件:核心包手工装配(RedissonConfig,不用 starter),`LockService` 收口 per-key 互斥,`erp.lock.fail-open` 降级可配;数据正确性不走锁,库存走 DB 原子 UPDATE |
| Hutool | 5.8.47 | hutool-core,根 pom 全模块继承;isBlank/集合判空用 `StrUtil`/`CollUtil` |
| springdoc | 3.1.0 | Boot 4 专用 3.x 线;Controller 必标 @Tag/@Operation(中文) |
| Apache POI | 5.4.0 | 仅 erp-report 依赖,版本模块内自管不进根 pom(Excel 导出) |

回滚方案:Boot 3.5.14 + Spring AI 1.1.5 + SAA 1.1.2.3。

## ⚠️ MyBatis-Plus 3.5.9+ 硬约束

- **`IService`/`ServiceImpl` 已被移除**。本项目统一写法:**Mapper 做通用 CRUD,Service 只装业务逻辑(plain @Service)**,禁止引用 MP 泛型 Service 基类。
- boot4-starter 不默认携带分页,需 `mybatis-plus-extension` + `mybatis-plus-jsqlparser`(根 pom 已配,勿删)。
- LambdaQueryWrapper `.in()`/`.set()` 急切解析列元数据,纯单测环境炸(docs/07 §10 有规避口径)。

## 开发铁律

1. **TODO(编号) 约定**:复杂逻辑/AI 代码**只写 `TODO(编号)` 注释 + 实现指引,不实现**,由人工补齐;只有简单 CRUD 直接生成。新 TODO 用新编号登记进 TODO.md(历史编号不复用)。
2. **模块间禁止横向依赖**,跨域调用走 erp-contract 契约(实现收口 erp-api)。依赖方向:erp-shop / erp-fulfill / erp-worker → erp-platform-sdk(调平台 API);erp-order / erp-aftersale → erp-platform-sdk 仅消费 Unified* 落库模型(禁调平台 API);业务模块 → erp-contract;全部业务模块聚合进 erp-api。
3. **adapter 内只做报文翻译**,出现 `if (业务)` 即防腐失败。
4. **库存变更唯一入口** `InventoryService.change()`,同事务写 inventory_flow(且先经 InventoryCostService.apply 推进成本账),禁止旁路 update。
5. **幂等靠唯一键**:订单 `(shop_id, platform_order_id)`、店铺 `(platform, seller_id)`,upsert 落库。
6. **金额一律 DECIMAL(12,4)**;汇率 DECIMAL(12,8)。
7. **安全红线**:平台凭证 AES-GCM 加密(密钥 `ERP_TOKEN_KEY` 环境变量/local.properties,禁入提交配置文件,无默认值启动强制;凭证写入唯一入口 ShopService,实体凭证字段 @ToString.Exclude);JWT 密钥 `ERP_JWT_SECRET`(≥32字节);接口返回脱敏;密码 BCrypt;**AI 工具只读,写操作必须人工确认,AI 产出一律落 ai_suggestion**;sys_config 禁入凭证类键(SMTP 授权码为唯一范围例外,docs/07 §7)。
8. **Token 纪律**:同构样板不走 AI 手写——八件套跑 erp-codegen,批量同款修改用脚本/正则;前端同构页面跑 erp-web/tools 生成器(add-page);同一条路径第二次出现时,主动提议沉淀成生成器模板或脚本,由人工拍板。**判断归 AI、执行归程序、业务逻辑归人工。**

## 核心流程(SKU 映射 = 系统心脏)

```
拉取 listing → shop_product / shop_product_sku(product_id / sku_id 先为 NULL)
    → 自动匹配: seller_sku == product_sku.sku_code → 回填 sku_id, match_status=1
    → 匹配不中: 保持 NULL + match_status=0 进待匹配列表 → 人工绑定 = 2
    → 订单落库时按映射翻译 shop_order_item.sku_id(未绑定则 NULL,订单照常入库)
```

拉单游标:`pull_log` 取该店该类型最近一次成功记录的 `window_end`,左叠 5 分钟重拉,靠唯一键去重。

## 目录速查

| 模块 | 职责与现状 |
|---|---|
| erp-common | 返回体/异常/分页(PageQuery + MP Page)/领域事件(DeliveryShippedEvent、SystemConfigChangedEvent 等,发布方与监听方互不依赖) |
| erp-contract | 跨域契约(零实现,收口 erp-api):动账/存在性/引用计数命令(InventoryChangeApi/GoodsSkuApi/WarehouseApi/CurrentUserApi/ShopOrderApi findDeliveryView+casOrderStatus)+ **只读查询契约九件**(Order/Inventory/Goods/Aftersale/Sales/Shop/Purchase/Delivery/InventorySnapshot)+ ProfitQueryApi + SystemConfigApi;过滤 record + 行视图 record + QueryPage,全 record 不引 MP 类型;**凭证字段不进契约**(ShopView 不收 appKey/accessToken);域 Service 注入契约接口一律 @Lazy 断构造环;lombok 仅编译期 |
| erp-system | 用户/角色/菜单/字典(JWT + RBAC,按 role_key 鉴权)/站内通知(#14 三渠道:站内 pushAllUsers 出口 + Webhook(钉钉/飞书/企微)与邮箱两监听器 AFTER_COMMIT 挂事件)/sys_config 配置热更(#18:词表白名单、SECRET 掩码回显、事件失效缓存) |
| erp-shop | 店铺/授权/凭证加密(AES-256-GCM,脱敏唯一出口)/pull_log/OAuth 授权中心(state 加密签发 10 分钟 TTL、DB CAS 防双刷新、过期前 10 分钟刷新)/店铺删除引用校验 |
| erp-goods | 商品库(SPU/SKU/分类/品牌 + 分类成环校验)+ **SKU 映射**(#5:自动匹配 + 人工绑定,绑定列永不被同步覆盖)/SKU 批量翻译端点(options) |
| erp-order | 统一订单(saveUnifiedOrder upsert 幂等 + casOrderStatus 条件推进,对外只读)/order_sales_daily 销量日表(SalesSnapshotJob 01:00 重算 30 天窗,支付日×SKU 已支付态口径) |
| erp-inventory | 多仓四量 + 流水(**change() 唯一入口**:一条原子 UPDATE 守卫下推 WHERE,首建捕 DuplicateKeyException 重试;**FlowOps 矩阵**按 flow_type 差异化列语义:IN_TRANSIT 采购占在途/LOCK_SHIP 发货占用/IN_PURCHASE 核销/OUT_SHIP 占用转出库)/transfer() 跨仓组合/**InventoryCostService 移动加权成本账**(随流水同事务,#19③,CostOps 策略分派,FOR UPDATE 锁 state 行)/inventory_snapshot_daily 日快照(InventorySnapshotJob 01:30,只增不可回溯) |
| erp-purchase | 采购四域(#10:状态机 DRAFT→AUDITED→(部分)入库→CLOSED;audit/close 复合事务占/释在途;入库核销 confirm 三步同事务,arrived_qty 原子累加防超收)/供应商(uk_name 唯一) |
| erp-warehouse | 仓库档案/出入库/删除引用校验(库存 + 采购两域合计) |
| erp-fulfill | 发货单(#11:状态机 + **建单即占库存**(LOCK_SHIP,缺货建单即拦)/ship 同事务核销 + 发足判定推进订单(部分发货不推进)/cancel·delete 释放/update 行锁读 + 释放重占;delivery_order_item 为进度事实源,未绑定行不参与;ship 事务内发布 DeliveryShippedEvent) |
| erp-aftersale | 售后单(#12:8 态五动作状态机,类型血缘前置白名单;收退件 receiveReturn 复合事务(校验链→占位→IN_RETURN 动账→实收明细);saveUnifiedRefund 平台同步(仅平台终态条件推进)) |
| erp-finance | 财务(#19:settlement 域结算报告解析 V2 报表(勾稽不平整单 FAILED)+ profit 域 SKU 级利润(移动加权,归集键:成本=OUT_SHIP 流水/佣金=settlement_detail 按 platform_order_item_id;缺口纪律三计数不静默归零)+ 汇率回溯 resolveRate + RefundReconciliationService 退款勾稽三类差异)/erp-api RefundReconciliationJob 每日一扫 |
| erp-ads | 营销中心(休眠:卡各平台广告 API 真凭证;ad_report_daily 草案随 #20 激活) |
| erp-report | 报表 BI(#20~#23:销售日报/周报/SKU 明细/库存快照 + Excel 导出(POI)/商品分析单品下钻(日期轴不进 SQL)/利润看板(SVG 零依赖)/经营简报(ReportDigestJob 三周期定时,走 #14 三渠道);join 不滤已删) |
| erp-platform-sdk | 防腐层 SPI:PlatformClient/AdapterRegistry(构造统一套 PlatformGateway 限流装饰)/ShopSession/unified 模型/gateway 横切(PlatformRateGuard 三维令牌桶,Redis 持久化防重启丢,fail-open/closed 双模式)/`adapter/amazon` 包(LWA + SigV4 + STS + Orders/Reports/Finances + confirmShipment + AmazonMarketplace 23 站币种映射,翻译 fixture 官方模板推导;默认不注册 Bean `erp.adapter.amazon.enabled`);其余 adapter 待实现 |
| erp-ai | AI 能力层:tools/ 只读 @Tool 七类(一类一文件,取数走 erp-contract,余量 Report)+ ErpChatService(chat 同步/SSE 流式)+ 会话持久化(ai_chat_session/message + source 列隔离 CHAT/AGENT;AuditingToolCallback 工具审计行)+ ai_suggestion 确认闭环(cas 守卫 + adopt/ignore)+ graph/ 五工作流(AnomalyWorkflow 两段式母本 → Replenish(V2 (s,S) 策略)/Purchase/Copywriting/Selection 变体;共享组件 LowStockScanner/ReplenishCalculator)+ alert/ 预警引擎(五规则,出口 AlertJob 收口)+ agent/(AgentScope ReActAgent 双角色 SUPPORT/OPS,SpringAiAgentToolBridge 桥接零复制,历史重放截断)+ kb/ RAG(SimpleVectorStore,向量不入库正本可重建,检索注入 chat 双通道) |
| erp-api | 主应用入口 :8088(配置:GlobalExceptionHandler/MybatisPlusConfig/SecurityConfig;跨域编排:contract/impl 契约实现 + job 调度(OrderPullJob/ProductPullJob/AlertJob/ReplenishJob/AnomalyJob/SalesSnapshotJob/InventorySnapshotJob/RefundReconciliationJob/ReportDigestJob,均 MDC traceId + LockService 抢锁)+ shipment/ShipmentSyncService 发货回传编排(AFTER_COMMIT 事件驱动,失败记 pull_log 不回滚本地)) |
| erp-worker | 拉单 worker :8089(预留,一期跑在 erp-api 进程内;吞吐不足再拆,拆分预案见 docs/01 演进触发点) |
| erp-codegen | 开发工具(非运行时):DDL→CRUD 八件套 + 状态机守卫测试生成器,存在即跳过 |
| erp-web | 前端(#16:Geeker-Admin v2 底座,Vue3.5/Vite8(Rolldown)/TS6/EP2.14/Pinia3/oxlint;登录/动态菜单/通知轮询;tools 生成器(api:sync + gen:page + chat 模板 + take-screenshots);25+ 业务页面已铺开;规范 docs/09;纯 Node 工程不进根 pom,日常前端会话在本目录开) |

## 落地记录与收尾纪律

- **docs/ 分层入库**:**docs/ 全部随仓库发布**(01~10 设计文档/规范/精读清单/对标分析/sql/devlog/images),.gitignore 不忽略 docs。
- 历史落地记录(拍板/改动/坑/未尽)在 `docs/devlog/`(根 = 近期日志,archive/ = 31 篇归档);做对应任务时才读相关篇,本文件**不追加日期段落**,只更新上方目录速查表状态。
- 任务收尾:仅**有拍板/坑级内容的大任务**写 devlog——`scripts/devlog-new.sh <TODO编号> "<标题>"` 生成骨架后补拍板/坑/未尽各 2-3 句;纯 CRUD/同款批量任务不写。
- 分层收口与只读规约(Controller 不直连 Mapper/系统写入表对外只读)以 docs/07 §2.1 为准;省 token:读文件优先子代理与 offset/limit。
- 对标纪律(docs/10 §6):qihang AGPL-3.0 **禁读源码**;wimoor/OmniTrade 借鉴思路,参考表结构须 devlog 记出处;领星/优麦云闭源 SaaS 仅功能形态对标。
