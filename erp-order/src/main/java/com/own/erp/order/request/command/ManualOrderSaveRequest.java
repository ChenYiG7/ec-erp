package com.own.erp.order.request.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 内销订单手工录单写侧入参(#29 订单域补课,创建/更新共用;id 由路径携带不入参)。
 *     平台/单号/状态/来源/金额/审核态均由服务端派生,故一律不入参(服务端管理列,docs/07 §1):
 *     - platform 取店铺真实平台;platform_order_id 用合成号 MAN-{shopId}-{yyyyMMdd}-{4位seq};
 *     - order_status 固定 WAIT_SHIP、order_source 固定 MANUAL;
 *     - order_amount = Σ(单价×数量) 服务端计算,shipping_fee/discount_amount 归零;
 *     - review_status 按风控判定(地址完整性 + 留言关键词),与平台拉单同一判定器。
 *     收货六列全部必填(内销单人工录入,不背"地址不完整"风险)
 */
@Builder
public record ManualOrderSaveRequest(

        /** 店铺ID(shop.id,取其真实平台作为订单 platform) */
        @NotNull
        Long shopId,

        /** 买家留言(可空;命中风控关键词则进待审核) */
        @Size(max = 512)
        String buyerNote,

        /** 收货人姓名 */
        @NotBlank
        @Size(max = 64)
        String receiverName,

        /** 收货人电话 */
        @NotBlank
        @Size(max = 32)
        String receiverPhone,

        /** 收货国家(ISO 3166,如 CN) */
        @NotBlank
        @Size(max = 8)
        String receiverCountry,

        /** 收货省/州(可空) */
        @Size(max = 64)
        String receiverState,

        /** 收货城市 */
        @NotBlank
        @Size(max = 64)
        String receiverCity,

        /** 收货详细地址 */
        @NotBlank
        @Size(max = 512)
        String receiverAddress,

        /** 收货邮编 */
        @NotBlank
        @Size(max = 32)
        String receiverZip,

        /** 币种(ISO 4217;空=CNY 本币) */
        @Size(max = 3)
        String currency,

        /** 手工录入汇率快照(空=1,内销本币场景) */
        BigDecimal exchangeRate,

        /** 订单明细(整单提交;更新时整体替换) */
        @NotEmpty
        @Valid
        List<ManualOrderItemSaveRequest> items
) {
}
