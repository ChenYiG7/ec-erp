package com.own.erp.contract;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 订单只读查询契约(#6 三期 AI 地基):erp-ai 工具取数唯一正道(铁律 2,禁横向依赖 erp-order),
 *         实现收口 erp-api(OrderQueryApiImpl,委托 ShopOrderService.page/getById)。
 *         参数/返回全 record 不引 MP 类型;行视图只带 AI 查询所需字段,收件人地址/buyer_note/raw_json
 *         不出契约(后续按工具需要再扩,契约演进只加字段不改语义);AI 只读,@Tool 侧禁写操作(铁律 7)
 */
public interface OrderQueryApi {

    /**
     * 订单分页查询(按下单时间倒序,服务端口径);过滤条件全空 = 全量分页。
     * 分页大小钳制 1..100(工具侧足够,服务端 PageQuery ≤500 兜底)
     */
    QueryPage<OrderView> pageOrders(OrderFilter filter);

    /** 订单详情(带全部明细行,含未绑定 SKU 行;不存在返回 null)。AI 答"订单买了什么"走这里 */
    OrderDetail getOrderDetail(Long orderId);

    /**
     * 过滤条件 + 分页入参(全 record):pageNo/pageSize 为 int,@Builder 不设时默认 0,
     * 经 page()/size() 归一后生效(AI 侧漏传分页按第 1 页 / 20 条执行)
     */
    @Builder
    record OrderFilter(

            /** 店铺ID(shop.id,精确,可空) */
            Long shopId,

            /** 平台(PlatformType 枚举名,精确,可空) */
            String platform,

            /** 订单状态(WAIT_PAY/WAIT_SHIP/SHIPPED/COMPLETED/CANCELLED/CLOSED,精确,可空) */
            String orderStatus,

            /** 页码(从 1 起) */
            int pageNo,

            /** 页大小 */
            int pageSize
    ) {

        /** 归一页码(<1 按 1) */
        public int page() {
            return Math.max(pageNo, 1);
        }

        /** 归一页大小(未传/非法按默认 20,上限 100) */
        public int size() {
            return pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        }
    }

    /** 订单行视图(列表摘要,不含明细) */
    @Builder
    record OrderView(

            /** 订单ID(shop_order.id) */
            Long id,

            /** 店铺ID(shop.id) */
            Long shopId,

            /** PlatformType 枚举名:TAOBAO/AMAZON/... */
            String platform,

            /** 平台订单号((shop_id, platform_order_id) 幂等唯一键) */
            String platformOrderId,

            /** WAIT_PAY/WAIT_SHIP/SHIPPED/COMPLETED/CANCELLED/CLOSED */
            String orderStatus,

            /** 履约渠道:SELF_FULFILL/FBA/OVERSEAS_WAREHOUSE */
            String fulfillmentChannel,

            /** 下单时间(平台侧) */
            LocalDateTime orderTime,

            /** 支付时间 */
            LocalDateTime paidTime,

            /** 币种(ISO 4217) */
            String currency,

            /** 下单日汇率快照(原币→本位币) */
            BigDecimal exchangeRate,

            /** 订单总金额(原币) */
            BigDecimal orderAmount,

            /** 运费(原币) */
            BigDecimal shippingFee,

            /** 优惠金额(原币) */
            BigDecimal discountAmount
    ) {
    }

    /** 订单详情(视图 + 全量明细;与 findDeliveryView 不同,不过滤未绑定 SKU 行——AI 只读视图看全貌) */
    @Builder
    record OrderDetail(

            /** 订单主体 */
            OrderView order,

            /** 订单明细行(落库原样) */
            List<Item> items
    ) {

        /** 订单明细行视图 */
        @Builder
        public record Item(

                /** 订单明细ID(shop_order_item.id) */
                Long orderItemId,

                /** 内部SKU ID(product_sku.id),未绑定则 null */
                Long skuId,

                /** 平台侧 SKU 标识(seller_sku 等) */
                String platformSku,

                /** 平台商品名 */
                String productName,

                /** 购买数量 */
                Integer quantity,

                /** 单价(原币) */
                BigDecimal unitPrice,

                /** 行小计(原币) */
                BigDecimal itemAmount,

                /** 币种(ISO 4217) */
                String currency
        ) {
        }
    }
}
