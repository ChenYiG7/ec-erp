# TODO(#6) Report tools 第八类落地

- 日期: 2026-09-10
- 收尾提交: 531c36c docs: 文档体系全量入库与工程收口——架构/设计/规约/对标差距分析/devlog 31 篇随仓库发布,scripts 一次性脚本归档,TODO 重写为纯待办清单,devlog 清理过时公开治理日志(#15 文档边界拍板)

## 拍板

- **契约落 4 个报表面方法**:`salesDailySummary` / `skuSalesTop` / `skuTrend` / `inventorySnapshotSummary`(全 record + QueryPage,零 MP 类型)。计划书 §2.1 唯一列名的就是这 4 个;执行提示词的"ReportQueryApi 五方法"按其同句"利润面直接复用既有 ProfitQueryApi,不二次包装"理解为把利润 bullet 计入了计数(周报未入契约,报表中心 REST 仍可供前端)。
- **工具面 5 个 @Tool**:4 个报表工具 + `reportProfitSummary`(经既有 ProfitQueryApi,不重复包),与计划书 §2.2"5 个 @Tool 方法"、§七②"宁少而准"一致。
- **利润工具只返 `OrderProfitSummary` 聚合面,不返行页**:OrderProfitRow 19 字段含 orderItemId/orderId/shopId/platformOrderItemId 等内部 id 堆栈,与 §2.2"AI 裁剪:排除内部 id 堆栈"直接相悖;聚合面已含售价/成本/佣金/利润 + 三类缺口计数,足够回答"这段时间利润如何"。行级/SKU 排行如需再扩第 6 工具(ProfitQueryApi.pageOrderProfit/listSkuProfitRank 已就绪)。
- **零新 SQL**:分页在 Impl 层做内存分页(日报 ≤365 行 / 单日快照全行,行数天然受限),不为 LIMIT 新写 SQL(§2.3 口径);窗口缺省/钳制与快照日缺省全部沿用 erp-report ReportService 既有口径,不在契约层重复实现。
- **agent 双角色双开报表工具**(§七① 默认双开):`AgentRole.OPS` 白名单追加 5 个报表工具名(与库存/商品盘面并列)。
- **越界修复(计划外,已在交付摘要标注)**:`ErpChatService.chatOptions()` 无 DB 覆盖时返回 null,原链路无条件 `.options(null)` 被 Spring AI 断言拦下(`IllegalArgumentException: customizer cannot be null`)——即 `erp.ai.model` 处于种子态(NULL)时对话整体 500,报表工具根本不可达。改为"有覆盖才挂 options",与该私有方法既有注释口径("不额外建 options,保持原链路")对齐。

## 改动

- `erp-contract/ReportQueryApi.java`(新):契约 + 记录组(ReportSalesQuery/SalesDailyRow/ReportSkuQuery/SkuSalesRow/SkuTrendPoint/ReportInvQuery/InventorySnapshotRow),`@Builder` + `page()/size()/topN()` 归一。
- `erp-api/contract/impl/ReportQueryApiImpl.java`(新):委托 erp-report `ReportService`,域 record → 契约 record 显式逐字段映射;`skuId` 空短路;`skuTrend` 由 days 折算 [from,to](日期轴不进 SQL,#22 先例)。
- `erp-ai/tools/ReportTools.java`(新):5 个 @Tool;表格类 pageSize/limit 硬钳 20 行、趋势天数钳 30(§2.2 token 纪律);利润入参把工具面"含首含尾"日期转契约面 dateTo 不含(+1 天)。
- `erp-ai/chat/ErpChatService.java`:白名单 `ToolCallbacks.from(...)` 追加 ReportTools;抽出 `promptSpec()` 承载"有覆盖才挂 options"修复。
- `erp-ai/agent/AgentService.java` / `AgentRole.java`:生产构造器扩 ReportTools;OPS 白名单追加报表工具。
- `erp-ai/config/ErpAiProperties.java`:两处角色 prompt 注释补"报表"。
- 测试:新增 `ReportToolsTest`(7)、`ReportQueryApiImplTest`(7);`ToolsPagingDefaultsTest` +2(报表分页缺省);`ErpChatServiceTest` 新增"报表工具进白名单 + 无模型覆盖不炸"回归;`AgentServiceTest` 新增"OPS 可调 5 个报表工具"。
- 文档:CLAUDE.md(契约九件→十件、tools 七类→八类)、docs/02 §11/§13 状态、docs/07 §9(七类→八类)、TODO.md 移除 #6 Report tools 条目、计划书头部追加实施行。
- 依赖方向未变:erp-ai → erp-contract(未引 erp-report);实现收口 erp-api。

## 坑

- **`options(null)` 断言坑(本次踩到)**:Spring AI 2.0.1 `DefaultChatClientRequestSpec.options()` 对 null 直接 `Assert.notNull`;而 `AiRuntimeProperties.chatModel()` 无 DB 覆盖返回 `Optional.empty()` → `chatOptions()` 返回 null。单测里 spec 是 Mockito 桩(RETURNS_SELF),传 null 不报错,所以**单测全绿而生产 500**——这类"桩宽容掩盖空值"的链路,必须真机起服务才能暴露。
- **计划书数字自相矛盾**:§2.1 只列 4 个报表面方法却执行提示词写"五方法",§2.2 写"5 个 @Tool"——两处同时满足需把周报也做成工具(6 个)。已按"契约 4 + 工具 5(含利润)"取最贴合口径的解并记录在此,避免下轮再猜。
- **`erp.ai.model` 种子值为 NULL 是设计而非缺失**(docs/sql 注释:模型连接三键落 NULL 以免压掉部署侧变量)——但配合上面那条 `options(null)`,等于开箱对话即 500;两者叠加是隐性组合缺陷。
- **联调环境**:Bash 工具的 `mvn` 脚本在本沙箱解析 MAVEN_HOME 失败(`ClassNotFoundException: plexus-classworlds.launcher`),改直连 classworlds 启动器(临时 shim 落 target/,gitignored);背景进程跨调用会被回收,联调必须单次调用内跑完(起服务→对话→查库),且 localhost 出网被沙箱代理拦截,HTTP 客户端须显式禁用代理。

## 未尽

- **TOOL 审计行真机证据未取得**:本轮真机联调卡 `AI_API_KEY`(local.properties 内的 DashScope key 返回 401),模型调用不通 → 无 TOOL 行。已完成的替代验证:①报表面数据面活性(既有 REST 端点 `/api/report/*` 返回真实数据:销售日报 8 行、SKU Top5 5 行、库存快照 15 行;DB 直查 order_sales_daily 17 行 / inventory_snapshot_daily 41 行);②工具白名单与角色过滤由单测静态断言(5 个报表工具均进 chat 白名单与 OPS 白名单);③修复前后对照:日志中 `customizer cannot be null` 已归零,失败点前移到上游 401。**换有效 key 后需补跑**:起后端 → `POST /api/ai/chat/sessions` → `POST .../chat-sync` 问"最近 7 天销量 top5 的 SKU" → 断言 `ai_chat_message` 出现 `role=TOOL, tool_name=reportSkuSalesTop` 且数字与 `/api/report/sales/sku?limit=5` 一致。
- **周报工具位**:`salesWeeklySummary` 未入契约;若模型侧需要"按周"口径,补一个契约方法 + 工具即可(域服务已有)。
- **ACOS/广告工具位**:随 #20 广告 API 真凭证落地按第九类扩容,`ReportTools` 类注释已留锚点。
- **`openapi.json` 快照未刷新**:本次仅动 erp-contract/erp-ai(契约接口与 @Tool,非 Controller),`/v3/api-docs` 无变化,故 `pnpm api:sync` 为无差异操作,跳过。
