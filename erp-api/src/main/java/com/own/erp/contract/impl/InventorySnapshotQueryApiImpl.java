package com.own.erp.contract.impl;

import com.own.erp.contract.InventorySnapshotQueryApi;
import com.own.erp.inventory.entity.InventorySnapshotDaily;
import com.own.erp.inventory.service.InventorySnapshotDailyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : InventorySnapshotQueryApi 实现(接口模块方案编排胶水,收口 erp-api,#6 库存快照数据面):
 *         erp-ai/报表域取数委托 erp-inventory InventorySnapshotDailyService.listSeries,
 *         entity→契约 record 显式逐字段映射(禁反射拷贝,漏字段编译期可见)
 */
@Component
@RequiredArgsConstructor
public class InventorySnapshotQueryApiImpl implements InventorySnapshotQueryApi {

    private final InventorySnapshotDailyService inventorySnapshotDailyService;

    @Override
    public List<SnapshotView> listSeries(Long skuId, Long warehouseId, LocalDate from, LocalDate to, Integer limit) {
        List<InventorySnapshotDaily> rows = inventorySnapshotDailyService.listSeries(skuId, warehouseId, from, to, limit);
        return rows.stream().map(InventorySnapshotQueryApiImpl::toView).toList();
    }

    private static SnapshotView toView(InventorySnapshotDaily e) {
        return SnapshotView.builder()
                .statDate(e.getStatDate())
                .skuId(e.getSkuId())
                .warehouseId(e.getWarehouseId())
                .qtyOnHand(e.getQtyOnHand())
                .qtyLocked(e.getQtyLocked())
                .qtyTransit(e.getQtyTransit())
                .qtyAvailable(e.getQtyAvailable())
                .build();
    }
}
