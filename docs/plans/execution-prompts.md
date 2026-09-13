# 执行提示词集 —— 配合 docs/plans/ 计划书逐项投喂

> 2026-09-10 生成。**2026-09-12 起大部分条目为存档**:06-report-tools/25-oss-storage/28-deploy-docker/
> order-review-split/warehouse-ops/payment-receipt/first-mile-freight/sse-notify/27-rbac-enhance 九项已实施
> (计划书见 `archive/` 或本目录头部实施标注,devlog 见 docs/devlog/),对应提示词仅存档不再投喂。
> **2026-09-13 优先级重整(国内平台先行)**:24-adapter-playbook 升为 **P0 抖店 adapter 实施手册**(见第 12 条);
> p3-ai-deepening/p3-goods-ai-extensions 已删除(计划书文件已删,git 历史可溯,预拍板摘要存于本文件 P3-4/P3-7)、
> p3-chat-ux 主体已落地、余量已删除(计划书已归档)——三者不再投喂(提示词仅存档);
> p3-ai-workflow-scheduling 已于 2026-09-12 实施(计划书已归档,提示词仅存档)。
> 仍可投喂的 = fba-shipment、26-frontend-audit 与 P3 保留三项(P3-1 同买家规则/P3-5 代发海外仓/P3-6 超退上限,按触发条件)。
> 用法:每开工一项,**清空上下文(/clear 或新会话)后整段复制对应提示词**投喂执行模型。
> 各计划书的「拍板点」已由用户于 2026-09-10 预拍板,固化在各条「预拍板」中——执行模型**按拍板执行,不再停下来询问**;
> 仅一处例外需要用户补充业务输入(p3-fulfill 的代发四问),已在对应条目留填写槽。
> 执行模型遇「计划书/提示词与代码现状冲突」:停下报告差异,不得自行改设计。
> 预拍板若与后续人工意志冲突:直接改本文件对应条目(本文件即拍板事实源)。

## 一、执行协议(上下文管理,先读)

1. **一个功能 = 一个全新会话**。这是唯一有效的防堆积手段:每条提示词都自包含,清空上下文后投喂;**禁止一个会话连做多项**。
2. 完成后执行模型只输出交付摘要即停(每条提示词尾部已写死停止条件),用户 /clear 后贴下一条。
3. 单功能确实过长:优先按计划书「实施步骤」的阶段边界拆两次会话;会话内实在超限用 /compact 续作。
4. **没有「完成后自动 /clear」机制**(Claude Code 的 hook 触发不了清屏),一功能一会话 + 手动 /clear 就是最优解。
   要全自动可改 headless:`claude -p "<整段提示词>"`——每次调用天然全新上下文;代价是门禁失败/现状冲突时无交互修复,
   只建议用于纯机械项(如 06-report-tools)。
