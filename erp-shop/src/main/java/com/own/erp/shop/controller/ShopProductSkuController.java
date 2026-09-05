package com.own.erp.shop.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.shop.request.command.SkuBindRequest;
import com.own.erp.shop.request.query.ShopProductSkuQuery;
import com.own.erp.shop.response.ShopProductSkuResponse;
import com.own.erp.shop.service.ShopProductSkuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : SKU映射:ShopProductSku 域整域收口,一律走 ShopProductSkuService(docs/07 §2.1)
 *     行数据由 listing 同步写入(#5),对外只读 + 人工绑定动作
 */
@Tag(name = "SKU映射", description = "平台 seller_sku ↔ 内部SKU 绑定(系统心脏)")
@RestController
@RequestMapping("/api/shop-product-skus")
@RequiredArgsConstructor
public class ShopProductSkuController {

    private final ShopProductSkuService shopProductSkuService;

    @Operation(summary = "分页查询SKU映射", description = "match_status=0 即待匹配列表")
    @GetMapping
    public Result<Page<ShopProductSkuResponse>> page(ShopProductSkuQuery query) {
        return Result.ok(shopProductSkuService.page(query));
    }

    @Operation(summary = "SKU映射详情", description = "不存在返回 null data")
    @GetMapping("/{id}")
    public Result<ShopProductSkuResponse> get(@PathVariable Long id) {
        return Result.ok(shopProductSkuService.getById(id));
    }

    @Operation(summary = "人工绑定内部SKU", description = "回填 sku_id 并置 match_status=2;重复绑定同一SKU幂等")
    @PutMapping("/{id}/bind")
    @PreAuthorize("hasRole('admin')")
    public Result<Void> bind(@PathVariable Long id, @Valid @RequestBody SkuBindRequest request) {
        shopProductSkuService.bind(id, request.skuId());
        return Result.ok();
    }
}
