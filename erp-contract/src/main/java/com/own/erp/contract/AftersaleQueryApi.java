package com.own.erp.contract;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 售后单只读查询契约(#6 三期 AI 地基):erp-ai 工具取数唯一正道(铁律 2,禁横向依赖 erp-aftersale),
 *         实现收口 erp-api(AftersaleQueryApiImpl,委托 AftersaleOrderService.page)。
 *         参数/返回全 record 不引 MP 类型;售后单状态推进(人工五动作)走 erp-aftersale 自身接口,
 *         本契约只读(AI 只读,铁律 7);退货明细不在此(仅详情接口携带,分页不查子表,口径同域内 Response)
 */
public interface AftersaleQueryApi {

    /** 售后单分页查询(过滤条件全空 = 全量分页);分页大小钳制 1..100(服务端 PageQuery ≤500 兜底) */
    QueryPage<AftersaleView> pageAftersales(AftersaleFilter filter);

    /**
     * 过滤条件 + 分页入参:四维过滤均可空(对齐域内 AftersaleOrderQuery);pageNo/pageSize 为 int,
     * @Builder 不设时默认 0,经 page()/size() 归一后生效
     */
    @Builder
    record AftersaleFilter(

            /** 店铺ID(shop.id,精确,可空) */
            Long shopId,

            /** 状态(PENDING/APPROVED/RETURNING/RETURN_RECEIVED/REFUNDED/COMPLETED/REJECTED/CANCELLED,精确,可空) */
            String status,

            /** 售后类型(REFUND_ONLY/RETURN_REFUND/EXCHANGE/RESEND,精确,可空) */
            String type,

            /** 关联订单ID(shop_order.id,精确,可空) */
            Long orderId,

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

    /** 售后单行视图(不含退货明细与 platform_refund_id 幂等键——AI 查询无需) */
    @Builder
    record AftersaleView(

            /** 售后单ID(aftersale_order.id) */
            Long id,

            /** 售后单号,唯一 */
            String aftersaleNo,

            /** 店铺ID(shop.id) */
            Long shopId,

            /** 关联订单ID(shop_order.id) */
            Long orderId,

            /** 退货入库仓ID(warehouse.id,收退件时回填,未收退件为 null) */
            Long warehouseId,

            /** REFUND_ONLY仅退款/RETURN_REFUND退货退款/EXCHANGE换货/RESEND补发 */
            String type,

            /** PENDING/APPROVED/RETURNING/RETURN_RECEIVED/REFUNDED/COMPLETED/REJECTED/CANCELLED */
            String status,

            /** 退款金额(原币) */
            BigDecimal refundAmount,

            /** 币种(ISO 4217) */
            String currency,

            /** 售后原因 */
            String reason,

            /** 处理结果 */
            String result,

            /** 创建时间 */
            LocalDateTime createdAt
    ) {
    }
}
