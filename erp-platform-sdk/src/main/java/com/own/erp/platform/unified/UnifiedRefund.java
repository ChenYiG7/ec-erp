package com.own.erp.platform.unified;

import com.own.erp.platform.PlatformType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 统一售后模型(退款/退货/换货/补发)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedRefund {

    private String platformRefundId;
    private String platformOrderId;
    private Long shopId;
    private PlatformType platform;

    private RefundType type;
    private RefundStatus status;

    private String currency;
    private BigDecimal refundAmount;
    private String reason;
    private String description;

    private Instant applyTime;
    private Instant finishTime;

    private List<Item> items;

    public enum RefundType {
        REFUND_ONLY,      // 仅退款
        RETURN_REFUND,    // 退货退款
        EXCHANGE,         // 换货
        RESEND            // 补发
    }

    public enum RefundStatus {
        APPLYING,          // 处理中
        WAIT_RECEIVE,      // 待收货(买家已寄回)
        FINISHED,          // 已完成
        REJECTED,          // 已拒绝
        CANCELLED          // 已撤销
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String platformSkuId;
        private String sellerSku;
        private Integer quantity;
        private BigDecimal refundAmount;
    }
}
