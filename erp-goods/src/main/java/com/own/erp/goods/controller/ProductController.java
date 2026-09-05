package com.own.erp.goods.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.goods.request.command.ProductCreateRequest;
import com.own.erp.goods.request.command.ProductSaveRequest;
import com.own.erp.goods.request.query.ProductQuery;
import com.own.erp.goods.response.ProductResponse;
import com.own.erp.goods.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 商品 SPU 管理:商品域整域收口(docs/07 §2.1)——读写一律走 ProductService,本类不注入 Mapper。
 *     API 模型收口(docs/07 §1):入参 query/ProductQuery、command/ProductSaveRequest,出参 response/*Response,
 *     entity 不出 Service 层
 */
@Tag(name = "商品管理", description = "SPU 与 SKU CRUD;SKU 与平台 seller_sku 的映射是系统心脏(TODO.md #5)")
@RestController
@RequestMapping("/api/goods/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "分页查询商品", description = "keyword 模糊匹配商品名(可选),categoryId 精确过滤(可选)")
    @GetMapping
    public Result<Page<ProductResponse>> page(ProductQuery query) {
        return Result.ok(productService.pageProducts(query));
    }

    @Operation(summary = "商品详情", description = "SPU 基础信息 + 全部 SKU 一次带回,结构 {product, skus}")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(productService.getProductDetail(id));
    }

    @Operation(summary = "新增商品", description = "SPU + SKU 列表同事务保存;spuCode 重复报错")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ProductCreateRequest request) {
        return Result.ok(productService.createProduct(request.product(), request.skus()));
    }

    @Operation(summary = "更新商品", description = "MP updateById 忽略 null 字段,可部分更新")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProductSaveRequest request) {
        productService.updateProduct(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除商品", description = "一期硬删;TODO#5 后增加 shop_product_sku/库存/订单引用校验")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.deleteProduct(id);
        return Result.ok();
    }
}
