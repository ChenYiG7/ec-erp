# 收付款/回款实施计划书

> 已于 2026-09-11 主体实施(#31),devlog 见 `docs/devlog/TODO31-收付款回款.md`;
> 余量(其他环境存量库迁移脚本 + 拍板挂起项)见 TODO.md 对应条目。

| 元信息 | 值 |
|---|---|
| TODO 条目 | 收付款/回款:采购付款登记、平台回款记录、资金流追踪(docs/02 二期规划) |
| 优先级 | P2(无外部依赖) |
| 前置依赖 | 无;平台回款自动派生可选衔接 settlement 域(V1 已投产) |
| 目标一句话 | 统一资金流水 payment_record(+采购分摊 alloc),采购单挂应付/已付视图,结算回款自动/手动登记,形成资金流查询面 |
| 明确不做 | 银行/支付网关 API 对接;付款审批流(登记制起步,拍板点);供应商报价管理(docs/02 §5 另列);利润口径接入(费用面归 #19 第三层) |

## 一、背景与现状(2026-09-10 代码事实)

- **采购侧**:`PurchaseOrder`(poNo/supplierId/warehouseId/status/totalAmount 本位币/remark/createdBy)——**无已付金额、无付款状态**;`Supplier` 无账期字段(settleType 是唯一结算相关列)。状态机动作:save/update(DRAFT)/audit(DRAFT→AUDITED 占在途)/close(释在途)/receiveQuantities(confirm 三步核销)。Controller `/api/purchase/orders` 全套 + admin 审核点。
- **回款侧**唯一资产:`settlement_report.transfer_amount`(ΣTRANSFER 明细,即"回款净额"雏形)+ `settlement_detail` fee_type=TRANSFER 行 + `UnifiedSettlement.depositDate`(预计打款日,V1 仅留档)。
- **全仓 payment/付款登记/资金流/回款记录零代码零表**。docs/02 §9 收付款行 ❌P2;§5 供应商报价/账期 ❌。
- 惯例参考:金额 DECIMAL(12,4)、逻辑删除、系统写入表对外只读判定、单号生成(poNo 先例)。

## 二、方案设计

### 2.1 统一资金流水模型(核心)

- `payment_record` 一张流水表承载收付两向:`direction`(EXPENSE 付款/INCOME 回款)+ `biz_type`(PURCHASE_PAYMENT 采购付款 / SETTLEMENT_RECEIPT 结算回款 / MANUAL_ADJUST 手工调整)+ `party_type/party_id`(SUPPLIER/PLATFORM)+ 金额原币 + currency + exchange_rate(非 CNY 落库时 resolveRate 冻结,无报价拦) + paid_at + method(银行转账/支付宝/平台打款…) + ref_type/ref_id(源单据)+ status(NORMAL/VOIDED 作废)。
- **一单多付/一付多单**:分摊表 `payment_alloc`(payment_id + alloc_biz_type=PURCHASE + alloc_biz_id=po_id + amount)。采购单已付 = Σalloc(查询时聚合,**不冗余存储**,防漂移;量级上来再评估冗余列+事件刷新,拍板点①)。
- 作废制:登记错误走 status=VOIDED(保留痕迹),**禁物理删**;VOIDED 时校验未被他处引用(分摊随作废失效)。

### 2.2 采购付款登记(erp-purchase)

- 采购单详情新增资金视图:totalAmount(本位币) vs Σalloc 已付 = 待付;付款进度随状态联动提示(AUDITED 后才允许登记付款——未审核单不可付,守卫)。
- 端点:付款登记/作废走 payment 域统一入口(跨采购域引用经 erp-contract?采购单存在性校验用既有只读契约 PurchaseQueryApi 扩展 or 直接同模块——**落点拍板点②**:payment_record 域建议落 `erp-finance`(资金域归属),对采购单的引用校验走 erp-contract 契约(存在性/金额只读方法),实现收口 erp-api,符合模块纪律。

### 2.3 平台回款登记(erp-finance,与结算域衔接)

- 自动派生(推荐):settlement_report 落为 PARSED 且 transfer_amount>0 时,同事务派生一条 INCOME/SETTLEMENT_RECEIPT 流水(ref=settlement_report.id,uk `ref_type+ref_id` 幂等,重拉覆盖报告时流水同步刷新);金额=transfer_amount,币种=报告币种,汇率=报告 rate 快照链路(resolveRate 回溯)。
- 手工补录:派生遗漏/非结算回款(如其他打款)走 MANUAL 登记端点。
- 拍板点③:自动派生(推荐,数据已就绪)vs 纯手动登记。

### 2.4 资金流追踪(查询面)

- payment_record 本身即流水账;查询面三条:
  1. 流水列表(方向/类型/往来方/期间过滤,Excel 导出走 erp-report 惯例可选);
  2. 往来方视图:供应商(应付/已付/待付,按 Σalloc)与平台(回款累计,按 settle 期间);
  3. 汇总:期间收付净额(CNY 折算,缺汇率行计数不静默——缺口纪律同 #19)。
- Supplier 顺手补 `settle_days`(账期天数)列(V1 仅展示,账期到期提醒留 TODO)。

## 三、实施步骤

1. add-table skill:payment_record + payment_alloc + supplier 加列——三方同步。
2. payment 域八件套(erp-finance 新增 payment 域,add-domain skill)+ 作废守卫 + 分摊勾稽(Σalloc ≤ payment_amount,允许部分分摊挂账)。
3. 契约扩容:erp-contract 加采购单只读校验方法(存在性/总金额/状态,全 record)或复用 PurchaseQueryApi 扩展;erp-api 实现。
4. 采购付款登记端点 + 采购单详情资金视图(前端列:已付/待付)。
5. 结算回款派生:SettlementService.saveUnifiedSettlement 落 PARSED 后同事务派生(uk 幂等);存量历史报告补派生的一次性脚本(进 scripts/archive 惯例)。
6. 前端:资金流水列表页 + 付款登记表单 + 采购单详情资金块(add-page;生成器出列表,登记表单手写组件)。
7. 真库验证:分摊/派生 SQL 进 validate 脚本家族;端到端(采购单→付款→待付归零;结算样本→回款流水)。

## 四、表结构草案(最终以 add-table skill 落正本)

```sql
CREATE TABLE IF NOT EXISTS payment_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  payment_no VARCHAR(32) NOT NULL COMMENT '流水号 PAY+yyyyMMdd+seq',
  direction VARCHAR(8) NOT NULL COMMENT 'EXPENSE/INCOME',
  biz_type VARCHAR(32) NOT NULL COMMENT 'PURCHASE_PAYMENT/SETTLEMENT_RECEIPT/MANUAL_ADJUST',
  party_type VARCHAR(16) NOT NULL COMMENT 'SUPPLIER/PLATFORM/OTHER',
  party_id BIGINT NULL COMMENT 'supplier_id / shop_id',
  amount DECIMAL(12,4) NOT NULL COMMENT '原币金额',
  currency CHAR(3) NOT NULL DEFAULT 'CNY',
  exchange_rate DECIMAL(12,8) NULL, amount_cny DECIMAL(12,4) NULL COMMENT '折算,缺汇率为空(计数不静默)',
  paid_at DATETIME NOT NULL, method VARCHAR(32) NULL,
  ref_type VARCHAR(32) NULL, ref_id BIGINT NULL COMMENT '源单据(settlement_report 等)',
  status VARCHAR(8) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/VOIDED',
  remark VARCHAR(255) NULL, created_by BIGINT NULL, created_at/updated_at DATETIME, deleted BIGINT DEFAULT 0,
  UNIQUE KEY uk_payment_no (payment_no),
  UNIQUE KEY uk_ref (ref_type, ref_id, deleted) COMMENT '派生幂等(SETTLEMENT_RECEIPT 用)',
  KEY idx_party (party_type, party_id, paid_at)
) COMMENT '资金流水(收付款/回款)';

-- payment_alloc(id, payment_id, alloc_biz_type VARCHAR(16) 'PURCHASE', alloc_biz_id BIGINT,
--   amount DECIMAL(12,4), uk(payment_id, alloc_biz_type, alloc_biz_id), KEY idx_alloc (alloc_biz_type, alloc_biz_id))
ALTER TABLE supplier ADD COLUMN settle_days INT NULL COMMENT '账期天数(展示,V1 不做到期提醒)';
```

## 五、验收标准

- 采购付款闭环:登记付款→分摊到采购单→采购单待付=总-Σalloc 精确;超额分摊被拦;作废后待付还原。
- 回款幂等:同一结算报告重拉(saveUnifiedSettlement 覆盖)不重复派生流水(uk 守卫);历史补派生脚本幂等。
- 多币种:非 CNY 回款按报告期 resolveRate 折算,缺汇率行进缺口计数(前端可见"折算缺失"标记)。
- 真库 validate 通过;前端门禁四件;mvn 绿。

## 六、红线提醒

- 金额 DECIMAL(12,4)/汇率 DECIMAL(12,8),禁浮点(前端 string 直存直显 docs/09 §6)。
- 派生流水同事务(结算落库与回款流水原子);禁 AFTER_COMMIT 后补写(资金数据不追赶记)。
- 契约方向:payment 域(erfinance)→采购域引用走契约禁横向依赖;@Lazy 断构造环惯例。
- 逻辑删除+作废双状态语义在文档与代码注释写清(查流水默认滤 VOIDED,对账口径含 VOIDED 注明)。
- SQL:Σalloc 聚合与 uk_ref 幂等按 9.7.2 形态,真库验证。

## 七、交接边界

1. 已付金额冗余列(查询聚合,推荐)vs 冗余+事件刷新——数据量判断后拍板。
2. payment 域落 erp-finance(推荐)vs erp-purchase。
3. 回款自动派生(推荐)vs 手动。
4. 付款审批流需求确认(登记制起步是否满足)。
5. 账期到期提醒(P3 预警引擎规则扩容候选,不随本期)。
