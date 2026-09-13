package com.own.erp.order.event;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026-09-12
 * @Description : 订单审核通过事件(#29 余量「自动拆单建议」,2026-09-12 方案 A 拍板):
 *     ShopOrderService.review 通过分支发布(cas 占位成功后),erp-api 监听器消费桥接 erp-ai
 *     SplitAdviceService(erp-order 禁依赖 erp-ai、erp-ai 禁依赖 erp-order,铁律 2——事件介体解耦,
 *     同 AnomalyHighRiskEvent/NotifyPushedEvent 先例)。只带订单号/店铺/明细(skuId+数量),
 *     无买家 PII(红线)。
 */
public record OrderReviewedEvent(

        /** 订单ID(shop_order.id) */
        Long orderId,

        /** 店铺ID(shop.id) */
        Long shopId,

        /** 平台订单号(建议摘要可读) */
        String platformOrderId,

        /** 订单明细行(拆仓建议的分组输入) */
        List<Item> items
) {

    /** 明细项:SKU + 数量(拆仓分组最小集) */
    public record Item(
            Long skuId,
            Integer quantity
    ) {
    }
}
