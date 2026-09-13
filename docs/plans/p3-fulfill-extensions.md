# P3 发货域扩展实施计划书(供应商代发/海外仓流程 + 回传异步化)

| 元信息 | 值 |
|---|---|
| TODO 条目 | #11 两项:FBA/OVERSEAS 发货单类型的供应商代发与海外仓发货流程(待业务确认后细化);发货回传异步化(AFTER_COMMIT 同步执行,真凭证实测耗时后再评估) |
| 性质 | **触发条件式计划** |
| 触发条件 | ①代发/海外仓:业务上出现真实代发或海外仓发货订单流(或国内 adapter 落地使此类订单量级可见);②异步化:#3 真凭证联调实测 uploadTracking 耗时(如 P95>3s 或超时率>1%)或回传失败影响 ship 事务体验 |
| 前置依赖 | ①与 fba-shipment.md(FBA 内部数据面)同源,代发/海外仓可复用其状态机与装箱骨架;②依赖 #3 真凭证(无凭证无耗时数据) |
| 明确不做 | 物流商 API 对接(4PX/云途,四期);海外仓库存全量跟踪(随 fba-shipment 口径延后);消息队列引入(线程池+pull_log 重试足够,量级不到) |

## 一、背景与现状(2026-09-10 代码事实)

- **发货单域**(#11 已投产):状态机+建单即占库存(LOCK_SHIP,缺货建单即拦)/ship 同事务核销+发足判定推进订单(部分发货不推进)/cancel·delete 释放;`delivery_order.type` 枚举预留 `FBA/OVERSEAS`;`DeliveryOrderService` L326 **FBA 订单拦建发货单**("仅卖家自履约订单可创建发货单")——代发/海外仓流程当前同样无通路。
- **回传链路**:`ShipmentSyncService` 发货回传编排,**AFTER_COMMIT 事件驱动同步执行**,失败记 pull_log 不回滚本地;FBA 在编排内直接跳过;`PlatformClient.uploadTracking(session, PlatformShipment)` MFN 回传。
- 调度基建:SchedulingConfig 单线程 `pull-sched-` 前缀——**TODO 原文明示异步化"需独立 executor,禁复用 pullScheduler"**。

## 二、方案设计

### 2.1 供应商代发(supplier drop-shipping)

- **业务形态预设(待业务确认后细化——本节是提问清单多于方案)**:
  - 订单(平台)→ 不经国内库存,直接由供应商/工厂发货给终端买家;国内仓不动账(无实物入库)。
  - 拍板清单:①代发单是否占用 delivery_order(建议:type=新枚举或复用 SELF_FULFILL+代发标记,**不占库存**是唯一硬差异——LOCK_SHIP 跳过);②采购联动:代发单是否自动生成采购单(供应商直发=采购与销售同单流,建议 V1 手动关联 po_id);③回传:供应商提供运单号后人工录入→uploadTracking 照常;④对账:代发采购成本直接挂订单成本(不走 OUT_SHIP 流水→**成本归集键断点**,需与 #19 利润口径约定代发成本的归集方式——这是本项最深的坑,提前登记)。
- 开工第一件事:业务确认会(订单量/供应商配合度/运单号回传方式),产出后再按 fba-shipment 的 add-table 模式细化。

### 2.2 海外仓发货(OVERSEAS)

- 形态:国内仓→海外仓头程(头程运费分摊计划承接)+ 海外仓本地发货(订单 fulfillment_channel=OVERSEAS_WAREHOUSE)。
- 与 fba-shipment.md 高度同源(type=OVERSEAS 预留同位):建议**开工时与 FBA 数据面合并设计**(同一套 fba/overseas 发货单骨架,type 区分),避免两套平行表。
- 海外仓库存口径跟随 fba-shipment 拍板(V1 只对账面不 track 库存)。

### 2.3 发货回传异步化

- 形态:ship 事务 AFTER_COMMIT 后不再同步调 uploadTracking,改为**投递内存队列(独立 `ThreadPoolTaskExecutor`,有界队列+拒绝记 pull_log)**,worker 线程异步回传;失败按 pull_log 现有记录,补偿=定时扫描失败记录重试(次数上限+退避)。
- **禁复用 pullScheduler**(TODO 原文红线):独立 executor 命名 `ship-sync-`,池大小 2-4,队列有界(100),拒绝策略=记 pull_log 等补偿扫。
- 语义变化登记:回传成功前订单本地已 SHIPPED(现状也是——回传失败本就不回滚),异步化只是把"用户等待回传"变成"后台追";**回传状态可视化**(发货单加 sync_status 列:待回传/成功/失败,前端列展示)是配套必做项,否则失败不可见。
- 幂等:同发货单重复回传由平台侧幂等+本地 sync_status 条件更新守卫。
- **预做登记(2026-09-12,联调前脱机落地)**:executor + 开关通道已就绪——开关 `erp.shipment.sync-async`
  默认 false(与同域 `erp.shipment.sync-enabled` 同命名空间,替代本节草案键 `erp.ship.sync.async-enabled`);
  独立单线程 executor `ship-sync-`(懒线程+空闲回收,关态零常驻;池参数按实测调的拍板不变,激活时只改参数)、
  有界队列 100;拒绝策略偏离登记:用 **CallerRuns 背压**(满载降级回同步不丢事件)替代草案"拒绝记 pull_log"
  ——负载型拒绝在 CallerRuns 下不存在,拒绝只在停机期出现(此时记 error 留痕, pull_log 写入同样不可靠);
  失败补偿扫语义不受影响(仍扫 pull_log 失败记录)。**激活期余量不变**:sync_status 列 + 前端可视化 +
  失败补偿扫,真凭证实测耗时后拍板。
- **余量补齐登记(2026-09-12,同日脱机落地——激活只剩翻开关)**:三项余量全部落地,形态与草案对齐带一处细化——
  ①`delivery_order` 加 `sync_status(PENDING待回传/SUCCESS成功/FAILED失败,NULL=未发货无关,存量已发货单不回溯)/
  sync_retry_count/sync_fail_reason/sync_time` 四列 + idx_sync(ship 即置 PENDING;成功条件更新翻牌仅 PENDING/FAILED
  可翻,幂等;失败计数 SQL 内自增);②前端发货单页加"回传状态"列(待回传/成功/失败 tag);③补偿扫=
  `ShipmentSyncRetryJob`(`erp.shipment.retry-*` 五参:enabled 默认关/30min 一扫/上限 5 次/退避 10min×计数/
  批量 100),扫"已发货未回传成功"的**发货单**重试而非字面扫 pull_log——pull_log 无 delivery 维度无法定位单据,
  且发货单态天然涵盖"事件丢失的 PENDING"(停机期提交被拒/崩溃于 AFTER_COMMIT 后)与"跳过未遂"(adapter 后接入),
  失败原因细节仍在 pull_log 互补;退避=距上次尝试 N×backoff 分钟,超限停扫保持 FAILED 待人工,无运单号不选。
  已建库跑 `scripts/replay_schema_migration.py` 补列(HISTORY_COLUMNS 已登记,真库验证 3/3 过)

## 三、开工路径

1. ①:业务确认会 → 与 fba-shipment.md 合并 add-table(type 语义/代发标记列)→ 状态机扩展(代发不占库存的分支)→ 成本归集口径与 #19 对齐(devlog 拍板)→ 前端。
2. ②:独立 executor 装配(erp-api config)→ ShipmentSyncService 投递改造(AFTER_COMMIT 后 submit)→ sync_status 列+条件更新 → 失败补偿扫(定时低频)→ 监控(失败率日志)。
3. 灰度:开关 `erp.ship.sync.async-enabled` 默认 false,真凭证环境实测开启;回滚=关开关回同步路径。

## 四、验收标准

- 代发(触发后):代发单不产生 LOCK_SHIP/OUT_SHIP;运单号录入→回传成功;成本归集按拍板口径进利润面。
- 海外仓:与 FBA 骨架合并后 type=OVERSEAS 全链同 FBA 验收标准。
- 异步化:ship 接口耗时回到事务本身(无平台 RT 抖动传导);回传成功率/失败补偿闭环可见(sync_status);并发 ship 多单时队列不丢(拒绝走 pull_log 补偿);关开关回退行为与现状一致。

## 五、红线提醒

- 库存动账唯一入口 change() 不变(代发=**不动账**,不是绕路动账)。
- 异步 worker 禁再触发事务(只做回传+状态回写,状态回写走条件更新短事务);**禁复用 pullScheduler**(词表/线程池隔离)。
- 回传失败语义=可观测可补偿,禁吞;sync_status 是发货单域事实,订单推进(发足判定)不依赖回传结果(现状口径保持)。
- 代发成本归集若走非 OUT_SHIP 路径,**必须先与利润口径(#19)对齐归集键再动工**,防缺口纪律静默失配。

## 六、交接边界

1. 代发业务形态四问(占单/采购联动/运单来源/成本归集)业务确认后才能细化——本计划到提问清单为止。
2. 代发与 FBA/OVERSEAS 是否合并一套单据骨架(建议合并)拍板。
3. 异步化的队列形态(内存有界队列 vs 未来 MQ)——当前量级内存够,引 MQ 是四期架构题。
4. executor 池参数按实测调,不预设为拍板项。
