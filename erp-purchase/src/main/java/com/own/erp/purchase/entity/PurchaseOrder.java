package com.own.erp.purchase.entity;

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
 * @Description : 采购单(审核/入库状态机 TODO(#10) 人工补)(purchase_order)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("purchase_order")
public class PurchaseOrder {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 采购单号,唯一 */
    private String poNo;

    /** 供应商ID(supplier.id) */
    private Long supplierId;

    /** 收货仓ID(warehouse.id) */
    private Long warehouseId;

    /** DRAFT草稿/AUDITED已审核/PARTIAL_RECEIVED部分入库/RECEIVED已入库/CLOSED已关闭(状态机草案,业务确认后调整) */
    private String status;

    /** 采购总金额(本位币) */
    private BigDecimal totalAmount;

    /** 备注 */
    private String remark;

    /** 创建人(sys_user.id) */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
