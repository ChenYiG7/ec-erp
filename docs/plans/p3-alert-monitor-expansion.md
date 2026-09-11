# P3 异常通知与预警扩容实施计划书(HIGH 推通知 / 同买家规则 / 预警模型扩容)

| 元信息 | 值 |
|---|---|
| TODO 条目 | P3 三项合并:#6 HIGH 级异常推通知;#6「同买家批量下单」规则;预警模型扩容(向领星 29+ 形态靠拢) |
| 性质 | **触发条件式计划**(P3 拍板挂起:需真实使用数据/业务确认后才开工,本文预设开工路径) |
| 触发条件 | ①HIGH 推通知:AnomalyJob 实际运行 2-4 周,统计 HIGH 条目量级(日均条数)后拍板推送策略;②同买家规则:出现真实刷单/恶意下单损失案例,或 V2 契约扩容窗口;③预警扩容:上述任一新规则的**数据源已就绪**且业务提出诉求 |
| 前置依赖 | #14 三渠道(站内/Webhook/邮箱)已投产;销量日表/库存快照已就绪;部分新规则依赖新拉取类型 MONITOR_*(卡 adapter 扩展 #24) |
| 明确不做 | 推送轰炸防护之外的完整告警中心产品(规则 CRUD 后台,随实际规模再立项) |

## 一、背景与现状(2026-09-10 代码事实)

- **预警引擎**(erp-ai `alert/`):`AlertEngine` + `AlertEvent`(字段 notifyType/title/content/bizType/bizId,**无 severity 字段**);五规则词表 TYPE_*:`LOW_STOCK`/`SHIP_TIMEOUT`/`REFUND_ABNORMAL`/`SLOW_MOVING`/`OVERSTOCK`;出口 `AlertJob`(fixedDelay 1h)收口 → `pushAllUsers`(→站内+Webhook/邮箱两监听)。
- **异常工作流**(erp-ai `graph/` Anomaly 两段式母本):`AnomalyScanNode`(规则基线,AnomalyRule 词表如 ZERO_AMOUNT/HIGH_DISCOUNT)→ `AnomalyScoreNode`(LLM 定级 riskLevel HIGH/MID/LOW,降序截断送评,API 失败/漏回回落规则基线并标降级)→ `AnomalyPersistNode`(落库,风险等级收条目终值,LLM 定级为增量、规则基线为下限;**重复 run 产生新一批=已接受语义**)。`AnomalyJob` cron 02:30 已接定时。**当前 HIGH 结果只落库,无任何推送出口**。
- **契约**:统一订单链路无 buyer 字段(PII 不出契约是安全红线);买家信息只存在于 shop_order.raw_json 与平台报文中。
- 领星对标(docs/10):29+ 预警模型形态(Listing 变动/关键词排名/库龄/店铺绩效等),本项目 5 规则。

## 二、方案设计

### 2.1 #6 HIGH 级异常推通知

- 挂点:`AnomalyPersistNode` 落库后(AFTER_COMMIT 语义)过滤 riskLevel==HIGH 条目 → 聚合为一条通知(禁逐条推,防轰炸)→ `SysNotificationService.pushAllUsers`(三渠道自动接通)。
- **推送量护栏(本项为何挂 P3 的原因)**:每日推送条数上限(sys_config GROUP_ALERT 键,超限聚合为"今日 HIGH 异常 N 条"摘要);静默期(同店铺/同规则 24h 内只推一次,抄 RefundReconciliationJob existsRecent 先例)。
- `AlertEvent` 是否加 severity 字段:预警引擎与异常工作流是两条链,本项**不动 AlertEvent**(异常工作流自有 riskLevel),预警规则将来分级时再统一评估——避免为一条链动公共词表。

### 2.2 #6「同买家批量下单」规则

