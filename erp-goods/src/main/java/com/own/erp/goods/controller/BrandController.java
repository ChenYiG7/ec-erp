package com.own.erp.goods.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.PageQuery;
import com.own.erp.common.api.Result;
import com.own.erp.goods.entity.Brand;
import com.own.erp.goods.mapper.BrandMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 品牌 CRUD。品牌域=纯配置表(docs/07 §2.1):无 Service、可预见不生长逻辑,Controller 直连 Mapper 合规;
 *     长出第一条规则时须建 Service 整域下沉
 */
@Tag(name = "品牌管理", description = "品牌 CRUD")
@RestController
@RequestMapping("/api/goods/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandMapper brandMapper;

    @Operation(summary = "分页查询品牌")
    @GetMapping
    public Result<Page<Brand>> page(PageQuery query) {
        return Result.ok(brandMapper.selectPage(
                new Page<>(query.getPageNo(), query.pageSize()),
                new LambdaQueryWrapper<Brand>().orderByAsc(Brand::getId)));
    }

    @Operation(summary = "新增品牌")
    @PostMapping
    public Result<Long> create(@RequestBody Brand brand) {
        brandMapper.insert(brand);
        return Result.ok(brand.getId());
    }

    @Operation(summary = "更新品牌", description = "MP updateById 忽略 null 字段,可部分更新")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Brand brand) {
        brand.setId(id);
        brandMapper.updateById(brand);
        return Result.ok();
    }

    @Operation(summary = "删除品牌")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        brandMapper.deleteById(id);
        return Result.ok();
    }
}
