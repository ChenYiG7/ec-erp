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
 * @Date : 2026/9/3
 * @Description : 库存流水(与库存变更同事务写入)(inventory_flow)。
 *     @Builder 供调用方装配入参(采购入库/发货出库等,docs/07 §1 分级⑤);
 *     双构造保无参构造,MP 反射映射/存量测试不受影响。before/after 由 change() 写前回填,装配时不用管
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("inventory_flow")
public class InventoryFlow {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** SKU ID(product_sku.id) */
    private Long skuId;

    /** 仓库ID(warehouse.id) */
    private Long warehouseId;

    /** IN_PURCHASE/OUT_SHIP/IN_RETURN/ADJUST/TRANSFER_OUT/TRANSFER_IN */
    private String flowType;

    /** 正负数 */
    private Integer quantity;

    /** 变更前可用库存 */
    private Integer beforeQty;

    /** 变更后可用库存 */
    private Integer afterQty;

    /** 关联业务类型 */
    private String bizType;

    /** 关联业务单据ID */
    private Long bizId;

    /** 动账单价快照(CNY,移动加权 #19③):IN_PURCHASE=采购单价(缺价暂估当时加权价)/OUT_SHIP=结转时加权价/
     *  IN_RETURN·ADJUST=当时加权价;IN_TRANSIT·LOCK_SHIP·TRANSFER_OUT·TRANSFER_IN 不进成本账为NULL */
    private BigDecimal unitCost;

    /** 动账成本额(CNY,带符号=quantity×unit_cost:入库正/出库负;不进成本账类型为NULL) */
    private BigDecimal costAmount;

    /** 备注 */
    private String remark;

    /** 操作人(sys_user.id),系统动作为NULL */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
