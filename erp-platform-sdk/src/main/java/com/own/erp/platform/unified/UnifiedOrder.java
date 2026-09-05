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
 * @Description : 统一订单模型(防腐层)——各平台订单被 adapter 翻译成此模型后再入库。
 *     字段同时覆盖国内与跨境:
 *     - 国内:平台推单、电子面单发货
 *     - 跨境:FBA 订单不发货只对账、多币种、申报信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedOrder {

    /** 平台订单号(全局唯一键 = shopId + platformOrderId) */
    private String platformOrderId;
    private Long shopId;
    private PlatformType platform;

    /** 平台原始状态 → 统一状态由 adapter 翻译 */
    private OrderStatus status;

    private Instant orderTime;
    private Instant payTime;
    private Instant shipByTime;

    // ---- 金额(跨境必填币种/汇率) ----
    private String currency;
    /** 订单币种对本位币(人民币)汇率,国内订单固定 1 */
    private BigDecimal exchangeRate;
    private BigDecimal totalAmount;
    private BigDecimal postageAmount;
    private BigDecimal discountAmount;

    // ---- 履约渠道:决定后续走国内面单 / FBA 对账 / 海外仓推单 ----
    private FulfillmentChannel fulfillmentChannel;

    // ---- 收件人(跨境需国家/州/邮编,用于申报与物流) ----
    private String receiverName;
    private String receiverPhone;
    private String receiverCountry;
    private String receiverState;
    private String receiverCity;
    private String receiverDistrict;
    private String receiverAddress;
    private String receiverZip;

    private String buyerMessage;
    private String remark;

    /** 平台原始报文,排障与字段回溯用 */
    private String rawJson;

    private List<Item> items;

    public enum OrderStatus {
        WAIT_PAY,          // 待付款
        WAIT_SHIP,         // 待发货
        SHIPPED,           // 已发货
        COMPLETED,         // 已完成
        CANCELLED,         // 已取消
        CLOSED             // 已关闭(退款关闭)
    }

    public enum FulfillmentChannel {
        SELF_FULFILL,      // 自发货(国内面单 / 跨境直发)
        FBA,               // 亚马逊 FBA(不发货,只对账)
        OVERSEAS_WAREHOUSE // 海外仓/云仓代发
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        /** 平台行项目ID(Amazon: OrderItemId;2026-09-04 #3 演进新增,只加字段不改语义) */
        private String platformOrderItemId;
        private String platformProductId;
        private String platformSkuId;
        /** 平台商品标题 */
        private String title;
        /** 规格(颜色/尺码等) */
        private String skuProps;
        private Integer quantity;
        private BigDecimal unitPrice;
        /** 该行明细的退款状态 */
        private RefundStatus refundStatus;
        /** 商家编码(平台侧 externalSkuId,用于自动匹配 ERP SKU) */
        private String sellerSku;
    }

    public enum RefundStatus {
        NONE, PART_REFUNDED, REFUNDED
    }
}
