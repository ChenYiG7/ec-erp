package com.own.erp.contract.impl;

import com.own.erp.contract.WarehouseApi;
import com.own.erp.inventory.service.InventoryService;
import com.own.erp.inventory.service.StocktakeOrderService;
import com.own.erp.inventory.service.TransferOrderService;
import com.own.erp.purchase.service.PurchaseOrderService;
import com.own.erp.warehouse.response.WarehouseResponse;
import com.own.erp.warehouse.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : WarehouseApi 实现(接口模块方案的编排胶水,收口 erp-api,#10):
 *         erp-purchase 校验 warehouse_id 存在性,取数走 erp-warehouse WarehouseService(docs/07 §2.2);
 *         #7 收口:删除引用计数 = 库存 + 采购两域合计(InventoryService/PurchaseOrderService)。
 *         #30 余量收口(2026-09-12):计数扩容盘点/调拨两域(StocktakeOrderService/TransferOrderService,四域合计)。
 *         #33 扩容:findWarehouseViewById 仓型视图委托 WarehouseService.getById
 */
@Component
@RequiredArgsConstructor
public class WarehouseApiImpl implements WarehouseApi {

    private final WarehouseService warehouseService;
    private final InventoryService inventoryService;
    private final PurchaseOrderService purchaseOrderService;
    private final StocktakeOrderService stocktakeOrderService;
    private final TransferOrderService transferOrderService;

    @Override
    public boolean existsWarehouse(Long warehouseId) {
        return warehouseService.existsWarehouse(warehouseId);
    }

    @Override
    public long countWarehouseRefs(Long warehouseId) {
        return inventoryService.countByWarehouseId(warehouseId)
                + purchaseOrderService.countByWarehouseId(warehouseId)
                + stocktakeOrderService.countByWarehouseId(warehouseId)
                + transferOrderService.countByWarehouseId(warehouseId);
    }

    @Override
    public WarehouseView findWarehouseViewById(Long warehouseId) {
        if (warehouseId == null) {
            return null;
        }
        WarehouseResponse wh = warehouseService.getById(warehouseId);
        if (wh == null) {
            return null;
        }
        return WarehouseView.builder()
                .id(wh.id())
                .whName(wh.whName())
                .whType(wh.whType())
                .country(wh.country())
                .status(wh.status())
                .build();
    }
}
