package com.own.erp.goods.controller;

import com.own.erp.common.api.Result;
import com.own.erp.goods.request.command.ProductCategorySaveRequest;
import com.own.erp.goods.service.ProductCategoryService;
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

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 商品分类管理:分类域整域收口(docs/07 §2.1)——树组装与 CRUD 一律走 ProductCategoryService。
 *     API 模型收口(docs/07 §1):入参 command/ProductCategorySaveRequest,出参为树节点 VO(CategoryNode),
 *     entity 不出 Service 层
 */
@Tag(name = "商品分类", description = "分类 CRUD 与树查询")
@RestController
@RequestMapping("/api/goods/categories")
@RequiredArgsConstructor
public class ProductCategoryController {

    private final ProductCategoryService categoryService;

    @Operation(summary = "全量分类树", description = "一次查全量内存组树,按 sort 升序,parent_id=0 为根")
    @GetMapping("/tree")
    public Result<List<ProductCategoryService.CategoryNode>> tree() {
        return Result.ok(categoryService.tree());
    }

    @Operation(summary = "新增分类", description = "parentId 不传按根处理")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ProductCategorySaveRequest request) {
        return Result.ok(categoryService.create(request));
    }

    @Operation(summary = "更新分类", description = "MP updateById 忽略 null 字段,可部分更新;TODO#7 成环校验待补")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProductCategorySaveRequest request) {
        categoryService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除分类", description = "一期硬删;TODO#7 后有子分类/商品引用时拦截")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
