package com.own.erp.finance.request.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 头程发货单写侧聚合入参(#33):主单表头 + 装箱(箱 + 箱内件)整体保存,
 *     创建/更新共用(id 由路径携带);仅 DRAFT 可保存(箱内容在 BOXED 后冻结,改单走取消重建)。
 *     record+@Builder(docs/07 §1 分级④);嵌套记录经 @Valid 级联校验;
 *     不含 shipment_no(服务端 FL 生成)/状态/运费列(运费走 ship 动作)/服务端管理列(防 mass assignment)
 */
@Builder
public record FirstLegShipmentSaveRequest(

        /** 国内发货仓ID(必须 wh_type=SELF) */
        @NotNull(message = "国内发货仓必填")
        Long fromWarehouseId,

        /** 目的仓ID(必须 wh_type=OVERSEAS/FBA) */
        @NotNull(message = "目的仓必填")
        Long toWarehouseId,

        /** 分摊策略:QTY/WEIGHT/AMOUNT,空=WEIGHT(发货前可改) */
        String allocateStrategy,

        /** 备注 */
        String remark,

        /** 装箱明细(箱+内件;草稿阶段允许空——先建单后装箱,装箱完成动作守卫至少 1 箱有件) */
        @Valid
        List<BoxSave> boxes

) {

    /** 箱:箱号单内唯一,外箱尺寸 cm/毛重 kg 为装箱记录面(WEIGHT 分摊只用 product_sku.weight_g) */
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