- **PII 约束下的可行口径(核心拍板点)**:契约无 buyer 字段是红线不是缺陷。两个方案:
  - **方案 A(推荐)**:规则计算收口在 erp-order 侧(数据就地,raw_json 可及)——新增只读契约方法"窗口内同收件人(哈希/电话尾号等脱敏键)订单数聚合",返回**聚合计数不返明细**;AnomalyScanNode 经契约取数判定。
  - 方案 B:UnifiedOrder 扩 buyerHash 字段(拉单翻译时算哈希落库)——动统一模型与 uk 语义,波及面大,仅当 A 的聚合查询性能不可接受再议。
- 判定口径:同脱敏键 + 窗口(如 1h)内订单数 ≥ 阈值(sys_config 词表白名单同款热更)→ 命中规则基线(riskLevel 下限 HIGH),进两段式定级。
- 新规则进 `AnomalyRule` 词表 + 单测(词表外拒绝语义已有先例)。

### 2.3 预警模型扩容(领星 29+ 形态对齐)

- 候选规则按**数据就绪度**排序(每条=TYPE_* 常量+规则+取数面):
  1. **库龄预警**:数据已就绪(inventory_snapshot_daily + sku_cost_state/流水可推首次入库时间),无需外部依赖——**首选开工项**;
  2. **Listing 变动**(价格/上下架):需 product 拉取做快照 diff(shop_product 有数据,需加 diff 检测逻辑,小改);
  3. **店铺绩效**(订单缺陷率/迟发率等):依赖新拉取类型 MONITOR_*(PlatformClient SPI 扩容,卡 #24 资质);
  4. **关键词排名**:依赖搜索词报告拉取(Amazon 业务报告/广告数据,卡 #20/#24)。
- 扩容纪律:一规则一文件(AlertEngine 现状惯例)、词表 TYPE_* 常量+DDL 注释同步、阈值走 sys_config 热更、AlertJob 出口不改(规则数多了再评估分批调度)。

## 三、开工路径(触发后)

1. HIGH 推通知:AnomalyPersistNode 后置钩子 + sys_config GROUP_ALERT 键族(上限/静默期)+ 聚合文案 + 单测(护栏触发/静默期拦截)。
2. 同买家规则:拍板 A/B → 契约聚合方法(A)或模型扩字段(B)→ AnomalyRule 新增+ScanNode 规则+阈值键。
3. 预警扩容:按数据就绪度逐条上(每条独立提交,禁一把梭);新拉取类型走 write-adapter skill SPI 扩容。
4. 每条规则上线后观察一周推送量,超护栏即调阈值(数据回流)。

## 四、验收标准(按触发项)

- HIGH 推通知:HIGH 条目聚合推送且三渠道可见;MID/LOW 不推;超上限日收摘要而非 N 条轰炸;静默期生效。
- 同买家:构造同键多单样本命中且定级≥HIGH;跨店不误合;明细 PII 不出契约层(断言契约 record 无 buyer 明文字段)。
- 新预警规则:真库数据触发样本→通知可达;阈值热更生效;规则单测+AlertJob 回归不破坏既有五规则。

## 五、红线提醒

- **PII 红线**:买家身份信息(姓名/电话/地址)禁出契约层、禁进 ai_suggestion payload、禁进通知内容(通知里只可有订单号/数量/脱敏键)。
- AI 产出纪律:风险等级/理由落 ai_suggestion 口径不变;推送是通知不是 AI 写操作。
- sys_config 键禁凭证;阈值词表白名单热更走 #18 事件失效缓存口径。
- 新拉取类型(MONITOR_*)走 PlatformClient SPI+翻译器纪律(报文翻译零业务 if),限流自动挂 PlatformGateway。

## 六、交接边界

1. 推送量护栏参数(每日上限/静默期时长)拍板。
2. 同买家规则 A/B 方案拍板(推荐 A);脱敏键口径(收件人哈希 vs 电话尾号)拍板。
3. 预警扩容的规则优先级与节奏(建议库龄先行)随业务诉求逐条确认。
4. 领星 29+ 形态只做功能对标,规则参数不抄(数据形态不同)。
