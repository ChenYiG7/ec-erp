# 仓内作业(盘点单/调拨单域/库位批次评估)实施计划书

> **已于 2026-09-11 实施**,devlog 见 `docs/devlog/`(#30 仓内作业);余量见 TODO.md #30。
> 实施口径:盘点单六态状态机(DRAFT→COUNTING→PENDING_ADJUST→ADJUSTED→CLOSED,CANCELED 旁路)+
> 建单快照/确认时点双口径 + 差异 ADJUST 动账(biz_type=STOCKTAKE);调拨单 DRAFT→CONFIRMED/CANCELED
> 确认即达两腿动账(transfer() biz_type 由 INVENTORY_TRANSFER 收口为 TRANSFER_ORDER);库位/批次评估结论=不做。

| 元信息 | 值 |
|---|---|
| TODO 条目 | 仓内作业:盘点单、调拨单域(transfer() 原语已备)、库位/批次评估(二期规划) |
| 优先级 | P2(docs/10 §4 G9 标 P3,TODO.md 列 P2——以 TODO.md 为准,docs/10 已注明口径差) |
| 前置依赖 | 无 |
| 目标一句话 | 盘点单(账实核对→差异 ADJUST 动账)与调拨单(单据化 transfer())两个作业域落地;库位/批次给出不做的评估结论 |
| 明确不做 | 库位/批次管理(见 2.4,结论:不做);库存质量维度良品/残品分账(三期);盘点冻结(停机动账,拍板默认不做) |

## 一、背景与现状(2026-09-10 代码事实)

- **动账底座**:`InventoryService.change(InventoryFlow)→Long` 数量列唯一改动入口,同事务写 inventory_flow,存量行原子 UPDATE(守卫下推 WHERE),首建撞 uk_sku_wh 捕 DuplicateKeyException 重试。
- **FlowOps 八值矩阵**(私有枚举,词表三方同步 FlowOps ↔ `erp-contract/InventoryConsts` ↔ DDL 注释):IN_TRANSIT / IN_PURCHASE / IN_RETURN / **ADJUST(在库+Δ、可用+Δ,守卫可用+Δ≥0)** / TRANSFER_OUT / TRANSFER_IN / LOCK_SHIP / OUT_SHIP。
- **调拨原语**:`transfer(skuId, fromWh, toWh, qty, remark, createdBy)` 同事务 TRANSFER_OUT(负)+TRANSFER_IN(正),biz_type 硬编码 `INVENTORY_TRANSFER`(常量注释明示"调拨单据域立项后由其常量收口")。
- **ADJUST 无人工入口**(Controller 无 adjust 端点)——盘点差异调整的直接通路。
- **零单据资产**:全仓无 stocktake/盘点、无调拨单任何代码与表;inventory/inventory_flow 无 location/lot 列;uk 粒度=sku+warehouse。erp-warehouse 仅仓库档案八件套。
- docs/02 §6:盘点单/调拨单二期 ❌ P2;库位批次"评估"。docs/10 G9"仓储仅档案级非作业级"。

## 二、方案设计

### 2.1 盘点单域(erp-inventory)

- 两表:`stocktake_order`(单头)+ `stocktake_item`(行)。状态机:`DRAFT→COUNTING→PENDING_ADJUST→ADJUSTED→CLOSED`(CANCELED 旁路;**只有 ADJUSTED 可达 CLOSED**,保证差异必处理或明确放弃)。
- 流程与数据纪律:
  1. 建单(DRAFT):选仓库 + 盘点范围(全仓 or SKU 集,拍板点①默认支持两种);**建单即快照账面四量**进 stocktake_item.book_qty(快照时点语义,防运行中动账污染——明细行记 snapshot_at);
  2. 录实盘(COUNTING):逐行填 counted_qty,后端算 diff_qty=counted-book;
  3. 生成调整(PENDING_ADJUST→ADJUSTED):**复合事务**逐行调 `InventoryService.change()`(flow_type=ADJUST,biz_type=`STOCKTAKE`,biz_id=盘点单 id,remark 带盘点单号);**本事务内不冻结、不复核建单后动账**(拍板点②:非冻结盘点采用"确认时点账面 vs 实盘"口径——即 ADJUST 用**确认时刻**的账面重算 diff,而非建单快照差;若确认时账面又变了,以确认时点为准并记 remark 提示)。行级 Δ 会导致可用不足(守卫拒)时**整单失败回滚**并提示差异行(数据异常先查,不静默放行)。
- 成本联动:ADJUST 正负行随 `change()` 同事务进 `InventoryCostService`(CostOps 策略分派)。**盘盈入账成本口径是拍板点③**(建议:盘盈按当前移动加权价入、盘亏按当前加权价结转——与 OUT_SHIP 同口径,避免成本账两套语义)。

### 2.2 调拨单域(erp-inventory)

- 两表:`transfer_order`(DRAFT→CONFIRMED/CANCELED)+ `transfer_order_item`(sku_id/quantity/状态)。V1=**确认即达**(无在途账):CONFIRM 复合事务逐行调 `InventoryService.transfer()` 原语;在途模式(OUT 占用→到货 IN)留 TODO 拍板(拍板点④,需求来自多仓地理分离时才硬)。
- 收口预留:`transfer()` 内 biz_type 常量 `INVENTORY_TRANSFER` 改由调拨单域常量 `TRANSFER_ORDER` 收口(注释预告过),历史流水 biz_type 不改写(只换新流水的常量,查询兼容两值)。
- 守卫:CONFIRMED 幂等(重复确认条件更新拦)、行数>0、from≠to、qty>0。

### 2.3 词表与契约

- biz_type 新常量 `STOCKTAKE`/`TRANSFER_ORDER` 进 `erp-contract/InventoryConsts`(词表三方同步:契约常量 ↔ FlowOps 注释 ↔ DDL 注释一次改齐,防漂移)。
- flow_type 不新增(复用 ADJUST/TRANSFER_*,避免词表扩容牵动全链);若拍板要独立 STOCKTAKE flow_type(报表区分度),走 add-table+词表三方变更,默认不做。

### 2.4 库位/批次评估(结论,不开工)

- **结论:V1/V2 不做。** 理由:inventory uk=sku+warehouse、四量模型、移动加权成本账、日快照、FlowOps 全链都建立在"仓级粒度"上,引入库位/批次=动 uk 粒度,牵一动四(成本账/快照/对账/前端);当前无批次效期合规压力。
- 触发点登记:进口商品效期合规、库龄精细化报表(docs/10 已有库龄预警 P3)、多库位拣货效率瓶颈——任一成为真实痛点再立项,届时按 add-domain 全域设计。

## 三、实施步骤

1. add-table skill:盘点两表 + 调拨两表(§四草案)+ InventoryConsts 词表两常量。
2. 盘点域八件套(add-domain skill:erp-inventory 新增 stocktake 域)+ 状态机 + 守卫测试生成器 spec(testgen)。
3. 盘点确认复合事务(逐行 change()+成本联动;复杂逻辑按铁律 1:AI 执行会话留 TODO 槽位或人工确认后实现)。
4. 调拨域八件套 + CONFIRM 复合事务(逐行 transfer() 原语)+ biz_type 常量收口。
5. 前端:盘点单列表/详情/录实盘、调拨单列表/详情(add-page 两套 spec;盘点录实盘行编辑为生成器外的定制组件)。
6. 真库验证:动账 SQL 与守卫条件真库回归(validate 脚本家族按需新增 validate_inventory_sql.py);盘点确认后 inventory/inventory_flow/sku_cost_state 三方对账断言。
7. devlog:盘点口径(确认时点账面)+ 盘盈成本口径两拍板。

## 四、表结构草案(最终以 add-table skill 落正本)

```sql
CREATE TABLE IF NOT EXISTS stocktake_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  stocktake_no VARCHAR(32) NOT NULL COMMENT '盘点单号 ST+yyyyMMdd+seq',
  warehouse_id BIGINT NOT NULL,
  scope_type VARCHAR(16) NOT NULL DEFAULT 'ALL' COMMENT 'ALL/SKU_SET',
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/COUNTING/PENDING_ADJUST/ADJUSTED/CLOSED/CANCELED',
  remark VARCHAR(255) NULL, created_by BIGINT NULL, confirmed_by BIGINT NULL, confirmed_at DATETIME NULL,
  created_at/updated_at DATETIME, deleted BIGINT DEFAULT 0,
  UNIQUE KEY uk_stocktake_no (stocktake_no)
) COMMENT '盘点单(仓内作业)';
-- stocktake_item(id, stocktake_id, sku_id, book_qty INT 建单快照, snapshot_at DATETIME,
--   counted_qty INT NULL, diff_qty INT NULL(生成时算), adjust_flow_id BIGINT NULL 回填 change() 流水,
--   uk(stocktake_id, sku_id))

CREATE TABLE IF NOT EXISTS transfer_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  transfer_no VARCHAR(32) NOT NULL COMMENT '调拨单号 TR+yyyyMMdd+seq',
  from_warehouse_id BIGINT NOT NULL, to_warehouse_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/CONFIRMED/CANCELED',
  remark/created_by/created_at/updated_at/deleted,
  UNIQUE KEY uk_transfer_no (transfer_no)
) COMMENT '调拨单(仓内作业,V1 确认即达)';
-- transfer_order_item(id, transfer_id, sku_id, quantity, uk(transfer_id,sku_id))
```

## 五、验收标准

- 盘点全链真库:建单快照→录实盘→确认→inventory 四量正确位移、inventory_flow 出 ADJUST 行(biz_type=STOCKTAKE)、sku_cost_state 移动加权按拍板口径推进;可用不足整单回滚。
- 调拨:CONFIRM 后 from/to 两仓四量镜像变化,流水两条(TRANSFER_OUT/IN)biz_id 同指调拨单;重复确认被拦。
- 守卫测试四类用例绿(两状态机各一套);守卫下推 WHERE 真库验证(非 mock)。
- 前端两套页面门禁四件绿;mvn 全量编译绿。

## 六、红线提醒

- **动账唯一入口 change()**,盘点/调拨一律经它,禁旁路 update inventory;成本账随 change() 同事务,禁单独动 sku_cost_state。
- 复合事务边界:确认动作单事务原子(部分行成功=最危险态);事务内禁远程/消息调用。
- 词表三方同步一次改齐(InventoryConsts↔FlowOps↔DDL 注释),防 8 值矩阵漂移。
- SQL 红线:确认聚合/批量查询按 9.7.2 形态;LambdaQueryWrapper in()/set() 单测急切解析坑(docs/07 §10)。
- uk 幂等:单号 uk+条件更新双保险;deleted 逻辑删除惯例。

## 七、交接边界

1. 盘点范围(ALL/SKU_SET)与是否需要按库区圈定(库位不做,只能 SKU 集圈定)。
2. 非冻结盘点的确认时点口径(推荐)vs 建单快照口径——devlog 拍板。
3. 盘盈成本入账口径(推荐当前加权价)。
4. 调拨在途模式(V1 确认即达)的延后确认。
5. 库位/批次"不做"结论的确认(写入 TODO.md 对应条目收口)。
