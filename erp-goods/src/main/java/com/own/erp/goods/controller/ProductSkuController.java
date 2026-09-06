package com.own.erp.goods.controller;

import com.own.erp.common.api.Result;
import com.own.erp.goods.request.command.ProductSkuSaveRequest;
import com.own.erp.goods.response.ProductSkuResponse;
import com.own.erp.goods.response.SkuOptionResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 商品 SKU 管理:商品域整域收口(docs/07 §2.1)——读写一律走 ProductService,本类不注入 Mapper。
 *     API 模型收口(docs/07 §1):入参 command/ProductSkuSaveRequest,出参 response/ProductSkuResponse,
 *     entity 不出 Service 层。与平台 seller_sku 的绑定(shop_product_sku)见 TODO.md #5
 */
@Tag(name = "商品SKU", description = "SKU CRUD;与平台 seller_sku 的绑定/自动匹配见 TODO.md #5")
@RestController
@RequestMapping("/api/goods/skus")
@RequiredArgsConstructor
public class ProductSkuController {

    private final ProductService productService;

    @Operation(summary = "查询 SPU 下的 SKU 列表")
    @GetMapping
    public Result<List<ProductSkuResponse>> list(@RequestParam Long productId) {
        return Result.ok(productService.listSkusByProductId(productId));
    }

    @Operation(summary = "批量按 ID 查 SKU", description = "跨页 skuId 列翻译数据源(#7 专条):"
            + "库存/采购/入库/发货/售后/订单明细行与 SKU 匹配页;查无的 ID 不在结果中,前端回落显示裸 ID")
    @GetMapping("/batch")
    public Result<List<SkuOptionResponse>> listBatch(@RequestParam List<Long> ids) {
        return Result.ok(productService.listSkuOptions(ids));
    }

    @Operation(summary = "新增 SKU", description = "TODO#5:skuCode 全局唯一校验待补")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ProductSkuSaveRequest request) {
        return Result.ok(productService.createSku(request));
    }

    @Operation(summary = "更新 SKU", description = "MP updateById 忽略 null 字段,可部分更新")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProductSkuSaveRequest request) {
        productService.updateSku(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除 SKU", description = "一期硬删;TODO#5 后校验 shop_product_sku 绑定与库存引用")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.deleteSku(id);
        return Result.ok();
    }
}
