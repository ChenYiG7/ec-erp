package com.own.erp.contract;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 发货只读查询契约(#6 tools 扩容,#11 发货域数据面已齐):erp-ai 工具取数唯一正道
 *         (铁律 2,禁横向依赖 erp-fulfill),实现收口 erp-api(DeliveryQueryApiImpl,
 *         委托 DeliveryOrderService.page/getById)。参数/返回全 record 不引 MP 类型;
 *         行视图只带 AI 查询所需字段(waybillUrl 文件地址无消费场景不出契约);AI 只读(铁律 7)
 */
public interface DeliveryQueryApi {

    /**
     * 发货单分页查询(按 id 倒序,服务端口径);过滤条件全空 = 全量分页。
     * 分页大小钳制 1..100(工具侧足够,服务端 PageQuery ≤500 兜底)
     */
    QueryPage<DeliveryView> pageDeliveries(DeliveryFilter filter);

    /** 发货单详情(带发货明细行,答"这单发了多少/发没发"走这里;不存在返回 null) */
    DeliveryDetail getDeliveryDetail(Long deliveryId);

    /**
     * 过滤条件 + 分页入参(全 record):pageNo/pageSize 为 int,@Builder 不设时默认 0,
     * 经 page()/size() 归一后生效(AI 侧漏传分页按第 1 页 / 20 条执行)
     */
    @Builder
    record DeliveryFilter(

            /** 发货单号(模糊,可空) */
            String deliveryNo,

            /** 平台订单ID(shop_order.id,精确,可空) */
            Long orderId,

            /** 店铺ID(shop.id,精确,可空) */
            Long shopId,

            /** 发货单状态:PENDING/SHIPPED/DELIVERED/CANCELLED(精确,可空) */
            String status,

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

    /** 发货单行视图(列表摘要,不含明细) */
    @Builder
    record DeliveryView(

            /** 发货单ID(delivery_order.id) */
            Long id,

            /** 发货单号,唯一 */
            String deliveryNo,

            /** 平台订单ID(shop_order.id) */
            Long orderId,

            /** 店铺ID(shop.id) */
            Long shopId,

            /** 出库仓ID(warehouse.id,ship 时从该仓扣库存) */
            Long warehouseId,

            /** MANUAL手工/WAYBILL电子面单/SUPPLIER供应商代发/FBA/OVERSEAS海外仓 */
            String type,

            /** PENDING待发货/SHIPPED已发货/DELIVERED已签收/CANCELLED已取消 */
            String status,

            /** 物流公司 */
            String logisticsCompany,

            /** 运单号 */
            String trackingNo,

            /** 承诺发货时限(平台侧快照) */
            LocalDateTime shipByTime,

            /** 发货时间(ship 确认时回写;null=未发货) */
            LocalDateTime shippedAt
    ) {
    }

    /** 发货单详情(视图 + 全量明细) */
    @Builder
    record DeliveryDetail(

            /** 发货单主体 */
            DeliveryView order,

            /** 发货明细行 */
            List<Item> items
    ) {

        /** 发货明细行视图 */
        @Builder
        public record Item(

                /** 发货明细ID(delivery_order_item.id) */
                Long deliveryItemId,

                /** 订单明细ID(shop_order_item.id) */
                Long orderItemId,

                /** SKU ID(product_sku.id,冗余自订单明细) */
                Long skuId,

                /** 本单发货数量 */
                Integer shipQty
        ) {
        }
    }
}
