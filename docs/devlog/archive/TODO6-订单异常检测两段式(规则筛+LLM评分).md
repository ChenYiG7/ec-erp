# TODO(#6) 订单异常检测两段式:规则筛 + LLM 评分

- 日期: 2026-09-07
- 收尾提交: 待提交(本会话只落工作区,commit 随收尾拍板)

## 拍板
- **V1 规则集 = 四规则**(2026-09-06 立项拍板):UNPAID_TIMEOUT(WAIT_PAY 超时未付,LOW)/
  BIG_AMOUNT(已支付且 orderAmount×exchangeRate ≥ 10000 本位币,MID)/ ZERO_AMOUNT(已支付且
  orderAmount ≤ 0,HIGH)/ HIGH_DISCOUNT(已支付且 discountAmount ≥ orderAmount×0.5,MID)。
  不含发货超时逐单版——AlertEngine 已有同款聚合告警,避免双出口。
- **产出出口 = 仅落 ai_suggestion**(ANOMALY 类型,前端 AI 建议页已支持,零前端改动);
  HIGH 推通知留 TODO(#6) 随实际告警量评估。
- **两段式控成本**(docs/02 §13):规则引擎先筛(纯程序零 token)→ LLM 只评可疑样本;
  无可疑单走条件边直达 END,零 LLM 成本。LLM 半边批量单次调用,JSON 数组按 orderId 对齐,
  输入无 PII(OrderView 收件人/地址/buyer_note 不出契约),无需脱敏。

## 设计要点
- **paidTime 判空是防误报的关键守卫**:金额类规则一律要求 paidTime 非空——Amazon Pending 单
  落库金额归零(#4 落库口径),无此守卫整批误报;大额阈值本位币口径,汇率缺省按 1。
- 扫描口径只扫 WAIT_PAY/WAIT_SHIP 两态(待处理可干预,终态历史单不扫防重复命中);
  分页走 OrderQueryApi 只读契约(铁律 2/7),scanPageSize/scanMaxRows 按"单状态"钳制;
  单态扫描失败只记日志隔离,不殃及另一状态(同 AlertEngine 单规则隔离口径);时间走注入 Clock(docs/07 §10)。
- 同单多规则命中合并一行(hitRules 列表,基线风险取 max);规则→风险映射收口 AnomalyRule 枚举。
- **护栏 llmMaxItems=20**:超限按基线风险降序截断,未送评单直接规则定级且**不算 degraded**;
  三重降级(apiKey 空/调用失败/解析失败,含 ``` 围栏容错)与逐单漏回/词表外 riskLevel →
  规则定级 + 模板 summary + degraded=true,工作流照跑照落库(评分是锦上添花,模型故障不阻断产出)。
  prompt 集中 ErpAiProperties.Anomaly.scorePrompt(docs/07 §9)。
- 落库经 AiSuggestionService.save 唯一入口:suggestionType=ANOMALY / refType=SHOP_ORDER / refId=orderId /
  payloadJson={hitRules,ruleRisk,orderAmount,currency,exchangeRate,discountAmount,orderTime,paidTime,llmScored}
  (时间序列化为 ISO 字符串:裸 ObjectMapper 无 jsr310,且 paidTime 可空);
  重复 run 产生新一批 = 已接受语义(同补货),接定时前必须先拍去重语义 → TODO(#6)。
- 架构:SAA graph 三节点链(照 ReplenishWorkflow 母本)
  START → scan(四规则筛) → 条件边(无可疑单直达 END) → score(LLM 批量评分,可降级) → persist → END。

## 改动
- 新增 erp-ai graph/(com.own.erp.ai.graph 平铺):AnomalyRule(枚举)/ AnomalyItem / AnomalyStateKeys /
  AnomalyScanNode / AnomalyScoreNode / AnomalyPersistNode / AnomalyWorkflow / AnomalyRunResult;
  controller/AnomalyController(POST /api/ai/anomaly/run,登录即可)。
- 修改:ErpAiProperties.Anomaly 参数组(上会话已布,bigOrderAmount/unpaidHours/highDiscountRatio/
  scanPageSize/scanMaxRows/llmMaxItems/scorePrompt 全配置化);application.yml erp.ai.anomaly 登记块
  (注释态,调参放开);TODO.md #6 勾选;CLAUDE.md erp-ai 行。
- 零改动:erp-contract(全走现有 OrderQueryApi 字段)/ 前端(ANOMALY 枚举已注册)/ 数据库(ai_suggestion 已建)。

## 验证
- 单测 19 个:AnomalyScanNodeTest 9(大额汇率换算+缺省按1/未付超时边界/paidTime 判空 0 元单不误报专列钉死/
  高折扣边界/多规则合并取 max/空页即停/scanMaxRows 单态钳制/单态失败隔离)+
  AnomalyScoreNodeTest 6(无 key/调用失败/解析失败三重降级/围栏容错按 orderId 对齐保持扫描原序/
  词表外+漏回逐单回落/llmMaxItems 降序截断且未送评不算降级)+ AnomalyPersistNodeTest 2(逐单字段+payload 键/
  空列表零落库)+ AnomalyWorkflowTest 2(无可疑单条件边直达 END verifyNoInteractions/全链路落库 + degraded 传播)。
- `mvn -DskipTests compile` ✅;`mvn -pl erp-ai -am test` ✅ 68 全绿(新 19 + 存量 49 不回退)。
  注:必须带 `-am`——~/.m2 的 erp-contract 快照还是契约四件前的旧包,-pl 单模块 test 编译期取旧包必炸。

## 未尽
- 定时接线:与补货工作流同张 TODO(#6) 一并拍板间隔;接前先拍去重语义(现重复 run 产生新一批)。
- 「同买家批量下单」规则:契约无 buyer 字段(PII 不出契约),随 V2 契约扩容再上 → TODO(#6)。
- HIGH 推通知:随实际告警量评估 → TODO(#6)。
- 契约快照:openapi.json 待后端起着后 `pnpm api:sync` 收 /api/ai/anomaly/run。

## 定时接线 + 去重语义(同日第二拍,拍板权授权)
- **拍板 1 调度口径 = cron 每日低峰**:补货 02:00 / 异常 02:30 错峰(共用单线程调度器),不用 fixedDelay
  (会随重启漂移,低峰语义天然是固定时刻);cron 走 @Scheduled 占位符 `erp.ai.{replenish,anomaly}.cron`
  直读 Environment,不进 ErpAiProperties 重复建键;enabled 进 ErpAiProperties(默认 true,无 key 自动降级不炸)。
- **拍板 2 去重语义 = 同键存在待确认(status=0)建议即跳过**:异常按 refId/补货按 skuId;旧建议被采纳/忽略后
  若单据仍命中允许再产出——确认闭环自然运转,忽略后次日复现可接受(库存条件在变,忽略动作本身廉价)。
  收口 scan 段(规则筛后、LLM 评分前)零浪费 token;无可疑单不查库。
- 实现:AiSuggestionService 增 findPendingRefIds/findPendingSkuIds(eq 全量待确认行 + 内存交集,
  规避 Wrapper.in() 急切解析坑 docs/07 §10;待确认量级=人工未处理积压,天然有界);AiConsts 增
  REF_TYPE_SHOP_ORDER/REF_TYPE_INVENTORY 收口两 persist 节点字面量;erp-api job ReplenishJob/AnomalyJob
  (模式同 AlertJob:MDC traceId→开关→LockService replenish:run/anomaly:run→跑工作流;效率锁+去重兜底,双跑无害)。
- 测试 +12:AiSuggestionService 去重读侧 2 / Scan 去重 1 / Collect 去重 1 / 两 Job 各 4(开关/锁/执行/异常不穿透);
  ⚠️ 中途炸出:workflow 冒烟测试 verifyNoInteractions(建议服务) 被去重查询打破——正确解法是给去重加空列表守卫
  (语义本就应"无可疑单不查库"),而非放松断言。erp-ai 74 / erp-api 65 全绿。
- 验证注:-pl erp-ai,erp-api -am test 一把过(erp-contract 必须 -am 进 reactor,~/.m2 快照旧)。
