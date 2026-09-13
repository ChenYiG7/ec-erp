# FBA Shipment 实施计划书

> **已于 2026-09-12 实施(V1 内部数据面;实施偏差与拍板细节见本文件头部标注与 TODO.md FBA 条目;
> V2 SP-API Inbound 客户端(2026-09-12 脱机)见 `docs/devlog/2026-09-12-SP-API外部依赖项预做收口.md`)**。
> 实施偏差:①新增 `fba_shipment_item` 计划行表(计划书 DDL 草案四表 → 五表——「Σbox_item=计划量」
> 装箱勾稽必须有计划量存储位);②FBA 目的仓不校验存在性(V1 仓侧不入账,warehouse_id 仅指国内发货仓);
> ③店铺存在性后端校验**已于 2026-09-13 接线**(ShopQueryApi.getShop 单店视图方法随 #6 tools 已存在,
>   FbaShipmentService save/update 双入口 validateShop(同 ManualOrderService 口径仅存在性,停用店铺不拦
>   既有履约),单测 2 例(假店铺建/改双拦+零写入断言)+ 真机冒烟全过);余量与拍板记录见 TODO.md FBA 条目与 devlog。

| 元信息 | 值 |
|---|---|
| TODO 条目 | FBA Shipment:发货计划生成/装箱信息/与平台对账(docs/02 三期规划,TODO 列 P2"无硬阻塞可先行") |
| 优先级 | P2(**口径冲突已按内部数据面方案消解**,见 2.1) |
| 前置依赖 | 无(V1 内部数据面零外部依赖);SP-API Inbound 集成卡 #3 真凭证 |
| 目标一句话 | FBA 发货单(计划→装箱→发出→平台收货对账)内部单据域落地,SP-API Inbound 客户端留凭证槽位 |
| 明确不做 | FBA 库存全量跟踪(docs/02 §4"海外仓/FBA 库存随履约形态落地",本期只做对账面);箱唛打印(随头程/国内域);FBA 费用抓取(卡 Finances 数据,进 #19/结算域) |

## 一、背景与现状(2026-09-10 代码事实)

- **FBA 现状=全链路跳过**:拉单照常入库(`shop_order.fulfillment_channel=FBA`),`ShipmentSyncService` L162 FBA 不回传不记 pull_log;`DeliveryOrderService` L326 FBA 订单拦建发货单("仅卖家自履约订单可创建发货单");docs/03 L193 口径"FBA 订单正常入库,不产生发货单,只参与对账与报表"。
- **预留位已挖好**:`delivery_order.type` 枚举预留 FBA/OVERSEAS;`warehouse.wh_type` 有 SELF/FBA/OVERSEAS/VIRTUAL;`PlatformClient.uploadTracking` javadoc 明示 FBA/海外仓平台自履约不回传。
- **SP-API 客户端面**:仅 Orders/Reports/Finances 三客户端,**无 Fulfillment Inbound 客户端**(createInboundShipmentPlan/putTransportContent 缺位)。
- **对账先例可复制**:`RefundReconciliationService`(售后侧 vs 结算侧 diff 三分类:AMOUNT_MISMATCH 容差 0.01/MISSING/CURRENCY_MISMATCH,纯读不改表)+ `RefundReconciliationJob`(每日一扫,existsRecent 静默期,pushAllUsers 扇出)。
- **依赖缺口**:`product_sku` 无重量/体积列(docs/02 §2 跨境商品属性 ❌)——装箱计费重的数据基础,first-mile-freight.md 同样依赖,两计划共享该加列。
- **口径冲突登记**:docs/10 §2.1 矩阵把 FBA Shipment 标"四期规划",§5/TODO.md 列 P2——本计划按 TODO.md P2 口径执行,内部数据面先行。

## 二、方案设计

### 2.1 范围分两期(消解口径冲突)

- **V1(本计划,无凭证可做)**:内部单据域——FBA 发货计划单(手建:选店/站点/目标 FBA 仓/SKU 清单)→ 装箱(箱/箱内件)→ 发出(SHIPPED,国内仓出库动账)→ 平台收货登记(手动录入平台收货数量 or 报告)→ 对账(本地发出 vs 平台收货 diff)。
- **V2(凭证槽位,TODO 挂 #3)**:SP-API Fulfillment Inbound 客户端(createInboundShipmentPlan 计划生成/putTransportContent 板箱信息/getShipments 收货对账自动化),按 write-adapter 流程扩 amazon 包,`@ConditionalOnProperty` 默认关(零噪音先例)。

### 2.2 单据模型(erp-fulfill 内新子域,拍板点①:落 erp-fulfill 而非新建模块——发货域血缘最近,type=FBA 预留在此)

- `fba_shipment`:shop_id/marketplace/warehouse_id(国内发货仓)/platform_shipment_id(平台回填,V1 手填可空)/status(`DRAFT→BOXED→SHIPPED→RECEIVING→CLOSED`,CANCELED 旁路)/remark/审计列。
- `fba_box`:shipment_id/box_no/weight/dim_l-dim_w-dim_h。
- `fba_box_item`:box_id/sku_id/quantity(uk box+sku)。
- 对账结果:`fba_shipment_diff`(shipment_id/sku_id/shipped_qty/received_qty/diff_qty/diff_type(SHORT/EXTRA/OK)/checked_at)——模式抄 RefundDiffEvent 三分类纪律。

### 2.3 库存语义(拍板点②)

- SHIPPED 时:货物理离开国内仓 → 逐 SKU `InventoryService.change()`(flow_type=OUT_SHIP,biz_type=`FBA_SHIPMENT`,biz_id=发货单 id,unit_cost 走移动加权结转——成本随货走,后续 FBA 销售成本才有依据)。
- **前置占用用 LOCK_SHIP 还是直接 OUT_SHIP?** 推荐:建单(BOXED)时不占库存(FBA 计划期长,长占恶化可用),SHIPPED 一步 OUT_SHIP(守卫在库充足);备选复用发货单 LOCK→OUT 两步。默认一步,devlog 拍板。
- FBA 仓侧不入账(wh_type=FBA 仓档案存在但不建 inventory 行),平台收货只进对账面。

### 2.4 对账面(V1 手动 + 骨架)

- RECV 登记:RECEIVING 态录平台收货数量(整单录入或按 SKU)→ 生成 diff 行(SHORT 缺收/EXTRA 多收/OK)→ 人工确认 CLOSED。
- `FbaReconciliationJob` 骨架:每日扫 SHIPPED 超 N 天未登记收货的单子 → pushAllUsers 提醒(抄 RefundReconciliationJob 两段式+静默期形态);自动取数版(报告/API)随 V2。

## 三、实施步骤

1. 拍板记录 devlog(口径归 P2 内部数据面 + 库存一步 OUT_SHIP + 模块落 erp-fulfill)。
2. add-table skill:fba_shipment/fba_box/fba_box_item/fba_shipment_diff 四表 + product_sku 加 weight_g/volume 三列(与 first-mile-freight.md 共享,谁先做谁落)。
3. fba 域八件套 + 状态机 + 守卫测试生成器 spec(testgen-fba-shipment.txt)。
4. SHIPPED 复合事务(逐 SKU change() OUT_SHIP + Σ 箱内件一致性勾稽:Σbox_item=计划量)。
5. 收货登记 + diff 生成 + FbaReconciliationJob 骨架(默认 enabled=false,同 ReportDigestJob 总开关惯例)。
6. V2 槽位:AmazonClient 补 inbound 方法族 TODO(新编号)+ 指引登记 TODO.md(实现等 #3 凭证)。
7. 前端:FBA 发货单列表/详情/装箱/收货登记(add-page spec)。

## 四、表结构草案(最终以 add-table skill 落正本)

```sql
CREATE TABLE IF NOT EXISTS fba_shipment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  shipment_no VARCHAR(32) NOT NULL COMMENT 'FBA 发货单号 FB+yyyyMMdd+seq',
  shop_id BIGINT NOT NULL, marketplace VARCHAR(16) NOT NULL COMMENT '站点',
  warehouse_id BIGINT NOT NULL COMMENT '国内发货仓',
  platform_shipment_id VARCHAR(64) NULL COMMENT '平台 ShipmentId(V2 回填/V1 手填)',
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/BOXED/SHIPPED/RECEIVING/CLOSED/CANCELED',
  shipped_at DATETIME NULL, remark VARCHAR(255) NULL,
  created_by/created_at/updated_at/deleted,
  UNIQUE KEY uk_fba_shipment_no (shipment_no)
) COMMENT 'FBA 发货单(V1 内部数据面)';
-- fba_box(id, shipment_id, box_no, weight DECIMAL(12,3), dim_l/dim_w/dim_h INT, uk(shipment_id,box_no))
-- fba_box_item(id, box_id, sku_id, quantity, uk(box_id,sku_id))
-- fba_shipment_diff(id, shipment_id, sku_id, shipped_qty, received_qty NULL, diff_type VARCHAR(8) SHORT/EXTRA/OK,
--   checked_at, uk(shipment_id,sku_id))
```

## 五、验收标准

- 状态机守卫四类用例绿;装箱勾稽:Σbox_item ≠ 计划量时 SHIPPED 被拦。
- 真库动账:SHIPPED 后国内仓在库/占用正确下降,inventory_flow OUT_SHIP 行 biz_type=FBA_SHIPMENT,sku_cost_state 加权结转正确;可用不足整单回滚。
- 对账:收货少于发出→SHORT 行;重复登记幂等;超期未登记被 Job 提醒(手动触发验证后关回)。
- 前端门禁四件;mvn 全量绿;Job 默认关零噪音。

## 六、红线提醒

- 动账唯一入口 change();成本结转随 change() 同事务禁旁路。
- 状态机条件更新即守卫;复合事务原子(部分行成功=危险态)。
- adapter 纪律:V2 Inbound 客户端报文翻译零业务 if(docs/07 §8);真报文脱敏 fixture 做翻译断言;凭证相关走 ShopSession,禁直读店铺表。
- FBA 不回传 uploadTracking 的既有裁剪不动(ShipmentSyncService L162 行为保持)。
- SQL 9.7.2 形态真库验证;逻辑删除;金额/重量精度 DECIMAL。

## 七、交接边界

1. 口径归 P2(内部数据面先行)的确认 + devlog。
2. 库存一步 OUT_SHIP(推荐)vs LOCK_SHIP→OUT_SHIP 两步。
3. 模块落 erp-fulfill(推荐)vs erp-inventory。
4. FBA 全量库存跟踪(在 FBA 仓建 inventory 行)明确延后,触发点=FBA 库存差异成为真实痛点。
5. V2 SP-API Inbound 集成的 TODO 编号登记(实现随 #3)。
