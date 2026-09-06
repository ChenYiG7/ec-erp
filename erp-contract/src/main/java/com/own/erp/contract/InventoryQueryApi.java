package com.own.erp.contract;

import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 库存只读查询契约(#6 三期 AI 地基):erp-ai 工具取数唯一正道(铁律 2,禁横向依赖 erp-inventory),
 *         实现收口 erp-api(InventoryQueryApiImpl,委托 InventoryService.page)。
 *         参数/返回全 record 不引 MP 类型;只读——库存变更唯一入口 InventoryService.change(铁律 4),
 *         本契约只供查询,不存在任何写方法
 */
public interface InventoryQueryApi {

    /** 库存分页查询(过滤条件全空 = 全量分页);分页大小钳制 1..100(服务端 PageQuery ≤500 兜底) */
    QueryPage<InventoryView> pageInventory(InventoryFilter filter);

    /**
     * 过滤条件 + 分页入参:skuId/warehouseId 均可空(对齐 uk_sku_wh 两维);pageNo/pageSize 为 int,
     * @Builder 不设时默认 0,经 page()/size() 归一后生效
     */
    @Builder
    record InventoryFilter(

            /** SKU ID(product_sku.id,精确,可空) */
            Long skuId,

            /** 仓库ID(warehouse.id,精确,可空) */
            Long warehouseId,

            /** 页码(从 1 起) */
            int pageNo,

            /** 页大小 */
            int pageSize
    ) {

        /** 归一页码(<1 按 1) */
        public int page() {
            return Math.max(pageNo, 1);
        }

        /** 归一页大小(未传/非法按默认 20,上限 100) */
        public int size() {
            return pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        }
    }

    /** 库存行视图(分仓);数量口径见 inventory 表列语义(FlowOps 矩阵,#7) */
    @Builder
    record InventoryView(

            /** 主键(inventory.id) */
            Long id,

            /** SKU ID(product_sku.id) */
            Long skuId,

            /** 仓库ID(warehouse.id) */
            Long warehouseId,

            /** 在库 */
            Integer qtyOnHand,

            /** 占用(发货单占用未发货) */
            Integer qtyLocked,

            /** 在途(采购审核占用未入库) */
            Integer qtyTransit,

            /** 可用=在库-占用(InventoryService 同事务维护) */
            Integer qtyAvailable
    ) {
    }
}
