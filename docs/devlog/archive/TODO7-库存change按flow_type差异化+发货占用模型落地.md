# TODO(#7) 库存change按flow_type差异化+发货占用模型落地

- 日期: 2026-09-06
- 收尾提交: ba4f68d 后续工作区改动(未提交时以 TODO.md #7 条目为准)

## 拍板
- **change() 列语义矩阵**(InventoryService.FlowOps 枚举,枚举名即 flow_type 字面量):新增 IN_TRANSIT
  (采购审核占在途/关闭释放)与 LOCK_SHIP(发货建单占用/取消·删除·改单释放);IN_PURCHASE 改"核销在途"
  (守卫=在途充足)、OUT_SHIP 改"占用转出库"(在库/占用双降,可用不变);IN_RETURN/ADJUST/TRANSFER_* 保持通用形。
  不变量 qty_available = on_hand − locked 全类型成立,守卫全下推 WHERE(一条原子 UPDATE 一类型)。
- **发货单占用模型升级(取代 2026-09-04"建单不动账"旧口径)**:qty_locked 列语义"占用(已分配未发货)"
  本就预设建单分配,旧模型 PENDING 在途单不持有库存、缺货要等 ship 才暴露;save 即 LOCK_SHIP 占用后
  缺货建单即拦,并发建单超发窗口被占用守卫顺带闭合(遗留项销账)。
- 改单竞态用行锁收口:selectByIdForUpdate(FOR UPDATE)串行化 update 与 ship/cancel 的 check-then-act,
  防"改单释放了已发货单据的占用"错账;cancel/delete 走 cas 先行同效。

## 改动
- erp-inventory:InventoryMapper 五条原子 UPDATE(按类型分发)+ InventoryService.change 按类型矩阵分发
  + transfer() 跨仓组合收口(暂无调拨单域,直调预留);erp-contract:InventoryConsts 扩两值。
- erp-purchase:audit 升级复合事务动作(cas + 逐行 IN_TRANSIT 占在途)、close 升级复合(cas + 释放未到货在途);
  erp-fulfill:save 占用/cancel·delete 释放/update 行锁+释放重占/ship 走新 OUT_SHIP 语义。
- 词表三方同步:01_schema_init.sql(inventory_flow 八值 COMMENT)/docs/03 §4(私有,含矩阵表)/InventoryConsts;
  前端库存流水页枚举两值 + 建发货单表单文案;单测翻新 inventory 29 + purchase 48 + fulfill 27 全绿。

## 坑
- **存量数据断链**:开发库改造前已审核未收齐的采购单无在途占位、仍 PENDING 的发货单无占用记录——
  直接核销/出货会报"在途/占用不足";回补 SQL 存 TODO.md #10/#11 条目(ODKU 幂等,执行前备份)。
- cas 以 from==to(PENDING→PENDING)做"改单围栏"不可行:MySQL 默认无 CLIENT_FOUND_ROWS,
  值未变化时 affected=0 会误判脱靶——改单走 FOR UPDATE 行锁读才是正解。
- transfer 单测首版没打桩两腿的 updateAvailableDelta,负数出库腿在无行时按守卫拒绝——
  组合方法依赖存量行,假服务/纯 Mockito 单测必须显式命中存量行分支。

## 未尽
- transfer() 无业务入口(调拨单据/审批/前端页面随需求另立项,现直调预留);
- inventory_flow 的 IN_TRANSIT/OUT_SHIP 流水 before=after(不动可用)已 javadoc 注明属正常,
  前端流水页如需"动前动后"展示语义可后续按类型定制;
- 前端 库存查询页/流水页 的仓库/SKU 名称列翻译仍挂在"对应域页面完善"批次(TODO.md #7 已有记载)。
