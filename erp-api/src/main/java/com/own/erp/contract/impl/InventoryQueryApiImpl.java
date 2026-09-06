package com.own.erp.contract.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.contract.InventoryQueryApi;
import com.own.erp.contract.QueryPage;
import com.own.erp.inventory.request.query.InventoryQuery;
import com.own.erp.inventory.response.InventoryResponse;
import com.own.erp.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : InventoryQueryApi 实现(#6 三期 AI 地基,编排胶水收口 erp-api,docs/07 §2.2):
 *         erp-ai 工具取数委托 erp-inventory InventoryService.page;entity→契约 record
 *         显式逐字段映射(经域 Response 中转,禁反射拷贝);本契约只读,无任何写路径
 */
@Component
@RequiredArgsConstructor
public class InventoryQueryApiImpl implements InventoryQueryApi {

    private final InventoryService inventoryService;

    @Override
    public QueryPage<InventoryView> pageInventory(InventoryFilter filter) {
        InventoryQuery query = new InventoryQuery();
        query.setSkuId(filter.skuId());
        query.setWarehouseId(filter.warehouseId());
        query.setPageNo(filter.page());
        query.setPageSize(filter.size());
        Page<InventoryResponse> page = inventoryService.page(query);
        List<InventoryView> list = page.getRecords().stream().map(r -> InventoryView.builder()
                .id(r.id())
                .skuId(r.skuId())
                .warehouseId(r.warehouseId())
                .qtyOnHand(r.qtyOnHand())
                .qtyLocked(r.qtyLocked())
                .qtyTransit(r.qtyTransit())
                .qtyAvailable(r.qtyAvailable())
                .build()).toList();
        return QueryPage.of(list, page.getTotal());
    }
}
