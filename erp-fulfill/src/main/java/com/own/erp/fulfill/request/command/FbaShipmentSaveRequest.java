package com.own.erp.fulfill.request.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : FBA 发货单写侧聚合入参(docs/plans/fba-shipment.md):主单表头 + 计划行(SKU 清单)+
 *     装箱(箱 + 箱内件)整体保存,创建/更新共用(id 由路径携带不入参);仅 DRAFT 可保存(BOXED 起冻结,改单走取消重建)。
 *     record+@Builder(docs/07 §1 分级④);嵌套记录经 @Valid 级联校验;
 *     不含 shipment_no(服务端 FB 生成)/状态/shipped_at/received_at(动作时点服务端写)/服务端管理列(防 mass assignment)
 */
@Builder
public record FbaShipmentSaveRequest(

        /** 店铺ID(shop.id,归属信息,前端店铺下拉选择;V1 不做后端存在性校验) */
        @NotNull(message = "店铺必填")
        Long shopId,

        /** 站点(如 US/UK/DE) */
        @NotBlank(message = "站点不能为空")
        String marketplace,

        /** 国内发货仓ID(必须 wh_type=SELF,SHIPPED 出库动账仓) */
        @NotNull(message = "国内发货仓必填")
        Long warehouseId,

        /** 平台 ShipmentId(V2 SP-API 回填,V1 手填可空) */
        String platformShipmentId,

        /** 备注 */
        String remark,

        /** 计划行(SKU 清单,SHIPPED 装箱勾稽基准;至少 1 行) */
        @Valid
        @NotNull(message = "计划行不能为空")
        List<PlanItemSave> planItems,

        /** 装箱明细(箱+内件;草稿阶段允许空——先建计划后装箱,装箱完成动作守卫逐 SKU 等于计划量) */
        @Valid
        List<BoxSave> boxes

) {

    /** 计划行:同单同 SKU 必须合并为一行(Service 守卫) */
    @Builder
    public record PlanItemSave(

            /** SKU ID(product_sku.id) */
            @NotNull(message = "计划行SKU必填")
            Long skuId,

            /** 计划发货数量(大于 0) */
            @NotNull(message = "计划数量必填")
            @Min(value = 1, message = "计划数量必须大于0")
            Integer planQty

    ) {
    }

    /** 箱:箱号单内唯一,毛重 kg/外箱尺寸 cm 为装箱记录面 */
    @Builder
    public record BoxSave(

            /** 箱号(单内唯一,禁空) */
            @NotBlank(message = "箱号不能为空")
            String boxNo,

            /** 整箱实重(毛重)kg,可空(非负) */
            @Min(value = 0, message = "箱重不能为负")
            BigDecimal weight,

            /** 外长 cm(正整数) */
            @Min(value = 1, message = "外长必须大于0")
            Integer lengthCm,

            /** 外宽 cm(正整数) */
            @Min(value = 1, message = "外宽必须大于0")
            Integer widthCm,

            /** 外高 cm(正整数) */
            @Min(value = 1, message = "外高必须大于0")
            Integer heightCm,

            /** 箱内件(每箱至少 1 行) */
            @Valid
            @NotNull(message = "箱内件不能为空")
            List<BoxItemSave> items

    ) {
    }

    /** 箱内件:同一箱内同一 SKU 必须合并为一行(Service 守卫) */
    @Builder
    public record BoxItemSave(

            /** SKU ID(product_sku.id) */
            @NotNull(message = "箱内件SKU必填")
            Long skuId,

            /** 件数(大于 0) */
            @NotNull(message = "箱内件数必填")
            @Min(value = 1, message = "箱内件数必须大于0")
            Integer quantity

    ) {
    }
}
