package com.own.erp.contract;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 内部仓库契约(接口模块方案,#10 起):erp-purchase 采购单/入库单校验 warehouse_id,
 *         防止错误仓库ID经 InventoryService.change 自动建行产出幻影库存;实现收口 erp-api(WarehouseApiImpl,
 *         查 erp-warehouse)——业务域禁横向依赖 erp-warehouse(铁律 2);后续发货域(#11)同用。
 *         #7 收口(2026-09-04):新增删除引用计数(库存/采购两域合计,仓库删除守卫用)
 */
public interface WarehouseApi {

    /** 内部仓库(warehouse.id)是否存在 */
    boolean existsWarehouse(Long warehouseId);

    /** 仓库被引用行数:inventory.warehouse_id 与 purchase_order.warehouse_id 两域合计;>0 即禁删(#7) */
    long countWarehouseRefs(Long warehouseId);
}
