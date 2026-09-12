# 头程运费分摊实施计划书

> 已于 2026-09-11 主体实施(#33),devlog 见 `docs/devlog/TODO33-头程运费分摊.md`;
> 余量(其他环境存量库重放/四期明确不做项)见 TODO.md 对应条目。

| 元信息 | 值 |
|---|---|
| TODO 条目 | 头程运费分摊(P2,docs/02 §6 三期规划提前项;利润口径第三层组件) |
| 优先级 | P2(无外部依赖) |
| 前置依赖 | 无(product_sku 重量/体积列在本计划内补);与 fba-shipment.md 的装箱结构存在同构拍板点 |
| 目标一句话 | 头程发货单(装箱单)数据面 + 运费按策略分摊到 SKU,作为费用行进入利润口径第三层 |
| 明确不做 | 箱唛打印/CLODOP(随国内 adapter,#17);头程运费资本化进移动加权成本账(拍板点,默认不做);物流商 API 取价(四期) |

## 一、背景与现状(2026-09-10 代码事实)

- **接近零资产**:全仓无头程/装箱/分摊任何代码与表。
- 可复用挂点:
  - `InventoryCostService.apply()` IN_PURCHASE 进账可传 unit_cost(缺价按加权价暂估)——**若拍板走资本化路线这里是唯一挂钩点**(默认不走,见 2.1);
  - `warehouse.wh_type` 已有 OVERSEAS/FBA 仓型 + country 列,海外仓档案可直接用;
  - `InventoryService.transfer()` 跨仓原语已备(国内仓→海外仓移库);
  - `ExchangeRateService.resolveRate` 汇率回溯(运费外币折算);
  - docs/02 §6 头程发行行"国内仓→海外仓/FBA、装箱单、箱唛打印,三期 ❌(头程运费分摊列 TODO P2)";docs/10 §2.8:头程运费分摊 ❌ / wimoor ✅ / 领星 ✅ 智能分摊 / 优麦云 🟡;docs/10 §4 G4 把头程分摊列入财务深水区。

## 二、方案设计

### 2.1 路线拍板(核心,必须人工确认):分摊去向

- **方案 B(推荐,本计划按 B 展开)**:运费作为**期间费用行**进入利润第三层口径——不触碰 `sku_cost_state` 移动加权账,无成本账重放风险;分摊结果落到独立表,由后续"财务利润"口径聚合。
- 方案 A(资本化,暂缓):运费摊进 IN_PURCHASE 的 unit_cost,加权均价永久抬高,OUT_SHIP 结转时自动进成本。**影响成本账语义**(历史重放校验、快照对账全部要重核),docs/03 §7.2 未涉及,不建议本期做。
- 拍板记录落 devlog;若日后翻案走 A,先补成本账影响评估。

### 2.2 单据模型(三表)

- `first_leg_shipment` 头程发货单:status 状态机 `DRAFT→BOXED→SHIPPED→ALLOCATED→CLOSED`(CANCELED 旁路);from_warehouse_id(国内仓)→to_warehouse_id(OVERSEAS/FBA 仓)、carrier、waybill_no、charge_weight(计费重)、volume_weight、freight_amount(DECIMAL(12,4))+currency+exchange_rate(可空,折算走 resolveRate)、allocate_strategy。
- `first_leg_box` 箱:shipment_id、box_no、weight、length/width/height。
- `first_leg_box_item` 箱内件:box_id、sku_id、quantity。
- 分摊结果:`first_leg_alloc`(shipment_id、sku_id、alloc_amount CNY、alloc_base 快照、strategy)——**分摊一旦确认不可重算覆盖,重算=作废重开**(防利润口径漂移)。

### 2.3 分摊策略(复杂逻辑,铁律 1)

三策略:按数量 / 按重量(箱内件 qty × product_sku.weight,毛重优先箱重) / 按金额(box_item 金额 = qty × 最近采购价或移动加权价)。默认按重量(跨境物流计费惯例)。
- 分母兜底:重量/金额全 0 时降级按数量并留 diff remark,不静默。
- 执行约束:**分摊算法属复杂逻辑**——实现会话按铁律 1 写 `TODO(新编号)` + 实现指引登记 TODO.md 由人工拍板补齐,或人工确认后由执行模型实现;Σalloc 必须 == freight_amount(容差 0.01),不平整单失败不落库(同结算勾稽纪律)。

### 2.4 与利润口径衔接

- 本计划交付到 `first_leg_alloc` + 查询面(SKU 维度头程费用汇总);接入第三层"财务利润"报表属后续(依赖 19-profit-caliber.md 第二层先落地)。
- `product_sku` 加 `weight_g`/`volume_mm` 三列(或长宽高),为分摊按重量与 FBA 装箱共同铺路(docs/02 §2 跨境商品属性 ❌ 的最小集)。

## 三、实施步骤

1. add-table skill:product_sku 加重量体积列 + first_leg 四表(三方同步)。
2. 头程域八件套(erp-warehouse?**模块归属拍板点**:货权在仓、费用在财务,推荐落 `erp-finance`(费用域)单据,仓库维度只作外键;备选 erp-warehouse)。
3. 状态机 + 守卫(条件更新即守卫,状态机守卫测试生成器出四类用例;spec 文件留 testgen)。
4. 装箱录入(手工 CRUD)→ SHIPPED 录运费 → 分摊(策略 + 勾稽 Σ=运费)→ ALLOCATED。
5. 跨仓动账:SHIPPED 时按拍板决定是否触发 `InventoryService.transfer()`(国内仓→海外仓实移库;若海外仓库存暂不track 可不动账,拍板点)。
6. 前端:头程发货单列表/详情/装箱/分摊查看(add-page)。
7. SQL 真库验证(分摊聚合 SQL 进 validate 家族脚本)。

## 四、表结构草案(最终以 add-table skill 落正本)

```sql
CREATE TABLE IF NOT EXISTS first_leg_shipment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  shipment_no VARCHAR(32) NOT NULL COMMENT '头程单号 FL+yyyyMMdd+seq',
  from_warehouse_id BIGINT NOT NULL, to_warehouse_id BIGINT NOT NULL,
  carrier VARCHAR(64) NULL, waybill_no VARCHAR(64) NULL,
  charge_weight DECIMAL(12,3) NULL COMMENT '计费重 kg', volume_weight DECIMAL(12,3) NULL,
  freight_amount DECIMAL(12,4) NOT NULL COMMENT '运费原币', currency CHAR(3) NOT NULL DEFAULT 'CNY',
  exchange_rate DECIMAL(12,8) NULL COMMENT '录入即冻结;空=落库时 resolveRate',
  allocate_strategy VARCHAR(16) NOT NULL DEFAULT 'WEIGHT' COMMENT 'QTY/WEIGHT/AMOUNT',
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/BOXED/SHIPPED/ALLOCATED/CLOSED/CANCELED',
  remark VARCHAR(255) NULL, created_by BIGINT NULL, created_at/updated_at DATETIME, deleted BIGINT DEFAULT 0,
  UNIQUE KEY uk_shipment_no (shipment_no)
) COMMENT '头程发货单(头程运费分摊)';

-- first_leg_box(id, shipment_id, box_no, weight DECIMAL(12,3), length/width/height INT cm, uk(shipment_id,box_no))
-- first_leg_box_item(id, box_id, sku_id, quantity, uk(box_id,sku_id))
-- first_leg_alloc(id, shipment_id, sku_id, alloc_amount DECIMAL(12,4) CNY, alloc_base DECIMAL(12,3) 分摊基数快照,
--   strategy VARCHAR(16), uk(shipment_id,sku_id))
```

## 五、验收标准

- 状态机守卫测试四类用例绿(条件更新即守卫);分摊勾稽:三策略各一份数据集 Σalloc==运费;全 0 分母降级按数量有 remark。
- 真库:分摊聚合 SQL 验证脚本通过;重复分摊请求被状态机拦(非 SHIPPED 不可分摊、ALLOCATED 不可重算)。
- 汇率:freight 币种非 CNY 且无报价 → 落库被拦(禁猜),录入 exchange_rate 后可过。
- 前端页面门禁四件绿;mvn 编译绿。

## 六、红线提醒

- 若翻案走资本化 A:动成本账必须同事务、FOR UPDATE 锁 state 行、历史重放校验先评估,**禁旁路 update sku_cost_state**。
- 库存动账(如做)唯一入口 `InventoryService.change()`,禁旁路。
- 金额 DECIMAL(12,4)/汇率 DECIMAL(12,8);resolveRate 无报价返 null 禁猜。
- 逻辑删除(deleted BIGINT 惯例);join 不滤已删的对账口径按 erp-report 先例明确写注释。

## 七、交接边界

1. 路线 B(期间费用,推荐)vs A(资本化进成本账)——devlog 拍板。
2. 模块归属 erp-finance(推荐)vs erp-warehouse。
3. SHIPPED 是否联动跨仓动账(取决于海外仓/FBA 库存是否本期 track,与 fba-shipment.md 联动拍板)。
4. 分摊算法实现方式(铁律 1:TODO 槽位人工补 or 人工确认后实现)。
