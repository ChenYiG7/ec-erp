package com.own.erp.contract.impl;

import com.own.erp.contract.WarehouseApi;
import com.own.erp.inventory.service.InventoryService;
import com.own.erp.purchase.service.PurchaseOrderService;
import com.own.erp.warehouse.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : WarehouseApi 实现(接口模块方案的编排胶水,收口 erp-api,#10):
 *         erp-purchase 校验 warehouse_id 存在性,取数走 erp-warehouse WarehouseService(docs/07 §2.2);
 *         #7 收口:删除引用计数 = 库存 + 采购两域合计(InventoryService/PurchaseOrderService)
 */
@Component
@RequiredArgsConstructor
public class WarehouseApiImpl implements WarehouseApi {

    private final WarehouseService warehouseService;
    private final InventoryService inventoryService;
    private final PurchaseOrderService purchaseOrderService;

    @Override
    public boolean existsWarehouse(Long warehouseId) {
        return warehouseService.existsWarehouse(warehouseId);
    }

    @Override
    public long countWarehouseRefs(Long warehouseId) {
        return inventoryService.countByWarehouseId(warehouseId)
                + purchaseOrderService.countByWarehouseId(warehouseId);
    }
}
