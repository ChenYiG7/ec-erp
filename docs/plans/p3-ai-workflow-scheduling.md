# P3 AI 三工作流定时接线实施计划书(采购/文案/选品)

| 元信息 | 值 |
|---|---|
| TODO 条目 | #6:采购/文案/选品三工作流定时接线(人工决策节奏,随实际使用拍板;补货/异常两工作流已接定时) |
| 性质 | **触发条件式计划** |
| 触发条件 | 三工作流的人工确认(adopt)率与产出质量经实际使用验证——即用户真的在用且采纳数据可评估节奏;任一工作流达到"每周至少被消费一次"即具备接线条件 |
| 前置依赖 | 无外部依赖(ReplenishJob 02:00 / AnomalyJob 02:30 先例完整) |
| 明确不做 | 工作流本身的算法改造;XXL-Job 可视化调度(四期,任务多了再上,docs/05);推送(AI 产出本就不推通知,确认闭环在页面) |

## 一、背景与现状(2026-09-10 代码事实)

- **五工作流矩阵**(erp-ai `graph/`):`AnomalyWorkflow` 两段式母本(Scan→Score→Persist)→ `ReplenishWorkflow`(V2 (s,S) 策略)/`PurchaseWorkflow`/`CopywritingWorkflow`/`SelectionWorkflow` 变体;共享组件 LowStockScanner/ReplenishCalculator。
- **定时接线现状**:补货(ReplenishJob cron 02:00)、异常(AnomalyJob 02:30)已接;采购/文案/选品三工作流**只有手动触发**,未接定时。
- **调度基建**:`SchedulingConfig` 单线程 `ThreadPoolTaskScheduler`(poolSize=1,前缀 pull-sched-)+ Clock Asia/Shanghai;Job 惯例=MDC traceId + `LockService` 抢锁(per-key tryLock,Redis 故障按 `erp.lock.fail-open` 降级)+ 总开关配置键(ReportDigestJob enabled=false 先例)。
- **确认闭环**:工作流产出落 `ai_suggestion`(status 三态 0待确认/1已采纳/2已忽略,cas 守卫 adopt/ignore)——**采纳率是本项触发判定的现成数据源**。

## 二、方案设计

### 2.1 接线形态(照抄补货/异常先例,零新机制)

- 三个 Job:`PurchaseSuggestJob`/`CopywritingSuggestJob`/`SelectionSuggestJob`,cron 错峰(建议 03:00/03:30/04:00,避开既有 01:00~02:30 快照与工作流带)。
- 每个开关独立(`erp.ai.workflow.purchase.schedule-enabled` 等,**默认 false**——与"随实际使用拍板"的定位一致,接线≠开启);cron 可配(`erp.report.digest.*` 同款覆盖先例)。
- LockService per-key:三 Job 各自 key,与 Replenish/Anomaly 同款,防手动触发与定时并发重入。

### 2.2 各工作流的节流口径(拍板点)

| 工作流 | 输入面 | 建议节奏 | 防重复 |
|---|---|---|---|
| 采购建议 | 低库存/在途/销量 | 每日一次 | 复用"重复 run 产生新一批=已接受语义"(Anomaly 母本语义),上一批未处理完是否覆盖需拍板 |
| 文案生成 | 触发源=新增/改版 listing(需圈定范围) | **低频**(每周)——文案无时效性,且 LLM 成本高 | 按 SKU+版本号去重,已生成未过期的跳过 |
| 选品评分 | 候选池定义(当前手动?) | 每周一次 | 候选池快照留痕,评分随数据滚动 |

- **成本护栏**:LLM 调用是花钱面——每 Job 单次 run 的 token 上限(sys_config 键,超限截断并留痕);文案/选品类默认低频的根因即在此。

### 2.3 触发判定数据(开工前先跑的评估)

- SQL 侧:`ai_suggestion` 按 source/suggestion_type 统计近 30 天 产出量/adopt 率/ignore 率——**采纳率<某阈值(拍板,建议 20%)的工作流不值得定时跑**(白烧 token)。
- 该评估本身是本计划的第 0 步,一条统计 SQL 即可,开工先跑。

## 三、开工路径

1. 第 0 步:跑采纳率统计,三工作流分别给出"值得接/不值得接"结论(人工拍板)。
2. 三 Job 骨架(八股同款:MDC+LockService+开关+cron 可配),先全部默认 false 落地。
3. 圈定各工作流的定时输入范围(采购=全量低库存;文案=listing 状态过滤;选品=候选池口径——后两者需拍板)。
4. token 上限护栏(sys_config)+ 截断留痕。
5. 单测:开关关=零动作;锁重入拦截;token 截断;cron 错峰不互相饿死(单线程调度器的既有约束,核对 01:00~04:00 带宽)。
6. 试运行一周(手动开一个),观察 ai_suggestion 量与采纳率回流,再逐个翻开关。

## 四、验收标准

- 开关默认全 false:升级部署后行为零变化(不烧 token)。
- 开启后:ai_suggestion 按预期节奏产出新批次,cas 确认闭环正常;重复 run 语义与手动触发一致。
- LockService 抢锁:手动+定时并发只跑一个;MDC traceId 贯穿日志。
- token 护栏:构造超限样本验证截断与留痕。

## 五、红线提醒

- 调度器单线程(poolSize=1):新增 Job 必须错峰,禁与快照/拉单带挤同一分钟(重演 SalesSnapshot 时间带事故的温床)。
- LLM 成本面:上限键必须先于开关上线;**禁无上限定时跑 LLM**。
- ai_suggestion 只落库不自动执行(铁律 7:AI 写操作必须人工确认——三工作流产出全部停在确认闭环)。
- Job 链路禁调 CurrentUserApi(既有铁律);失败记日志不中断调度。

## 六、交接边界

1. 三工作流各自的采纳率阈值与"值得接"结论(人工拍板,第 0 步产出)。
2. 文案触发范围/选品候选池口径拍板。
3. token 上限默认值拍板。
4. 不值得接的工作流:结论写回 TODO.md 收口(挂起理由=采纳率数据),不是删除。