5. 会话目录:后端项在**仓库根**;前端项(#26)在 **erp-web/**;跨栈项(sse-notify)在仓库根、动 erp-web 前先读 docs/09。
6. 除提示词「必读」清单外**不通读其他 docs**——计划书自包含,多读只烧 token。

## 二、通用模板(各条已实例化,一般不必手拼)

```text
实现 docs/plans/<计划书文件>(<TODO 编号> <名称>)。
必读:根 CLAUDE.md(铁律)→ 计划书全文 → <按项指定的规范章节>。
会话目录:<仓库根 / erp-web/>。只做本计划书范围;与代码现状冲突即停并报告。
预拍板(2026-09-10,不再询问):
- <逐条>
完成定义:计划书「验收标准」全过 + <门禁口径>;TODO.md 对应条目收口;
计划书头部追加「已于 YYYY-MM-DD 实施」行。不做 git commit(用户手动提交)。
本会话只做这一项,输出交付摘要即停。
```

## 三、P2 逐项提示词(编号≠开工顺序,开工顺序以 README「建议执行顺序」定版为准)

### 1) #25 OSS 对象存储(仓库根开会话)

```text
实现 docs/plans/archive/25-oss-storage.md(#25 OSS 对象存储 RustFS)。
必读:根 CLAUDE.md(铁律)→ 计划书全文 → docs/07 §7(安全红线:sys_config 凭证边界)。
会话目录:仓库根。只做本计划书范围;与代码现状冲突即停并报告。
预拍板(2026-09-10,不再询问):
- 导出流程选方案 A:保留现有同步流式导出体验不变,「导出归档到 OSS」做成开关且默认关;URL 化改造(差距 G10)本期不做。
- SecretKey 走 ValueType.SECRET 掩码 = SMTP 授权码例外口径的扩容:实现时写 devlog 拍板记录,不再另行询问。
- RAG 原文留存:ai_kb_document 加 original_file_key / original_file_size 两列;上传格式白名单暂不扩(仍 txt/md/markdown;PDF/DOCX 解析另立)。
- docker-compose 编排归 #28;本项本地验证用 docker run 起单节点 RustFS 即可。
- 交付物 = OssService(erp-common,S3 协议,AWS SDK v2 类型不外漏)+ sys_config GROUP_OSS + 契约/调用方接入。
完成定义:计划书「验收标准」全过(mvn 编译+单测绿,真 RustFS 端点冒烟);TODO.md #25 条目收口;计划书头部追加「已于 YYYY-MM-DD 实施」行。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

### 2) #28 一键部署 + Docker(仓库根)

```text
实现 docs/plans/archive/28-deploy-docker.md(#28 一键本地部署 + Docker)。
必读:根 CLAUDE.md → 计划书全文 → 根 README.md 手工启动步骤段(脚本化蓝本)。
会话目录:仓库根。
预拍板(2026-09-10,不再询问):
- 前端走独立 Nginx 镜像(不嵌 erp-api):hash 路由无需 history rewrite,Nginx 只做静态托管 + /api 反代 erp-api:8088(禁 rewrite)。
- 前端镜像多阶段构建(node 22 + pnpm build 产物 → nginx 托管),构建不依赖宿主机 Node。
- .env 进 .gitignore、.env.example 提交;所有容器 TZ=Asia/Shanghai。
- MySQL 镜像 tag 实拉验证:存在 9.7.2 官方 tag 则用之,否则取官方最新 9.x LTS,devlog 记录实测 tag。
- RustFS 并入 compose(可选 profile 默认不启),吸收 #25 编排需求。
- scripts/dev-up.sh 环境检查前置 fail-fast:ERP_JWT_SECRET / ERP_TOKEN_KEY 缺失即友好报错退出,不许漏到 Spring 堆栈。
完成定义:干净环境下 docker compose up -d 与 dev-up.sh 各冒烟一次(登录页可开、/api 登录成功);TODO.md #28 条目收口;计划书头部追加实施行。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

### 3) #19 周期利润口径(仓库根)

```text
实现 docs/plans/19-profit-caliber.md(#19 周期利润口径)。
必读:根 CLAUDE.md → 计划书全文 → docs/07 §6(MySQL 红线)/§4(事务)。
会话目录:仓库根。
预拍板(2026-09-10,不再询问):
- 周期口径 = 结算报告期间(不另造自然周期)。
- 预估费用仅 COMMISSION 一项,带 ESTIMATED 标志;禁费率猜算,无费率数据按缺口纪律计数不静默归零。
- 结算口径与订单口径差值容差 0.01。
- SettlementPullJob 只交付骨架,开关默认关(等 #3 真凭证)。
- platform_fee_rate / profit_period_report 按计划书表结构草案走 add-table 流程落 01_schema_init.sql。
- 多币种折算复用 ExchangeRateService.resolveRate,无报价不猜。
完成定义:计划书「验收标准」全过(含自定义 SQL 真库验证,validate 脚本按计划书扩);TODO.md #19 条目收口;计划书头部追加实施行。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

### 4) 头程运费分摊(仓库根)

```text
实现 docs/plans/first-mile-freight.md(头程运费分摊,利润口径第三层)。
必读:根 CLAUDE.md → 计划书全文 → docs/07 §4/§6。
会话目录:仓库根。
预拍板(2026-09-10,不再询问):
- 路线选 B(期间费用行):头程运费不资本化进 sku_cost_state 移动加权账,独立费用行进利润归集。
- 分摊策略 QTY/WEIGHT/AMOUNT 三实现,默认 WEIGHT(头程按重计费行业常态),配置可切。
- 分摊校验 Σalloc == 运费 ±0.01,尾差进最后一行。
- product_sku 补 weight_g / volume 两列(与 fba-shipment 计划共用,只加一次)。
- 模块归 erp-finance。
完成定义:计划书「验收标准」全过(含分摊平衡断言单测、真库验证);TODO.md 头程条目收口;计划书头部追加实施行。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

### 5) 订单域补课(仓库根)

```text
实现 docs/plans/order-review-split.md(订单审核/拆单/内销录入)。
必读:根 CLAUDE.md → 计划书全文 → docs/07 §2.1(分层收口)/§4(事务)。
会话目录:仓库根。
预拍板(2026-09-10,不再询问):
- 审核列挂 shop_order;saveUnifiedOrder upsert 显式排除审核列(平台同步永不覆盖人工审核结果)。
- 审核闸口设在 fulfill 建单(经 findDeliveryView 契约扩容),不在订单域拦。
- 内销单 order_source=MANUAL,合成单号 MAN-{shopId}-{yyyyMMdd}-{seq} 占既有 uk,不走「系统写入表人工写」旁路。
- 风控规则 = 地址不完整 + buyer_note 关键词白名单,词表入 sys_config GROUP_ORDER_REVIEW(走 #18 热更口径)。
- 合单不做(留观察),拆单按计划书。
完成定义:计划书「验收标准」全过;TODO.md 订单域条目收口;计划书头部追加实施行。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

### 6) 仓内作业(仓库根)

```text
实现 docs/plans/warehouse-ops.md(盘点单/调拨单/库位批次评估)。
必读:根 CLAUDE.md → 计划书全文 → docs/08 库存动账精读段(改动账相关前)。
会话目录:仓库根。
预拍板(2026-09-10,不再询问):
- 盘点单 = 建单时快照 + 确认时点账面 re-diff 双口径(防盘点期间动账丢失)。
- 盘点差异调整走 ADJUST flow_type,经 InventoryService.change() 唯一入口;biz_type 新增 STOCKTAKE,词表三方同步(InventoryConsts / FlowOps / DDL 注释)。
- 调拨单 CONFIRM 即达:直接调 transfer() 原语,biz_type 从 INVENTORY_TRANSFER 收口为 TRANSFER_ORDER(注释同步)。
- 库位/批次不做,评估结论写回 TODO.md(触发点按计划书)。
完成定义:计划书「验收标准」全过(动账必有 inventory_flow、状态机守卫测试按生成器);TODO.md 仓内条目收口;计划书头部追加实施行。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

### 7) 收付款/回款(仓库根)

```text
实现 docs/plans/payment-receipt.md(采购付款登记/平台回款/资金流追踪)。
必读:根 CLAUDE.md → 计划书全文 → docs/07 §4/§6。
会话目录:仓库根。
预拍板(2026-09-10,不再询问):
- 模块归 erp-finance。
- 采购「已付」口径 = Σalloc 查询时聚合,不落冗余已付列。
- 一票多付用 payment_alloc;幂等靠 payment_record.uk_ref。
- 结算报告 PARSED 且 transfer_amount>0 时,同事务自动派生 SETTLEMENT_RECEIPT 回款记录。
- 供应商补 settle_days 一列。
- 采购单存在性校验走 erp-contract 契约,不横跨模块。
完成定义:计划书「验收标准」全过;TODO.md 收付款条目收口;计划书头部追加实施行。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

### 8) FBA Shipment(仓库根)

```text
实现 docs/plans/fba-shipment.md(FBA 发货计划/装箱/对账,V1 内部数据面)。
必读:根 CLAUDE.md → 计划书全文 → docs/07 §6(建表)。
会话目录:仓库根。
预拍板(2026-09-10,不再询问):
- V1 只做内部数据面,零平台凭证依赖;SP-API Inbound 接入只留 TODO 编号槽位(#3 凭证卡点),不实现。
- SHIPPED = 按 SKU 逐行 OUT_SHIP 走 change()(biz_type=FBA_SHIPMENT),成本随货走移动加权成本账。
- 装箱阶段不占库存(无 LOCK_SHIP)。
- 对账 diff 仿 RefundReconciliationService(SHORT/EXTRA/OK 三态);FbaReconciliationJob 默认关。
完成定义:计划书「验收标准」全过(动账/成本账断言、真库验证);TODO.md FBA 条目收口;计划书头部追加实施行。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

### 9) SSE 实时推送(仓库根,跨栈)

```text
实现 docs/plans/archive/sse-notify.md(SSE 浏览器实时推送通知,后端 + 前端接线)。
必读:根 CLAUDE.md → 计划书全文 → docs/09(动 erp-web 前必读)。
会话目录:仓库根。
预拍板(2026-09-10,不再询问):
- 后端订阅端点 POST text/event-stream,复制 ErpChatController 流式形态。
- 前端复用 src/utils/sse.ts 的 postSse(fetch + Bearer);禁用 EventSource,禁改 base.ts。
- 每用户并发连接上限 5,心跳 30s;推送挂 NotifyPushedEvent 监听(AFTER_COMMIT)广播全部在线连接。
- 前端:SSE 健康时暂停 60s 轮询;断线指数退避 1s→30s 并回落轮询兜底。
- 多实例跨进程广播只登记 TODO(编号),不实现 Redis pub/sub。
完成定义:计划书「验收标准」全过(后端编译+前端门禁四件;双端连通冒烟);TODO.md SSE 条目收口;计划书头部追加实施行。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

### 10) #6 Report tools(仓库根)

```text
实现 docs/plans/archive/06-report-tools.md(报表取数契约化 + AI 第八类工具)。
必读:根 CLAUDE.md → 计划书全文 → docs/07 §9(AI 代码规范)。
会话目录:仓库根。
预拍板(2026-09-10,不再询问):
- erp-contract 新增 ReportQueryApi 五方法;利润面直接复用既有 ProfitQueryApi,不二次包装。
- ReportTools 第八类 @Tool:单次返回硬上限 20 行 + 字段裁剪。
- chat 白名单与 Agent 桥(SpringAiAgentToolBridge)双通道接通。
- 验收只认 ai_chat_message 的 TOOL 审计行与 DB 实查,回复文本不作为证据。
完成定义:计划书「验收标准」全过(mvn 编译+单测;TOOL 审计行实证);TODO.md #6 Report tools 条目收口;计划书头部追加实施行。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

### 11) #26 前端走查(erp-web/ 开会话,分轮)

```text
执行 docs/plans/26-frontend-audit.md(#26 前端页面走查优化)第 N 轮:覆盖 <域清单,按计划书业务链顺序切 3-4 个域>。
必读:erp-web/CLAUDE.md → docs/09-前端开发规范守则 → 计划书全文。
会话目录:erp-web/。一轮走查 = 一个会话:走查 → ISSUE-<n> 登记 → 当轮修复完 → 收口。
预拍板(2026-09-10,不再询问):
- ≥3 文件同款改动一律临时脚本/正则批量;生成器缺陷改生成器后重生成产物,禁手补产物。
- SSE 断线重连走查项在 sse-notify 落地后纳入;此前遇到只登记不修。
- 每轮完成定义 = 本轮 ISSUE 全部闭环 + 门禁四件全绿(type:check / lint / lint:stylelint / build)。
完成定义:ISSUE 清单与修复结论写回计划书附录(或独立 ISSUE 文件);不做 git commit。
本会话只做这一轮,输出交付摘要即停。
```

### 12) #24 adapter Playbook(仓库根;**P0 首要任务——2026-09-13 拍板:抖店先行**)

```text
执行 docs/plans/24-adapter-playbook.md(P0 抖店 adapter + #24 平台 adapter 批量扩展 SOP):本次目标 = <二选一:抖店 adapter 按手册演练接入(P0 首要任务) / 以 <平台名> 按手册演练接入(随 #24 逐平台)>。
必读:根 CLAUDE.md → 计划书全文 → docs/04(平台对接层设计)→ .claude/skills/write-adapter。
会话目录:仓库根。
预拍板(2026-09-10,不再询问):
- 国内平台推送/Webhook 形态:推送只作为「触发拉单」的信号(收到通知→立即走既有 pull 链路),不做推送直接建单。
- 平台差异登记表禁止凭想象预填;预研取得真实资料后补录。
- 每个平台接入 = 独立会话独立投喂(手册即提示词母版),禁止一个会话接多平台。
- 翻译器单测用真实报文样本(脱敏)做翻译断言;新 adapter 默认不注册(erp.adapter.<p>.enabled 默认 false)。
完成定义:按手册阶段 0-6 推进到计划书「验收标准」口径;TODO.md #24 条目收口;计划书头部追加实施行。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

### 13) #27 权限增强(仓库根;拆三次会话,按序)——✅ 已全部实施(13a/13b 2026-09-12,13c 2026-09-12,devlog 见 docs/devlog/archive/TODO27-权限增强(数据权限店铺轴收口).md)

**13a 部门组织架构(先做)**

```text
实现 docs/plans/archive/27-rbac-enhance.md 的「部门组织架构」部分(对应 TODO #27-③)。
必读:根 CLAUDE.md → 计划书全文 → docs/07 §6(建表)。
会话目录:仓库根。
预拍板(2026-09-10,不再询问):
- sys_dept 树 + sys_user.dept_id;部门成环校验仿 erp-goods 分类先例。
- 本会话只交数据面 + CRUD + 菜单;与数据权限的联动不做(归第三会话)。
完成定义:计划书对应验收项全过;TODO.md #27 条目标注进度;计划书头部追加实施行(整项完成时)。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

**13b 操作审计**

```text
实现 docs/plans/archive/27-rbac-enhance.md 的「操作审计」部分(对应 TODO #27-②)。前置:部门部分已实施。
必读:根 CLAUDE.md → 计划书全文 → docs/07 §11(AFTER_COMMIT)。
会话目录:仓库根。
预拍板(2026-09-10,不再询问):
- sys_oper_log + @OperLog AOP,AFTER_COMMIT 异步落库,失败不影响主事务。
- params 截断 2KB + 敏感值掩码(凭证字段模式复用既有脱敏口径)。
- 首批挂五域:订单状态推进 / 库存动账 / 发货回传 / 财务勾稽 / 采购审核;Job 与系统链路不挂。
完成定义:计划书对应验收项全过(审计行真库可查);TODO.md #27 条目标注进度。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

**13c 数据权限 + 字段级评估收口**

```text
实现 docs/plans/archive/27-rbac-enhance.md 的「数据权限」部分(对应 TODO #27-①),并顺带产出「字段级权限」评估结论(#27-④)收口整项。前置:部门、审计已实施。
必读:根 CLAUDE.md → 计划书全文 → docs/07 §2.2(模块边界)。
会话目录:仓库根。
预拍板(2026-09-10,不再询问):
- 数据权限轴 = 店铺(sys_user_shop 关联表);按人/按部门轴不做。
- 注入点 = erp-api 的 contract impl 层;CurrentUserApi 扩容 currentShopIds();禁用 MP DataPermissionInterceptor。
- JWT payload 结构不变;Job / 系统链路禁调 CurrentUserApi 的既有口径保持。
- 字段级权限(#27-④)只出评估结论:预期为「不引引擎,现有脱敏 + 角色粒度足够」,结论与复评触发点登记 TODO.md。
完成定义:计划书对应验收项全过;TODO.md #27 条目收口;计划书头部追加「已于 YYYY-MM-DD 实施」行。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

## 四、P3 逐项提示词(触发条件式——条件不满足不开工)

> 每条第 0 步都是核对触发条件;不成立时只输出结论,不动任何代码。开工前先确认条件真实成立再投喂。

### P3-1) 预警/监控扩容(仓库根;仅同买家规则可投喂——HIGH 已落地 2026-09-12,预警模型扩容已删除 2026-09-13)

```text
执行 docs/plans/p3-alert-monitor-expansion.md。
第 0 步:核对计划书头部触发条件是否成立;不成立则只输出「触发条件未满足:<原因>」并停止,不动代码。
必读:根 CLAUDE.md → 计划书全文 → docs/07 §9。
会话目录:仓库根。
预拍板(2026-09-10,不再询问):
- HIGH 推送 = 聚合推送(非逐条)+ 日上限 + 静默期,参数入 sys_config GROUP_ALERT;挂点在 AnomalyPersistNode 落库之后;不改 AlertEvent 结构(无 severity 字段,不扩)。
- 「同买家批量下单」走方案 A:聚合计算在 erp-order 内完成,契约/AI 建议/通知只出计数,买家标识不出契约。
- 扩容顺序:库龄规则先行(数据就绪)→ Listing 变动 diff 次之 → MONITOR_* 类卡 #24,不做。
完成定义:计划书「验收标准」全过;TODO.md 对应条目收口。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

### P3-2) 三工作流定时接线(仓库根)——✅ 已实施(2026-09-12,拍板与实施细节见 docs/devlog/2026-09-12-拍板解挂批次(小件五连+调拨在途).md §3);计划书已归档 archive/,本条仅存档不再投喂

```text
执行 docs/plans/archive/p3-ai-workflow-scheduling.md。
第 0 步:跑 ai_suggestion 采纳率统计(以 DB 实查为准,SQL 按计划书);三工作流采纳率均低于约 20% 则只输出结论并停止(接线 = 白烧 token)。
必读:根 CLAUDE.md → 计划书全文 → docs/07 §9/§5(调度)。
会话目录:仓库根。
预拍板(2026-09-10,不再询问):
- 三 Job 各自独立开关,默认 false。
- cron 错峰 03:00 / 03:30 / 04:00(调度器单线程,禁同时触发)。
- LLM token 上限配置键先于开关上线,超限熔断当次运行。
完成定义:计划书「验收标准」全过;TODO.md 对应条目收口。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

### P3-3) 聊天页体验(erp-web/ 开会话)——✅ 主体已实施(2026-09-12/09-13);余量(payloadJson 结构化渲染/代码块复制)已删除(2026-09-13);计划书已归档 archive/,本条仅存档不再投喂

```text
执行 docs/plans/archive/p3-chat-ux.md(触发条件:阅读痛点真实出现且引库已拍板——本次视为已拍板)。
必读:erp-web/CLAUDE.md → docs/09 → 计划书全文。
会话目录:erp-web/。
预拍板(2026-09-10,不再询问):
- 渲染引 marked + DOMPurify;DOMPurify 净化是硬红线(AI 输出按不可信输入),不可省。
- 交互 = 流式期明文追加、完成后整段 markdown 渲染;不做流式分块渲染。
- 停止生成 = postSse + AbortSignal,中断即丢弃本次输出(V1 不做续写)。
- ai_suggestion payloadJson 结构化渲染本期不做(等 schema 收敛);base.ts 不动。
完成定义:门禁四件全绿 + 计划书「验收标准」全过;TODO.md 对应条目收口。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

### P3-4) AI 深化四项(仓库根;一次会话一项)——❌ 已删除(2026-09-13 优先级重整;计划书文件已删,git 历史可溯),本条为唯一在库预拍板存档,不再投喂

```text
执行 docs/plans/p3-ai-deepening.md 的 <四选一:RAG×agent / 意图识别 / 多语言 / 更多角色>。
用户前置输入(未填则只输出追问并停止):客服用户定位 = <内部员工 / 外部买家>。
必读:根 CLAUDE.md → 计划书全文 → docs/07 §9。
会话目录:仓库根。
预拍板(2026-09-10,不再询问):
- RAG×agent 走方案 A:检索做成只读 KbTools @Tool,经 SpringAiAgentToolBridge 注入,agent 自主取用;前置 = chat 侧检索命中率达标(计划书阈值)。
- 多语言 = kb 加语言维度(add-table 流程)。
- 新增 agent 角色 = 工具白名单子集 + 独立 prompt,禁复制现有角色骨架改名。
- 意图识别的定位结论以用户填写为准,推翻预设路径时先改计划书再动工。
完成定义:计划书对应卡片「验收标准」全过;TODO.md 对应条目收口。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

### P3-5) 发货域扩展(仓库根;两半独立)

```text
执行 docs/plans/p3-fulfill-extensions.md 的 <二选一:①代发/海外仓 / ②回传异步化>。
若选①,用户前置输入(未填则改做②并说明):计划书 §2.1 四问答案 = <占单形态 / 采购联动 / 运单来源 / 成本归集(必须与 #19 利润口径对齐)>。
必读:根 CLAUDE.md → 计划书全文 → docs/07 §4/§11。
会话目录:仓库根。
预拍板(2026-09-10,不再询问):
- ①:与 fba-shipment 骨架合并设计(type 区分,不建两套平行表);代发不动账(无 LOCK_SHIP/OUT_SHIP),不是绕路动账。
- ②:独立 ship-sync- 线程池(禁复用 pullScheduler)、有界队列 100、拒绝记 pull_log 走补偿扫;sync_status 列 + 前端可视化必做;开关 erp.ship.sync.async-enabled 默认 false。
完成定义:计划书「验收标准」全过;TODO.md 对应条目收口。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

### P3-6) 超退上限收紧(仓库根)

```text
执行 docs/plans/p3-aftersale-refund-cap.md。
第 0 步:数据实证——跑计划书统计 SQL 找「现有口径放行过的超退样本」;无样本 = 触发不成立,只输出结论并把结论登记 TODO.md,停止。
必读:根 CLAUDE.md → 计划书全文 → docs/07 §4(复合事务)。
会话目录:仓库根。
预拍板(2026-09-10,不再询问):
- 新口径 = 累计实发量 + 容差 0(精确拦截;边界场景靠既有人工低频接受立场兜底)。
- 开关 erp.aftersale.refund-cap-mode 默认 SHIPPED,可回退 ORDER_LINE(回退后行为与现状逐字节一致)。
- #11 并发窗口立场 = 延续人工接受,与本次同批 devlog 拍板。
完成定义:计划书「验收标准」全过(含开关两态单测、真库验证);TODO.md #12 条目收口。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

### P3-7) 商品/AI 增值四项(仓库根;一次会话一项)——❌ 已删除(2026-09-13 优先级重整;计划书文件已删,git 历史可溯),本条为唯一在库预拍板存档,不再投喂

```text
执行 docs/plans/p3-goods-ai-extensions.md 的 <四选一:供应商比价 / 文案风格 V2 / listing 回填 / 选品权重自调优>。
第 0 步:核对该项触发条件(计划书元信息表);不成立则只输出结论并停止。
必读:根 CLAUDE.md → 计划书全文 → docs/07 §9(涉及 AI 项)/write-adapter skill(涉及 listing 回填项)。
会话目录:仓库根。
预拍板(2026-09-10,不再询问):
- 供应商比价硬前置 = 报价记录域先行(add-table/add-domain,另开会话,本项不跳步)。
- 文案风格 V2:风格参数用配置表承载,禁硬编码、凭证类禁入;参数借 #24 预研成果,禁想象预填。
- listing 回填 = SPI 扩容级(走 write-adapter);人工二次确认,ai_suggestion adopt 不自动回填。
- 选品:先做权重效果报表,自调优在效果被认可后另立项。
完成定义:计划书对应卡片「验收标准」全过;TODO.md 对应条目收口。不做 git commit。
本会话只做这一项,输出交付摘要即停。
```

## 五、维护

- 某项实施完成:本文件对应条目顶部标「已实施(日期)」保留存档;预拍板被推翻时**改本文件并同 commit 更新对应计划书**(计划=拍板事实)。
- 新增子项(如 #27 再拆、#24 逐平台):按第二节模板手拼,追加进对应小节。
