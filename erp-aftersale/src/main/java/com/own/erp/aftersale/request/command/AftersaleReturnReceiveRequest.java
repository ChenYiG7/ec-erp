package com.own.erp.aftersale.request.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 收退件 + 退货入库动作入参(#12 复合事务动作,同 #10 confirm/#11 ship 先例):
 *     warehouseId 退货入库仓(动账与 aftersale_order.warehouse_id 回填同源)、items 实收明细(动账凭证)、result 处理结果选填。
 *     校验双保险:注解 400 快速失败 + Service 业务校验收口(docs/07 §1)
 */
@Builder
public record AftersaleReturnReceiveRequest(

        /** 退货入库仓ID(warehouse.id,收退件时必填,回填 aftersale_order.warehouse_id) */
        @NotNull
        Long warehouseId,

        /** 实收退货明细(至少一行;退货类验件录入) */
        @NotEmpty
        @Valid
        List<AftersaleReturnItemRequest> items,

        /** 处理结果(选填,可记验件情况) */
        String result
) {
}
