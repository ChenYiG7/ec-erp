package com.own.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 盘点单明细(建单快照+实盘+差异;差异 ADJUST 动账凭证)(stocktake_item)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("stocktake_item")
public class StocktakeItem {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 盘点单ID(stocktake_order.id) */
    private Long stocktakeId;

    /** SKU ID(product_sku.id) */
    private Long skuId;

    /** 建单快照账面在库量(snapshot_at 时点值,展示用) */
    private Integer bookQty;

    /** 账面快照时点 */
    private LocalDateTime snapshotAt;

    /** 实盘数量(COUNTING 阶段录入,未录为 NULL) */
    private Integer countedQty;

    /** 差异=实盘-确认时点账面(生成调整时算,生成前为 NULL) */
    private Integer diffQty;

    /** 差异调整流水ID(inventory_flow.id,生成调整时回填) */
    private Long adjustFlowId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
