package com.own.erp.system.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.PageQuery;
import com.own.erp.common.api.Result;
import com.own.erp.system.entity.SysDict;
import com.own.erp.system.mapper.SysDictMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.groups.Default;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 字典 CRUD(含按类型取全量,供下拉框)。
 *     字典域=纯配置表(docs/07 §2.1):无 Service、可预见不生长逻辑,Controller 直连 Mapper 合规;
 *     长出第一条规则时须建 Service 整域下沉
 */
@Tag(name = "字典管理", description = "数据字典 CRUD 与按类型查询(下拉数据源)")
@RestController
@RequestMapping("/api/system/dicts")
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictMapper dictMapper;

    @Operation(summary = "分页查询字典", description = "dictType 精确过滤(可选),按类型+sort 排序")
    @GetMapping
    public Result<Page<SysDict>> page(PageQuery query,
                                      @RequestParam(required = false) String dictType) {
        return Result.ok(dictMapper.selectPage(
                new Page<>(query.getPageNo(), query.pageSize()),
                new LambdaQueryWrapper<SysDict>()
                        .eq(StrUtil.isNotBlank(dictType), SysDict::getDictType, dictType)
                        .orderByAsc(SysDict::getDictType)
                        .orderByAsc(SysDict::getSort)));
    }

    @Operation(summary = "按类型取启用字典项", description = "下拉框数据源;status=1")
    @GetMapping("/type/{dictType}")
    public Result<List<SysDict>> listByType(@PathVariable String dictType) {
        return Result.ok(dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictType, dictType)
                .eq(SysDict::getStatus, 1)
                .orderByAsc(SysDict::getSort)));
    }

    @Operation(summary = "新增字典项")
    @PostMapping
    public Result<Long> create(@Validated({Default.class, SysDict.Create.class}) @RequestBody SysDict dict) {
        dictMapper.insert(dict);
        return Result.ok(dict.getId());
    }

    @Operation(summary = "更新字典项", description = "MP updateById 忽略 null 字段,可部分更新(Default 组 @Size 对 null 放行,超长仍拦)")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SysDict dict) {
        dict.setId(id);
        dictMapper.updateById(dict);
        return Result.ok();
    }

    @Operation(summary = "删除字典项")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dictMapper.deleteById(id);
        return Result.ok();
    }
}
