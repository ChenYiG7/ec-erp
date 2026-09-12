# #19 周期利润口径实施计划书

> 已于 2026-09-11 主体实施,devlog 见 `docs/devlog/TODO19-周期利润口径.md`;
> **#32 周期校差算法已于 2026-09-12 落地(用户拍板授权 AI 实现,拍板与实施记录见同 devlog 补篇),
> 余量清零计划书归档**;真库验证 validate_profit_sql.py 12 项全绿,ProfitPeriodJob 默认开。

| 元信息 | 值 |
|---|---|
| TODO 条目 | #19 周期利润口径:结算单口径与订单口径差值校准(三口径第二层)+ 预估费用模型(平台费率表)+ 多币种折算完善 |
| 优先级 | P2(无外部依赖;结算拉取编排接线卡 #3 真凭证) |
| 前置依赖 | 无(settlement 域 V1 已投产);真实结算报告数据越多校差越准 |
| 目标一句话 | 在订单口径(小时级,已投产)之上落第二层"周期利润(结算单口径)":费率表→预估→结算回→校差闭环,并完善多币种 |
| 明确不做 | 第三层"财务利润(全费用分摊)"(头程/仓储费进分摊另见 first-mile-freight.md 与后续);汇率行情 API 自动抓取(仅预留 source 扩展);ACOS 面(卡 #20) |

## 一、背景与现状(2026-09-10 代码事实)

- **订单口径(第一层,已投产)**:`erp-finance` `ProfitQueryService` 纯读四方法:`page/summarize/listDailyTrend/listSkuProfitRank`;归集键=成本 `inventory_flow` OUT_SHIP 行经 `delivery_order_item` 按 order_item_id、佣金 `settlement_detail` fee_type=COMMISSION 按 `shop_id+order_item_id`("|"分隔)。**缺口纪律三计数**:`OrderProfitSummary.missingRateCount/costMissingCount/commissionMissingCount`,缺成本/缺汇率→NULL、仅缺佣金→毛利;全量 SQL 硬 `LIMIT 20000`(注释:量级增长落库方案随 V2 周期口径)。
- **结算域(V1,已投产)**:`SettlementService.saveUnifiedSettlement` 唯一写入口,勾稽 `Σ明细=totalAmount` 才 PARSED 否则 FAILED(可重拉覆盖),`uk_shop_settlement` 幂等;派生列 `feeSum()`/`transferSum()`;Amazon 翻译器 V2 flat file FeeType 八值归一。**erp-api 无结算拉取 Job**(javadoc:编排接线随真凭证拍板)。
- **汇率**:`ExchangeRateService.resolveRate(currency, businessTime)` 取 `quoted_at<=businessTime` 最近报价,CNY 短路 1,无报价返 null **禁猜**;写侧仅 MANUAL source。
- **表**:settlement_report / settlement_detail / exchange_rate / sku_cost_state。**无平台费率表、无周期利润落库表**。
- **docs 口径**:docs/02 §14 三口径=实时销售利润(订单口径,小时级)→周期利润(结算单口径)→财务利润(全费用分摊),先粗后细逐层校准;docs/02 §9 预估费用"结算报告未回按平台费率表先行估算,结算回后校差 ❌P2";docs/03 §6.1"V2 周期口径才落库校准;无结算数据→NULL,V1 禁费率猜算"。docs/10 §4 G4。

## 二、方案设计

### 2.1 周期利润落库(核心交付)

- 新域 `profit_period`(erp-finance 内):以 **结算报告期为聚合粒度**(拍板点①,备选自然月)。
- 聚合逻辑(纯 SQL 聚合 + Service 编排,复杂校差逻辑按铁律 1 处理):
  - 结算侧:`settlement_detail` 按报告期聚合 fee_type 分组金额(TRANSFER 回款/COMMISSION 佣金/FBA 系费用/其他费用);
  - 订单侧:同报告期窗口的订单口径利润(复用现有 ProfitQueryService 归集 SQL 思路,按 settle_report 的 posting 时间窗重算);
  - 校差行:结算收入 vs 订单收入差、结算佣金 vs 订单归集佣金差、其他费用合计——差异超容差(沿用 0.01 币种单位,同 RefundReconciliationService 口径)标记 `DIFF_FLAG`,差异计数**不静默归零**(延续缺口纪律)。
- 触发:结算报告落库(PARSED)后同步生成/刷新该期周期行 + `ProfitPeriodJob` 每日低频重算兜底(幂等 upsert)。

### 2.2 预估费用模型(费率表先行)

- 新表 `platform_fee_rate`(维度:platform + fee_type + 可选类目/站点 + 生效区间),wimoor profitcfg/referralfee 费率表族**只借鉴思路不抄结构**(docs/10 §6 对标纪律,若参考表结构须 devlog 记出处)。
- 估算规则:订单口径行缺 settlement_detail 佣金时,按费率表算预估佣金,行级 `ESTIMATED` 标志;结算回后以实际值覆盖并落差值到周期校差。**预估只算 COMMISSION 等有明确费率的费种,FBA 仓储类无费率不猜**(延续"禁猜"纪律)。
- 展示:现有利润行/看板加"预估"标识;周期报表展示 预估→实际 差值。

### 2.3 多币种折算完善

- 现状:本位币 CNY,resolveRate 已支持业务日回溯。完善项:
  1. 周期报表按结算原生币种聚合后折算,汇率快照记入周期行(`rate_used`),复核可追溯;
  2. 缺汇率行进 `missingRateCount` 同口径计数(已有字段,周期侧复用);
  3. exchange_rate 的 source 预留扩展(API 行情源 P3),本期不加。

### 2.4 结算拉取编排(挂接但默认关)

- `SettlementPullJob` 骨架:遍历 amazon 店铺 → `PlatformClient.pullSettlements`(契约已有,default 空实现)→ `saveUnifiedSettlement`;`erp.adapter.settlement-pull.enabled` 默认 false,#3 真凭证后开启。空转零噪音(同 ReportDigestJob 总开关先例)。

## 三、实施步骤

1. add-table skill:`platform_fee_rate` + `profit_period_report` 两表(草案见下)→ 八件套生成器出骨架。
2. 费率管理:Service + Controller(`/api/finance/fee-rates`,写侧 admin @PreAuthorize 同 ExchangeRateController 先例)+ 前端页面(add-page 生成器,tools/specs 写 spec)。
3. `ProfitPeriodService`:聚合 + 校差 + upsert(校差算法为复杂逻辑,执行时按铁律 1 处理——若执行会话是 AI,写 TODO(新编号)+指引登记 TODO.md)。
4. 预估接线:ProfitQueryService 佣金缺口处接费率表(ESTIMATED 标志进 OrderProfitRow——record 扩字段注意 openapi 快照同步 `pnpm api:sync`)。
5. 周期前端页:周期利润列表 + 期详情(订单口径 vs 结算口径对照)。
6. SettlementPullJob 骨架(默认关)+ LockService 抢锁 + MDC traceId(九 Job 同款)。
7. SQL 真库验证:扩展 `scripts/validate_profit_sql.py` 覆盖新聚合 SQL。

## 四、表结构草案(最终以 add-table skill 落正本)

```sql
CREATE TABLE IF NOT EXISTS platform_fee_rate (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  platform VARCHAR(32) NOT NULL COMMENT '平台(PlatformType)',
  fee_type VARCHAR(32) NOT NULL COMMENT '费种:COMMISSION/FBA_FULFILLMENT/... 对齐 settlement_detail.fee_type 词表',
  marketplace VARCHAR(32) NULL COMMENT '站点,空=全站点',
  category_path VARCHAR(255) NULL COMMENT '类目路径,空=全类目',
  rate DECIMAL(8,6) NOT NULL COMMENT '费率(如 0.150000)',
  eff_from DATE NOT NULL, eff_to DATE NULL COMMENT '生效区间,eff_to 空=长期',
  source VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/CRAWLED(预留)',
  remark VARCHAR(255) NULL,
  created_at/updated_at DATETIME, deleted BIGINT DEFAULT 0,
  UNIQUE KEY uk_fee_dim (platform, fee_type, marketplace, category_path, eff_from, deleted)
) COMMENT '平台费率表(#19 预估费用模型)';

CREATE TABLE IF NOT EXISTS profit_period_report (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  shop_id BIGINT NOT NULL,
  settlement_id BIGINT NOT NULL COMMENT '关联 settlement_report',
  period_start DATETIME NOT NULL, period_end DATETIME NOT NULL,
  currency CHAR(3) NOT NULL,
  rate_used DECIMAL(12,8) NULL COMMENT '折算 CNY 汇率快照',
  cny_rate_missing TINYINT(1) DEFAULT 0 COMMENT '缺汇率置 1(缺口纪律)',
  order_income DECIMAL(12,4) NULL COMMENT '订单口径收入(CNY)',
  settle_income DECIMAL(12,4) NULL COMMENT '结算口径 TRANSFER(CNY)',
  settle_commission/fba_fee/other_fee DECIMAL(12,4) NULL COMMENT '结算侧费用分项(CNY)',
  order_commission DECIMAL(12,4) NULL COMMENT '订单口径归集佣金(CNY)',
  order_profit DECIMAL(12,4) NULL COMMENT '订单口径利润(CNY)',
  diff_income/diff_commission DECIMAL(12,4) NULL COMMENT '校差(超容差置 diff_flag)',
  diff_flag TINYINT(1) DEFAULT 0, diff_remark VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OK' COMMENT 'OK/DIFF/RATE_MISSING',
  created_at/updated_at DATETIME,
  UNIQUE KEY uk_shop_settlement (shop_id, settlement_id)
) COMMENT '周期利润报告(#19 三口径第二层,结算期粒度)';
```

## 五、验收标准

- `mvn -DskipTests compile` + 全量单测绿;校差算法单测(平/不平/缺汇率三态)覆盖。
- `scripts/validate_profit_sql.py` 真库跑通(新聚合 SQL 全覆盖);**改 SQL 当天必须重跑**(TODO 红线:单测 mock 测不出 XML 语法错)。
- 端到端(用开发库既有 settlement 样本):造一期数据 → 周期行生成 → 手改一行明细金额 → 重算 → diff_flag=1 且 diff_income 精确;缺汇率样本 → status=RATE_MISSING 且 cny_rate_missing=1。
- 前端费率页 + 周期利润页门禁四件全绿;openapi.json 快照随契约提交。
- SettlementPullJob 默认关不空转;开 false→true 不改代码可启。

## 六、红线提醒

- SQL 兼容红线:聚合/窗口 SQL 一律 MySQL 9.7.2 原生形态;INSERT...SELECT 行别名不支持(1064),用源表/派生表别名;日期轴类不进 SQL(Service 层逐日,#22 先例)。
- 金额 DECIMAL(12,4)/汇率 DECIMAL(12,8);禁浮点;禁费率猜算(docs/03 §6.1)——无费率不估算。
- 缺口纪律:任何缺口计数禁止静默归零(G4 教训);LIMIT 20000 防御随落库方案复核,删防御须留说明。
- 分层:Controller 不直连 Mapper(erp-finance 已整域收口);系统写入表(profit_period_report/platform_fee_rate 由系统与人工写)对外只读按 docs/07 §2.1 判定。
- 契约:若 AI 工具/跨域需要周期数据,走 erp-contract 新契约,不横向依赖。

## 七、交接边界(必须人工拍板)

1. 周期粒度=结算报告期(推荐,天然对账)vs 自然月。
2. 费率表维度深度(平台×费种起步,类目/站点列预留是否本期启用)。
3. 周期报表是否进报表中心(erp-report)还是财务域自营页面(计划默认财务域自营)。
4. 校差容差值(沿用 0.01)与 DIFF 的处理流程(人工复核 or 自动重拉)。
