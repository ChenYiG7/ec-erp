package com.own.erp.purchase.entity;

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
 * @Date : 2026/9/3
 * @Description : 采购入库单(入库必须经 InventoryService.change 同事务写 flow,flow_type=IN_PURCHASE)(purchase_inbound)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("purchase_inbound")
public class PurchaseInbound {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 入库单号,唯一 */
    private String inboundNo;

    /** 采购单ID(purchase_order.id) */
    private Long poId;

    /** 入库仓ID(warehouse.id) */
    private Long warehouseId;

    /** PENDING待入库/RECEIVED已入库/CANCELLED已取消 */
    private String status;

    /** 备注(数量差异说明等) */
    private String remark;

    /** 创建人(sys_user.id) */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
