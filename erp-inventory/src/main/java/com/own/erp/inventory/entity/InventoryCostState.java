package com.own.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : SKU 移动加权成本账(#19③ 利润核算 V1,docs/02 §14 成本计价拍板"先移动加权")(sku_cost_state)。
 *     全局跨仓账本(不分仓,调拨两腿不进账);动账同事务由 InventoryCostService 维护,出库结转先
 *     SELECT FOR UPDATE 本行串行化同 SKU 成本计算;结存金额可由 inventory_flow 成本列逐笔重放校验(V2 对账)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sku_cost_state")
public class InventoryCostState {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** SKU ID(product_sku.id) */
    private Long skuId;

    /** 账本结存数量(全局跨仓,移动加权分母;与 inventory 在库量口径一致可核对) */
    private Integer totalQty;

    /** 账本结存金额(CNY,移动加权分子) */
    private BigDecimal totalAmount;

    /** 当前移动加权单价(CNY)=total_amount/total_qty;结存清零时保留末次价作下次入库暂估基准 */
    private BigDecimal avgCost;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
