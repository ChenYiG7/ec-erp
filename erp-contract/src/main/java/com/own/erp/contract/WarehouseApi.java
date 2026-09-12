package com.own.erp.contract;

import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 内部仓库契约(接口模块方案,#10 起):erp-purchase 采购单/入库单校验 warehouse_id,
 *         防止错误仓库ID经 InventoryService.change 自动建行产出幻影库存;实现收口 erp-api(WarehouseApiImpl,
 *         查 erp-warehouse)——业务域禁横向依赖 erp-warehouse(铁律 2);后续发货域(#11)同用。
 *         #7 收口(2026-09-04):新增删除引用计数(库存/采购两域合计,仓库删除守卫用)。
 *         #33 扩容(2026-09-11,只加方法不改语义):findWarehouseViewById 仓型视图,
 *         供头程发货单校验 SELF→OVERSEAS/FBA 流向
 */
public interface WarehouseApi {

    /** 内部仓库(warehouse.id)是否存在 */
    boolean existsWarehouse(Long warehouseId);

    /** 仓库被引用行数:inventory / purchase_order / stocktake_order / transfer_order 四域合计;>0 即禁删(#7,#30 余量扩容) */
    long countWarehouseRefs(Long warehouseId);

    /**
     * 仓库行视图(#33 头程流向校验):按 id 取仓型/国家/名称,不存在返回 null。
     * 头程发货单据此校验 from=SELF 国内仓、to∈{OVERSEAS,FBA} 目的仓
     */
    WarehouseView findWarehouseViewById(Long warehouseId);

    /** 仓库只读行视图(跨域只带流向校验/展示所需字段,凭证类域无敏感列) */
    @Builder
    record WarehouseView(

            /** 仓库ID(warehouse.id) */
            Long id,

            /** 仓库名称 */
            String whName,

            /** SELF自仓/FBA/OVERSEAS海外仓/VIRTUAL虚拟仓 */
            String whType,

            /** 国家(ISO 3166) */
            String country,

            /** 1启用0禁用 */
            Integer status
    ) {
    }
}
