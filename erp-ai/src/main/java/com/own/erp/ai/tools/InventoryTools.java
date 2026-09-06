package com.own.erp.ai.tools;

import com.own.erp.contract.InventoryQueryApi;
import com.own.erp.contract.QueryPage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 库存查询工具(#6,启航 11 类 checklist:Inventory 类已开;余量待随查询契约扩容)。
 *         只读铁律(铁律 7):库存变更唯一入口 InventoryService.change(铁律 4),本工具只读,
 *         无任何写路径;契约接口注入一律 @Lazy 断构造环(docs/07 §2.2)
 */
@Component
public class InventoryTools {

    private final InventoryQueryApi inventoryQueryApi;

    public InventoryTools(@Lazy InventoryQueryApi inventoryQueryApi) {
        this.inventoryQueryApi = inventoryQueryApi;
    }

    @Tool(description = "分页查询分仓库存(口径:qtyOnHand在库/qtyLocked发货占用/qtyTransit采购在途/qtyAvailable可用=在库-占用)。过滤条件均可选")
    public QueryPage<InventoryQueryApi.InventoryView> queryInventory(
            @ToolParam(required = false, description = "内部SKU ID(product_sku.id),精确过滤") Long skuId,
            @ToolParam(required = false, description = "仓库ID(warehouse.id),精确过滤") Long warehouseId,
            @ToolParam(required = false, description = "页码,从1起,不传默认1") Integer pageNo,
            @ToolParam(required = false, description = "页大小,不传默认20,最大100") Integer pageSize) {
        return inventoryQueryApi.pageInventory(InventoryQueryApi.InventoryFilter.builder()
                .skuId(skuId)
                .warehouseId(warehouseId)
                .pageNo(pageNo == null ? 0 : pageNo)
                .pageSize(pageSize == null ? 0 : pageSize)
                .build());
    }
}
