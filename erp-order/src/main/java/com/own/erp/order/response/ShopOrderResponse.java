package com.own.erp.order.response;

import com.own.erp.order.entity.ShopOrder;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 平台订单对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     raw_json 为平台原始报文(排查/补偿用,体积大),不对外——排查直查 DB
 */
@Builder
public record ShopOrderResponse(

        /** 主键 */
        Long id,

        /** 店铺ID(shop.id) */
        Long shopId,

        /** PlatformType枚举名:TAOBAO/AMAZON/... */
        String platform,

        /** 平台订单号,幂等唯一键 */
        String platformOrderId,

        /** WAIT_PAY/WAIT_SHIP/SHIPPED/COMPLETED/CANCELLED/CLOSED */
        String orderStatus,

        /** SELF_FULFILL/FBA/OVERSEAS_WAREHOUSE */
        String fulfillmentChannel,

        /** 下单时间(平台侧) */
        LocalDateTime orderTime,

        /** 支付时间 */
        LocalDateTime paidTime,

        /** 买家留言 */
        String buyerNote,

        /** 收货人姓名 */
        String receiverName,

        /** 收货人电话 */
        String receiverPhone,

        /** 收货国家(ISO 3166,如CN/US) */
        String receiverCountry,

        /** 收货省/州 */
        String receiverState,

        /** 收货城市 */
        String receiverCity,

        /** 收货详细地址 */
        String receiverAddress,

        /** 收货邮编 */
        String receiverZip,

        /** 币种(ISO 4217) */
        String currency,

        /** 下单日汇率快照(原币→本位币) */
        BigDecimal exchangeRate,

        /** 订单总金额(原币) */
        BigDecimal orderAmount,

        /** 运费(原币) */
        BigDecimal shippingFee,

        /** 优惠金额(原币) */
        BigDecimal discountAmount,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt,

        /** 订单明细(仅详情接口随单返回,列表不带) */
        List<ShopOrderItemResponse> items
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见) */
    public static ShopOrderResponse from(ShopOrder entity) {
        return ShopOrderResponse.builder()
                .id(entity.getId())
                .shopId(entity.getShopId())
                .platform(entity.getPlatform())
                .platformOrderId(entity.getPlatformOrderId())
                .orderStatus(entity.getOrderStatus())
                .fulfillmentChannel(entity.getFulfillmentChannel())
                .orderTime(entity.getOrderTime())
                .paidTime(entity.getPaidTime())
                .buyerNote(entity.getBuyerNote())
                .receiverName(entity.getReceiverName())
                .receiverPhone(entity.getReceiverPhone())
                .receiverCountry(entity.getReceiverCountry())
                .receiverState(entity.getReceiverState())
                .receiverCity(entity.getReceiverCity())
                .receiverAddress(entity.getReceiverAddress())
                .receiverZip(entity.getReceiverZip())
                .currency(entity.getCurrency())
                .exchangeRate(entity.getExchangeRate())
                .orderAmount(entity.getOrderAmount())
                .shippingFee(entity.getShippingFee())
                .discountAmount(entity.getDiscountAmount())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** 携带明细的副本(wither,docs/07 §1):详情接口构造后补 items 用,列表接口不调——record 不可变的"构造后补字段"等价写法 */
    public ShopOrderResponse withItems(List<ShopOrderItemResponse> items) {
        return new ShopOrderResponse(id, shopId, platform, platformOrderId, orderStatus, fulfillmentChannel,
                orderTime, paidTime, buyerNote, receiverName, receiverPhone, receiverCountry, receiverState,
                receiverCity, receiverAddress, receiverZip, currency, exchangeRate, orderAmount, shippingFee,
                discountAmount, createdAt, updatedAt, items);
    }
}
