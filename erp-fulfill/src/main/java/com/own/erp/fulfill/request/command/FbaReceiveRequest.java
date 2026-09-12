package com.own.erp.fulfill.request.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : FBA 发货单「收货登记」入参(docs/plans/fba-shipment.md):SHIPPED→RECEIVING 首登 /
 *     RECEIVING 重复登记覆盖(先删后插幂等),按 SKU 录平台收货数量。
 *     发出 SKU 必须全部在列(未登记按 0 计=SHORT,由前端预填保证完整,后端守卫缺行即拦防半量登记失真);
 *     diff 三态 SHORT/EXTRA/OK 由 Service 生成
 */
@Builder
public record FbaReceiveRequest(

        /** 平台收货明细(每个发出 SKU 一行,收货量 >= 0;可含计划外 SKU=EXTRA 多收) */
        @NotNull(message = "收货明细不能为空")
        @Valid
        List<ReceiveItem> items

) {

    /** 收货行:同 SKU 重复行 Service 守卫合并拒绝 */
    @Builder
    public record ReceiveItem(

            /** SKU ID(product_sku.id) */
            @NotNull(message = "收货行SKU必填")
            Long skuId,

            /** 平台收货数量(>= 0) */
            @NotNull(message = "收货数量必填")
            @Min(value = 0, message = "收货数量不能为负")
            Integer receivedQty

    ) {
    }
}
