package com.own.erp.order.entity;

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
 * @Description : 平台订单(幂等:uk_shop_platform_order)(shop_order)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("shop_order")
public class ShopOrder {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 店铺ID(shop.id) */
    private Long shopId;

    /** PlatformType枚举名:TAOBAO/AMAZON/... */
    private String platform;

    /** 平台订单号,幂等唯一键 */
    private String platformOrderId;

    /** WAIT_PAY/WAIT_SHIP/SHIPPED/COMPLETED/CANCELLED/CLOSED */
    private String orderStatus;

    /** SELF_FULFILL/FBA/OVERSEAS_WAREHOUSE */
    private String fulfillmentChannel;

    /** 下单时间(平台侧) */
    private LocalDateTime orderTime;

    /** 支付时间 */
    private LocalDateTime paidTime;

    /** 买家留言 */
    private String buyerNote;

    /** 收货人姓名 */
    private String receiverName;

    /** 收货人电话 */
    private String receiverPhone;

    /** 收货国家(ISO 3166,如CN/US) */
    private String receiverCountry;

    /** 收货省/州 */
    private String receiverState;

    /** 收货城市 */
    private String receiverCity;

    /** 收货详细地址 */
    private String receiverAddress;

    /** 收货邮编 */
    private String receiverZip;

    /** 币种(ISO 4217) */
    private String currency;

    /** 下单日汇率快照(原币→本位币) */
    private BigDecimal exchangeRate;

    /** 订单总金额(原币) */
    private BigDecimal orderAmount;

    /** 运费(原币) */
    private BigDecimal shippingFee;

    /** 优惠金额(原币) */
    private BigDecimal discountAmount;

    /** 平台原始报文,排查/补偿用 */
    private String rawJson;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
