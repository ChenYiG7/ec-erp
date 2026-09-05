package com.own.erp.shop.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.shop.request.query.ShopProductQuery;
import com.own.erp.shop.response.ShopProductResponse;
import com.own.erp.shop.service.ShopProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 店铺商品(listing)查询:ShopProduct 域整域收口,一律走 ShopProductService(docs/07 §2.1)
 *     listing 由拉取任务同步写入(#5),只读;SPU 绑定关系由匹配流程维护
 */
@Tag(name = "店铺商品", description = "店铺 listing 查询(平台商品 ↔ 内部SPU)")
@RestController
@RequestMapping("/api/shop-products")
@RequiredArgsConstructor
public class ShopProductController {

    private final ShopProductService shopProductService;

    @Operation(summary = "分页查询店铺商品")
    @GetMapping
    public Result<Page<ShopProductResponse>> page(ShopProductQuery query) {
        return Result.ok(shopProductService.page(query));
    }

    @Operation(summary = "店铺商品详情", description = "不存在返回 null data")
    @GetMapping("/{id}")
    public Result<ShopProductResponse> get(@PathVariable Long id) {
        return Result.ok(shopProductService.getById(id));
    }
}
