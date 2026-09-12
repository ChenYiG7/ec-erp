# TODO(#30) 仓内作业(盘点单/调拨单域)

- 日期: 2026-09-11
- 计划书: `docs/plans/warehouse-ops.md`
- 收尾提交: 待提交(本次会话为工作区改动,未提交)

## 拍板

- **盘点差异口径 = 确认时点账面重算**(计划书 §2.1 拍板点②落地):建单即快照账面在库进 stocktake_item.book_qty
  (snapshot_at 记时点,仅展示用);generateAdjust 按 `InventoryService.currentOnHand` 逐行 re-diff,差异行经
  `change()`(flow_type=ADJUST、biz_type=STOCKTAKE、biz_id=盘点单 id)动账。理由:非冻结盘点期间建单快照会被
  运行中动账污染,确认时点口径保证动账差额与真账一致;行级守卫拒(可用不足)整单回滚,数据异常先查不静默放行。
- **盘盈/盘亏成本口径 = 当前移动加权价**(拍板点③):随 `change()` 同事务进 InventoryCostService(CostOps 分派),
  盘盈按当前加权价入、盘亏按当前加权价结转,与 OUT_SHIP 同口径,成本账不出现第二套语义。
- **调拨 V1 = 确认即达无在途账**(拍板点④):CONFIRM 复合事务逐行 `transfer()` 两腿动账;在途模式
  (OUT 占用→到货 IN)留多仓地理分离需求出现再立项。`transfer()` biz_type 由 INVENTORY_TRANSFER 收口为
  TRANSFER_ORDER(仅新流水,历史不改写,查询兼容两值)。
- **库位/批次:评估结论不做**(计划书 §2.4):库级粒度牵动 uk=sku+warehouse/四量模型/成本账/日快照/对账/前端,
  无批次效期合规压力;触发点(进口效期合规/库龄精细化报表/多库位拣货瓶颈)任一成真痛点再按 add-domain 立项。

## 改动

- 后端(erp-inventory):盘点两表+调拨两表 DDL(docs/sql 正本,docs/03 §4.1 对齐);
  `StocktakeOrder/TransferOrder` 两域八件套 + `StocktakeConsts/TransferConsts` 词表;六态/三态状态机
  casStatus 条件更新即守卫;`generateAdjust` 复合事务(确认时点 re-diff + 逐行 ADJUST 动账 + 成本联动);
  `TransferOrderService.confirm` 复合事务(逐行两腿动账);`InventoryConsts` 加 BIZ_TYPE_STOCKTAKE/TRANSFER_ORDER。
- 测试:`StocktakeOrderServiceTest`/`TransferOrderServiceTest`(AIR mock)+ 两状态机守卫测试
  (生成器 spec:`erp-inventory/testgen-stocktake.txt`、`testgen-transfer.txt`);复合事务/守卫下推留 TODO 槽位人工核。
- 前端(本次会话):`tools/specs/stocktake.txt`、`transfer.txt` 两拍板表 → gen:page 四件×2;
  盘点单页按六态裁剪动作按钮(开始/录实盘/提交/生成调整/关闭/取消)+ `StocktakeOrderForm`
  (仓库下拉/范围单选/SKU 集行编辑联动)+ **`StocktakeCountDialog`(录实盘·详情双模定制组件,
  生成器外)**;调拨单页按三态裁剪(确认/取消)+ `TransferOrderForm`(明细子表行编辑,对齐 PurchaseOrderForm)
  + `TransferOrderDetail` 详情弹窗;api/类型由快照生成(counts 动作含请求体类型)。
- 契约:`tools/openapi.json` 手工同步两域 12 端点 + 17 schema(后端未起,api:sync 不可用,见坑);
  sys_menu 种子补 3808 开始盘点/3809 提交盘点两按钮 + role_menu 绑定(前端 v-auth 需要 permKey 登记)。

## 坑

- **计划书页面清单的按钮种子漏了 start/submit**:种子只写了 add/edit/remove/count/adjust/close/cancel 八件,
  开始盘点/提交盘点两动作无 permKey → 非引导期下 v-auth 对管理员也隐藏按钮。已补 3808/3809 进
  01_schema_init.sql(INSERT IGNORE 幂等,已建库需重跑该段)。
- **手改 tools/openapi.json 属权宜**(同 #29 先例):正本来源是 `pnpm api:sync`(需后端 8088 在跑);
  本次按 Controller/record 手工补齐端点与 schema 并用 node JSON.parse 校验,后续必须重跑 api:sync 覆盖。
- **PowerShell 5.1 下 pnpm build 的 stderr 进度行会被包装成 NativeCommandError** 且输出截断,
  伪报 exit 1;经 `cmd /c` 收敛输出后实测 BUILD_EXIT=0(12.1s built)。门禁结论以 cmd 收敛后为准。
- gen:page 产出的操作列动作按钮全是注释槽位,enum 列不会自动带 search 配置(role=column 无搜索位,
  role=all 又会把服务端管理列灌进表单)——状态/仓库搜索项人工补在 columns 上,与 #10 采购单页同口径。

## 未尽

- **真库验证未做**(同 #29):盘点建单快照/生成调整动账 SQL、调拨两腿动账、casStatus 条件更新、
  inventory/inventory_flow/sku_cost_state 三方对账断言,均需真库跑(单测 mock Mapper 测不出 XML)。
- 仓库删除引用校验未纳入 stocktake_order/transfer_order(TODO #30 余量⑤,现仅 inventory+采购两域计数)。
- `pnpm api:sync` 重抓契约快照待后端起后补跑(当前为手改同步);已建库需重跑 sys_menu 38/39 段
  (含 3808/3809)+ role_menu 段,重新登录生效。
- 调拨在途模式(余量①)/盘点冻结(明确不做②)/良品残品分账(三期③)维持计划书口径。
