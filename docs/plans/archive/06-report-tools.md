# #6 Report tools(AI 工具第八类)实施计划书

| 元信息 | 值 |
|---|---|
| TODO 条目 | #6 Report tools:报表域取数契约化后 AI 工具第八类(报表数据面已就绪,ACOS 面卡 #20) |
| 优先级 | P2(无外部依赖) |
| 前置依赖 | 无;ACOS/广告面随 #20 凭证后扩 |
| 目标一句话 | erp-contract 新增报表只读查询契约,erp-ai 落地 `ReportTools` 第八类 @Tool,chat 与 agent 双通道可自然语言查报表 |
| 明确不做 | 新报表维度开发(报表中心面已就绪);广告报表工具(卡 #20);AI 写操作(铁律 7 永久红线) |

> **已于 2026-09-10 实施**(ReportQueryApi 契约 + ReportQueryApiImpl + ReportTools 第八类 + chat/agent 双通道接线),
> devlog 见 `docs/devlog/TODO6-Reporttools第八类落地.md`。
> 实施差异(与本文原案):①契约落 4 个报表面方法(salesDailySummary/skuSalesTop/skuTrend/inventorySnapshotSummary)
> —— 原文 §2.1 列名的即这 4 个,"五方法"仅见于执行提示词(疑为把利润 bullet 计入),周报未入契约;
> ②利润工具仅返 `OrderProfitSummary` 聚合面,不返行页(OrderProfitRow 19 字段含内部 id 堆栈,与本文 §2.2「AI 裁剪」相悖);
> ③`skuTrend` 返回点序列(销量合计/动销天数/期末库存可由点序列推导),不另带汇总块;
> ④联调卡环境:`docs/sql` 种子的 `erp.ai.model` 值为 NULL 时 `ErpChatService.chatOptions()` 返回 null,
> 原链路无条件 `.options(null)` 触发 `IllegalArgumentException: customizer cannot be null`(对话整体 500),
> 已随本次一并修复(有覆盖才挂 options,与该方法既有注释口径对齐);本轮真机联调另卡 AI_API_KEY 401,TOOL 审计行证据未取得。

## 一、背景与现状(2026-09-10 代码事实)

- **七类工具样板**(erp-ai `tools/` 一类一文件):ShopTools/GoodsTools/InventoryTools/OrderTools/PurchaseTools/DeliveryTools/AftersaleTools。注入模式(OrderTools 为代表):`@Component` + 构造器 `@Lazy XxxQueryApi`(断构造环,docs/07 §2.2)+ 方法挂 `@Tool/@ToolParam` + 方法体直转契约接口。取数走 erp-contract(禁横向依赖)。
- **工具编排**:`ErpChatService` 七类工具转 `ToolCallback[]` 白名单一次构建,每请求经 `AuditingToolCallback` 包装(工具名+入参 JSON 截断落 `ai_chat_message` TOOL 行,先落库再委托)走 `ChatClient.toolCallbacks`,同步/SSE 双通道统一。
- **ai_suggestion 闭环**(本项纯取数不涉及,但纪律同源):adopt/ignore 条件更新守卫。
- **报表数据面**:`erp-report` ReportController(9 端点:sales/daily、sales/weekly、sales/sku、inventory/snapshot、export/sales、goods/options、goods/trend、export/inventory、digest/preview)+ ReportService + ReportDigestService(三周期复用四方法零新 SQL)+ ReportQueryMapper + `report/` 下 8 个 Row/Response record——**未进 erp-contract**(ReportController javadoc 明注"AI 工具若需取数再契约化")。
- **辨析**:`erp-contract` 已有 `SalesQueryApi` 是补货/异常工作流吃 `order_sales_daily` 的契约,**不是**报表中心取数面,勿混淆复用。
- docs/02 §11「AI 自然语言查数|Report tools 第八类只读 @Tool|❌P2」;§13「七类只读 ✅,余量 Report tools 第八类」。

## 二、方案设计

### 2.1 契约先行(erp-contract)

- 新契约 `ReportQueryApi`(全 record 视图 + QueryPage,零 MP 类型,凭证不进契约):
  - `salesDailySummary(ReportSalesQuery) → QueryPage<SalesDailyRow>`(店铺×日销量/销售额)
  - `skuSalesTop(ReportSkuQuery) → List<SkuSalesRow>`(topN SKU 销量/销售额/时段)
  - `skuTrend(skuId, days) → List<SkuTrendPoint>`(单品日期轴——Service 层逐日对齐,日期轴不进 SQL,#22 先例)
  - `inventorySnapshotSummary(ReportInvQuery) → QueryPage<InventorySnapshotRow>`(库存快照面)
  - `profitSummary(ProfitQuery) → OrderProfitSummary + 行页`(利润看板数,经 ProfitQueryApi 已有契约?**辨析:ProfitQueryApi 已存在**——利润数不重复包,Report tools 直接注入 ProfitQueryApi;本契约只包 erp-report 面)
- 实现收口 `erp-api/contract/impl/ReportQueryApiImpl`(18 个 Impl 同目录惯例)→ 委托 erp-report 的 ReportService(注入 @Lazy)。

### 2.2 ReportTools 第八类(erp-ai)

- `tools/ReportTools.java`:5 个 @Tool 方法对应上列契约,**返回面为 AI 裁剪**:字段只留决策相关(日期/店铺/SKU/数量/金额/趋势方向),排除内部 id 堆栈与 raw 字段;行数上限硬编码(如 20,防 token 爆炸),@ToolParam 描述写清默认窗口。
- 白名单接线:ErpChatService ToolCallback[] 构建处加 ReportTools;`SpringAiAgentToolBridge` agent 双角色工具面同步(SUPPORT/OPS 均只读安全,默认双开,拍板点①)。
- 审计:AuditingToolCallback 自动覆盖(零改动)——**验证铁则(用户 memory):只认 ai_chat_message TOOL 审计行/DB SQL,回复文本会幻觉**。

### 2.3 SQL 纪律

- 优先**零新 SQL**(契约包现有 ReportService 方法);若裁剪字段确需新查询:9.7.2 原生形态 + `scripts/validate_report_sql.py` 扩展真库验证;日期轴不进 SQL。

## 三、实施步骤

1. erp-contract:ReportQueryApi + 视图 record(命名对齐 erp-report 现有 record 语义,不复制类,新定义收口契约)。
2. erp-api:ReportQueryApiImpl(委托 ReportService,@Lazy)。
3. erp-ai:ReportTools(第八类)+ ErpChatService 白名单 + SpringAiAgentToolBridge 扩容。
4. 单测:Impl 委托/裁剪/mock 契约(按七类工具测试先例);MP Wrapper 急切解析坑规避(docs/07 §10)。
5. 联调验证(铁则):全新会话起后端,curl 中文用 UTF-8 文件,chat 问"最近 7 天销量 top5 的 SKU"→ 断言 `ai_chat_message` 出现 role=TOOL 且 tool_name=报表工具行 + 返回数字与 SQL 直查一致。
6. `pnpm api:sync` 刷新 openapi.json 快照(契约变更随代码提交)。

## 四、表结构草案

无(纯契约/工具层,零 DDL;ai_chat_message TOOL 审计行复用)。

## 五、验收标准

- mvn 全量编译 + 单测绿;契约 record 不引 MP 类型(包依赖核查)。
- 联调:TOOL 审计行证据 + 数字与直查一致(**不接受回复文本自证**);超行数上限截断生效。
- agent 通道:OPS 角色同样可调报表工具(若拍板双开)。
- openapi.json 快照已刷新并随代码一致。

## 六、红线提醒

- **AI 工具只读**(铁律 7):ReportTools 禁任何写方法/写 SQL;产出物不落 ai_suggestion(纯查数,无建议闭环)。
- 契约全 record、零 MP、@Lazy 断环;模块方向 erp-ai→erp-contract,禁直接依赖 erp-report。
- token 纪律:返回面裁剪+行数硬上限;@Tool 中文描述(工具描述即 prompt,写清口径防误读)。
- 若加新 SQL:9.7.2 红线 + 当天真库验证(mock 测不出 XML 语法错——TODO 红线)。

## 七、交接边界

1. agent 双角色是否双开报表工具(默认双开)。
2. 工具方法集粒度(5 方法集 vs 更细;过多工具稀释选择准确率,宁少而准)。
3. 利润数经既有 ProfitQueryApi 注入(推荐)vs 重复包进 ReportQueryApi——推荐前者,避免双契约漂移。
4. ACOS 工具位登记:随 #20 广告报表落地后第九类扩容,本期仅留注释锚点。
