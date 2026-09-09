# TODO(#6) AI三期双件:库存预警引擎+SAA补货工作流

- 日期: 2026-09-06
- 收尾提交: 93bef9d feat: #7 SKU 批量查询端点 + 前端 skuId 列翻译收口,裸 ID 清零

## 拍板
- **补货工作流偏离 TODO 原文"取数LLM"**:改为程序取数 + 程序计算、LLM 只写报告(ReplenishSummarizeNode 单次调用产逐 SKU 摘要)。
  理由:低库存筛选与补货公式确定性强,交给 LLM 既费 token 又引入不确定性;"判断归 AI、执行归程序"的铁律 8 落到 AI 层内部同样适用。
  LLM 不可用时摘要三重降级(apiKey 空/调用失败/解析失败)落模板串、degraded=true 照跑照落库——摘要是锦上添花,不允许模型故障阻断建议产出。
- **预警引擎推送收口 erp-api AlertJob,erp-ai 不依赖 erp-system**:AlertEngine 只产 AlertEvent(record),
  静默期去重与 #14 扇出在 Job 侧;跨域方向保持"erp-ai → erp-contract 只读契约"单向,通知能力由聚合模块编排接入。
- **静默期免建去重表**:sys_notification 自身即"上次告警时间"存储(existsRecent 按 notifyType + idx_created 范围判定),
  预警量级(每类每日至多一条)远够;告警通知正文截断 1000 沿用 #14 写侧兜底。

## 改动
- erp-ai/alert/:AlertEngine(V1 三规则:低库存/发货超时/退款异常,分页扫全量带 scanPageSize/scanMaxRows 护栏,
  单规则失败隔离;滞销/积压留 TODO(#6) 待销量数据面)+ AlertEvent + ErpAlertProperties(erp.alert.* 全配置化)。
- erp-ai/graph/:SAA Graph Core 四节点工作流(collect 跨仓合并→calculate 公式→条件边→summarize LLM 可降级→persist 落
  ai_suggestion type=REPLENISH,可用≤0 即 HIGH)+ ReplenishmentController(POST /api/ai/replenishment/run,登录即可)。
- erp-api/job/AlertJob:每小时 fixedDelay,模式同 OrderPullJob(开关→LockService alert:scan 效率锁→评估→静默去重→扇出);
  SysNotificationService 新增 existsRecent。
- erp-ai/chat/:AuditingToolCallback 装饰器——ToolCallback invoke 前落 TOOL 审计行(工具名+入参 JSON 截断),
  同步/流式双通道统一生效,补掉地基会话"TOOL 行词表预留不落"的欠账。
- 根 pom:victools jsonschema-module-jackson 钉 5.0.0(见坑);erp-ai pom 补 web/validation/MP/test 四件;
  yml 布 erp.alert.* 默认值;01_schema_init.sql notify_type COMMENT 扩 LOW_STOCK/SHIP_TIMEOUT/REFUND_ABNORMAL;
  本双件新增单测 32(预警 15:AlertEngine 8 + AlertJob 6 + existsRecent 1 / graph 12 / TOOL 审计 5);
  erp-ai 模块测试合计 49、erp-api 新增 20(契约四件 14 + AlertJob 6),存量类扩展 getSkuByCode 2,全模块 mvn test 绿。

## 坑
- **victools 传递依赖仲裁把 spring-ai 拉炸(T1 当场炸出)**:spring-ai 2.0.1 JsonSchemaGenerator 静态引用
  jsonschema-module-jackson 的 JacksonSchemaModule(该类仅 5.0.0 存在,4.38.0 已更名 JacksonModule);
  agentscope 2.0.2 直依赖 4.38.0,Maven nearest-wins 仲裁下 5.0.0 被拉低 → 启动即 NoClassDefFoundError。
  解法 = 根 pom dependencyManagement 钉 5.0.0;遗留:agentscope 四期真启用时若与 5.0.0 不兼容需再评估(已写进 TODO.md graph/ 条目)。
- SAA 2.0.0-M1.1 graph-core 的 API 形态:KeyStrategyFactoryBuilder + KeyStrategy.REPLACE 逐键声明状态合并策略,
  条件边分支串必须与 mappings key 严格一致(无低库存项直达 END 的分支同样要登记),图装配错误用 GraphStateException
  捕获转 IllegalStateException 启动即失败,优先于运行期才发现。

## 未尽
- 定时接线两处待拍板间隔:AlertJob 已接(默认 1h 可配);补货工作流仅手动端点,TODO(#6) 参考 AlertJob 模式待拍板。
- 销量数据面(无销量表/视图):预警滞销/积压规则、补货动销公式重估,两个 TODO(#6) 槽位等 inventory_snapshot_daily 类统计面落地。
- ai_suggestion 无唯一键,重复触发 run 会产生新一批待确认建议(建议语义可接受);若接定时调度需一并拍板防堆积策略
  (如同 SKU 待确认去重/合并),随定时接线 TODO(#6) 一起评估。
- SSE 帧格式真模型联调校准随 #3(前端已按裸文本 data: 块防御实现);agent/ 四期 AgentScope 多 Agent 不变。
