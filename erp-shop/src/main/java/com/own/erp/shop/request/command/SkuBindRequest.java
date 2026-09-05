package com.own.erp.shop.request.command;

import jakarta.validation.constraints.NotNull;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : SKU映射人工绑定入参(回填内部 sku_id,match_status 置 2)
 */
public record SkuBindRequest(@NotNull(message = "内部SKU不能为空") Long skuId) {
}
