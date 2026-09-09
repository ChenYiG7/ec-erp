# TODO(#6) 销量数据面:order_sales_daily + 补货动销重估 + 滞销/积压预警

- 日期: 2026-09-07
- 收尾提交: 待提交(本会话只落工作区)

## 拍板
- **统计口径 = 支付日×SKU 合计购买数量**:DATE(paid_time) 为统计日(支付即需求确认,发货日受履约时延干扰);
  范围 = 已支付态 WAIT_SHIP/SHIPPED/COMPLETED(WAIT_PAY 未付不计,已支付后回转 CANCELLED 的单随重算窗口退出);
  未绑定 SKU 不统计(同"报表以已绑定为口径",docs/03 设计要点 3)。
- **V1 只落数量维(qty_sold)**:现消费者(补货动销/滞销/积压)只需数量;金额维(选品/ACOS)等真消费者出现
  再 ALTER 加列(统计表加列可空回填,演进成本低)——不为"以后可能"预付复杂度。
- **归属 erp-order(源数据域)+ 调度收口 erp-api**:聚合 SQL 建在订单域(自己的表自己聚合),
  Job 模式同 AlertJob(开关→LockService sales:snapshot 效率锁→重算);erp-ai 读侧只走 SalesQueryApi
  只读契约(第五件,铁律 2)。
- **窗口重算而非逐日追加**:每日 upsert 近 30 天(uk_sku_date 幂等,可安全重试),覆盖订单状态回传/
  取消单修正;30 天外的状态变更不回刷为 V1 已知边界(影响趋零,文档已声明)。
- **补货公式重估(收口 TODO 槽位①)**:日均销量 = 窗口销量合计/窗口天数,需求 = ceil(覆盖天数×日均)
  不在中间步骤截断;建议量 = max(min, 需求−可用−在途);**需求 ≤0 剔除不产建议**——零动销死 SKU
  不再被旧公式的固定 assumedDailySales 每日硬补 minSuggestQty,库存充足者也不再空产建议。
- **预警 V1 三规则→五规则(收口 TODO 槽位②)**:滞销 = 有可用库存但窗口内零动销(SLOW_MOVING);
  积压 = 日均>0 且 可用/日均 ≥ overstock-days(默认 90 天,OVERSTOCK);两规则各扫各的库存
  (单规则隔离纪律不因共用数据面而放松),聚一条 topN 明细;notify_type 词表三方同步。

## 改动
- docs/03 §7.1 定稿 + 01_schema_init.sql 建表(CREATE IF NOT EXISTS 幂等,已建库重跑即补建)+ notify_type
  COMMENT 扩 SLOW_MOVING/OVERSTOCK(已建库可选手工 ALTER,仅注释无功能影响)。
- erp-order:OrderSalesDaily 实体 + OrderSalesDailyMapper(XML:upsertWindow 单语句 INSERT…SELECT GROUP BY
  行别名 upsert;sumQtySince GROUP BY 合计)+ OrderSalesDailyService(rebuildWindow/sumQtyBySku,
  时间走注入 Clock)。
- erp-contract:SalesQueryApi(第五件,Map<Long,Integer> 返回,未记录 sku 按 0 兜底语义写进契约 javadoc)。
- erp-api:SalesQueryApiImpl(委托)+ ErpSalesProperties(erp.sales.*)+ SalesSnapshotJob(cron 01:00,
  赶在补货 02:00 前出当日数据;与 AI 两工作流错峰共用单线程调度器)+ yml 登记块。
- erp-ai:ErpAiProperties.Replenish 弃 assumedDailySales 改 salesWindowDays=30;ReplenishCalculateNode
  接 SalesQueryApi 重估公式 + 空集守卫;ErpAlertProperties 增 slowMovingDays/overstockDays;
  AlertEvent 增 TYPE_SLOW_MOVING/TYPE_OVERSTOCK;AlertEngine 增两规则(+scanInventoryStocked/soldBySku 共用件)。

## 坑
- **测试场景自洽性**:滞销测试里"sku1 卖 30/30 天但库存 500"同时命中积压(覆盖 500 天)——数据面功能
  彼此联动,造数时必须全规则自洽(销量×库存×阈值三算)。
- **erp-api 单模块 test 必带 -am**:~/.m2 的 erp-contract/erp-order 快照落后于工作区(同前日教训)。
- Mockito 默认对 Map 返回值给空 map:AlertEngineTest 里"没桩销量 = 全部零动销",存量测试因此批量收到
  SLOW_MOVING 事件——默认桩显式给"有动销且周转健康"的 map 让新规则静默,规则专测再覆盖。

## 验证
- 全 reactor 18 模块 `mvn -pl erp-order,erp-ai,erp-api -am test` BUILD SUCCESS:
  erp-ai 80(Calculate 重写 7 + AlertEngine 11)/ erp-api 70(新增 SalesSnapshotJob 4 + SalesQueryApiImpl 1)/
  erp-order 12(新增 OrderSalesDailyService 3),存量不回退。
- 单测合计 +15:OrderSalesDailyService 3(窗口委托/行映射 Number 兼容/短路不触库)+
  SalesSnapshotJob 4 + SalesQueryApiImpl 1 + ReplenishCalculateNode 重写 7(真实动销/分数速率取整/下限/
  零动销剔除/库存充足剔除/空集不触契约)+ AlertEngine +3(滞销命中/有动销排除/积压覆盖阈值)。

## 未尽
- 金额维列(选品/ACOS 数据面)随 #17 对标功能排期。
- inventory_snapshot_daily(库存趋势/周转报表)仍留草案,与销量面相互独立。
- 30 天外取消单不回刷(V1 已知边界,量大或偏差明显时再拉长窗口/改事件驱动)。
