package com.own.erp.fulfill.request.command;

import com.own.erp.fulfill.entity.DeliveryOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 发货单写侧入参(docs/07 §1:XxxSaveRequest,创建/更新共用,id 由路径携带不入参)。
 *     #11 激活改造:status/shippedAt 服务端管理、shopId 按订单回填、skuId 按订单明细回填,均从入参剔除;
 *     发货明细整单提交,更新时整体替换;
 *     createdBy 亦入参剔除(#11 遗留收口 2026-09-06):Service 经 CurrentUserApi 按 SecurityContext 服务端回填
 */
@Builder
public record DeliveryOrderSaveRequest(

        /** 发货单号,唯一 */
        @NotBlank
        @Size(max = 64)
        String deliveryNo,

        /** 平台订单ID(shop_order.id),须为待发货且卖家自履约(SELF_FULFILL) */
        @NotNull
        Long orderId,

        /** 出库仓ID(warehouse.id),ship 时从该仓扣库存 */
        @NotNull
        Long warehouseId,

        /** MANUAL手工/WAYBILL电子面单/SUPPLIER供应商代发/FBA/OVERSEAS海外仓 */
        String type,

        /** 承诺发货时限(平台侧快照,人工录入;随 #3 拉单可回填) */
        LocalDateTime shipByTime,

        /** 物流公司 */
        @Size(max = 64)
        String logisticsCompany,

        /** 运单号,唯一(可多条 NULL) */
        @Size(max = 64)
        String trackingNo,

        /** 电子面单文件地址 */
        @Size(max = 512)
        String waybillUrl,

        /** 发货明细(创建/更新整单提交;仅 sku_id 已绑定的订单明细行) */
        @NotEmpty
        @Valid
        List<DeliveryOrderItemSaveRequest> items
) {

    /** 请求 → 实体显式逐字段映射(禁反射拷贝);status/shopId/createdBy 由 Service 服务端回填,不入映射 */
    public DeliveryOrder toEntity() {
        return DeliveryOrder.builder()
                .deliveryNo(deliveryNo)
                .orderId(orderId)
                .warehouseId(warehouseId)
                .type(type)
                .shipByTime(shipByTime)
                .logisticsCompany(logisticsCompany)
                .trackingNo(trackingNo)
                .waybillUrl(waybillUrl)
                .build();
    }
}
