package com.own.erp.aftersale.entity;

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
 * @Description : 售后单(平台售后同步,幂等:uk_shop_platform_refund)(aftersale_order)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("aftersale_order")
public class AftersaleOrder {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 售后单号,唯一 */
    private String aftersaleNo;

    /** 店铺ID(shop.id) */
    private Long shopId;

    /** 平台退款/售后单ID,幂等键 */
    private String platformRefundId;

    /** 关联平台订单ID(shop_order.id) */
    private Long orderId;

    /** 退货入库仓ID(warehouse.id,收退件时必填回填;#12 激活加列 2026-09-04) */
    private Long warehouseId;

    /** REFUND_ONLY仅退款/RETURN_REFUND退货退款/EXCHANGE换货/RESEND补发 */
    private String type;

    /** PENDING待处理/APPROVED已同意/RETURNING待收退件/RETURN_RECEIVED已收退件/REFUNDED已退款/COMPLETED已完成/REJECTED已拒绝/CANCELLED已取消(#12 状态机 2026-09-04 定版) */
    private String status;

    /** 退款金额(原币) */
    private BigDecimal refundAmount;

    /** 币种(ISO 4217) */
    private String currency;

    /** 售后原因 */
    private String reason;

    /** 处理结果 */
    private String result;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
