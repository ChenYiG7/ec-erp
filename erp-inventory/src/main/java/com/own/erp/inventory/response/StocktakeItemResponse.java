package com.own.erp.inventory.response;

import com.own.erp.inventory.entity.StocktakeItem;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 盘点单明细对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     record+@Builder(模型可变性分级 docs/07 §1);from 用 builder 命名传参防相邻同类型字段错位
 */
@Builder
public record StocktakeItemResponse(

        /** 主键 */
        Long id,

        /** 盘点单ID(stocktake_order.id) */
        Long stocktakeId,

        /** SKU ID(product_sku.id) */
        Long skuId,

        /** 建单快照账面在库量(snapshot_at 时点值,展示用) */
        Integer bookQty,

        /** 账面快照时点 */
        LocalDateTime snapshotAt,

        /** 实盘数量(COUNTING 阶段录入,未录为 null) */
        Integer countedQty,

        /** 差异=实盘-确认时点账面(生成调整时算,生成前为 null) */
        Integer diffQty,

        /** 差异调整流水ID(inventory_flow.id,生成调整时回填) */
        Long adjustFlowId,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt

) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见);builder 命名传参防相邻同类型字段错位 */
    public static StocktakeItemResponse from(StocktakeItem entity) {
        return StocktakeItemResponse.builder()
                .id(entity.getId())
                .stocktakeId(entity.getStocktakeId())
                .skuId(entity.getSkuId())
                .bookQty(entity.getBookQty())
                .snapshotAt(entity.getSnapshotAt())
                .countedQty(entity.getCountedQty())
                .diffQty(entity.getDiffQty())
                .adjustFlowId(entity.getAdjustFlowId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
