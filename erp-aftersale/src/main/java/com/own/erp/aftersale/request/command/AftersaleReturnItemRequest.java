package com.own.erp.aftersale.request.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 收退件实收明细行入参(#12 退货入库,2026-09-04 拍板:实收人工录入,可≠平台申明)。
 *     skuId 不入参——服务端按 orderItemId 从订单发货视图回填(防乱传 SKU 串单,同 #11 发货明细回填先例);
 *     归属/数量预校验收口 Service,注解只做 400 快速失败(docs/07 §1 CQRS command 分包)
 */
@Builder
public record AftersaleReturnItemRequest(

        /** 订单明细ID(shop_order_item.id,SKU 归属锚点;须为该订单 sku_id 已绑定行) */
        @NotNull
        Long orderItemId,

        /** 实收退货数量(正数,仓库验件录入) */
        @NotNull
        @Positive
        Integer returnQty
) {
}
