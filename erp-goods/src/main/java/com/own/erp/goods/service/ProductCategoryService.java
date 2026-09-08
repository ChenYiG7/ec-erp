package com.own.erp.goods.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.goods.entity.Product;
import com.own.erp.goods.entity.ProductCategory;
import com.own.erp.goods.mapper.ProductCategoryMapper;
import com.own.erp.goods.mapper.ProductMapper;
import com.own.erp.goods.request.command.ProductCategorySaveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 商品分类服务:树组装 + CRUD(分类域整域收口——树组装属业务逻辑,Controller 不直连 Mapper,docs/07 §2.1)。
 *     API 模型收口(docs/07 §1):入参 command/ProductCategorySaveRequest,出参 CategoryNode(树节点,record),
 *     entity 不出本层。一次查全量内存组树,分类量级下足够。
 *     #7 收口(2026-09-04):新增/更新父分类存在性 + 成环校验(自身/自身子孙禁挂),删除前子分类与商品引用拦截
 */
@Service
@RequiredArgsConstructor
public class ProductCategoryService {

    private final ProductCategoryMapper categoryMapper;
    private final ProductMapper productMapper;

    /** 分类树节点(对外出参):实体字段裁剪 + children 装配;record(docs/07 §1 模型可变性分级),叶子 children 为空列表 */
    public record CategoryNode(Long id, Long parentId, String name, Integer sort, Integer status,
                               List<CategoryNode> children) {
    }

    /** 全量分类树(按 sort 升序,parent_id=0 为根) */
    public List<CategoryNode> tree() {
        List<ProductCategory> all = categoryMapper.selectList(
                new LambdaQueryWrapper<ProductCategory>().orderByAsc(ProductCategory::getSort));
        return buildTree(all, 0L);
    }

    /** 类目名称精确查(单条;不存在返回 null——#17 文案生成 prompt 材料,2026-09-08 加) */
    public String findNameById(Long id) {
        if (id == null) {
            return null;
        }
        ProductCategory category = categoryMapper.selectById(id);
        return category == null ? null : category.getName();
    }

    /** 内存引用组树:子节点在父的 children 里递归装配;数据量级小,牺牲递归换可读 */
    private List<CategoryNode> buildTree(List<ProductCategory> all, Long parentId) {
        List<CategoryNode> nodes = new ArrayList<>();
        for (ProductCategory c : all) {
            if (parentId.equals(c.getParentId())) {
                nodes.add(new CategoryNode(c.getId(), c.getParentId(), c.getName(), c.getSort(), c.getStatus(),
                        buildTree(all, c.getId())));
            }
        }
        return nodes;
    }

    /** 新增分类:parentId 未传按根(parent_id=0)处理;非根父分类必须存在(#7,防孤儿节点) */
    public Long create(ProductCategorySaveRequest request) {
        ProductCategory category = request.toEntity();
        if (category.getParentId() == null) {
            category.setParentId(0L);
        }
        requireParentUsable(null, category.getParentId());
        categoryMapper.insert(category);
        return category.getId();
    }

    /**
     * 更新分类(MP 忽略 null 可部分更新)。
     * #7 收口:parentId 变更时父分类必须存在,且不得挂到自己或自己的子孙分类下(成环)
     */
    public void update(Long id, ProductCategorySaveRequest request) {
        requireParentUsable(id, request.parentId());
        ProductCategory category = request.toEntity();
        category.setId(id);
        categoryMapper.updateById(category);
    }

    /**
     * 父分类可用性(#7):根(0)放行;非根沿父链逐级上走——链上出现 excludeId 即成环(自己/自己子孙禁挂),
     * 分类不存在即拒;visited 集合兼防存量脏数据已成环时死循环。纯查询,分类量级小开销可忽略
     */
    private void requireParentUsable(Long excludeId, Long parentId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null && cursor != 0L) {
            if (!visited.add(cursor)) {
                throw new BusinessException("父分类链已存在环,请先修复分类数据");
            }
            if (cursor.equals(excludeId)) {
                throw new BusinessException("不能把分类挂到自己或自己的子孙分类下");
            }
            ProductCategory parent = categoryMapper.selectById(cursor);
            if (parent == null) {
                throw new BusinessException("父分类不存在: " + cursor);
            }
            cursor = parent.getParentId();
        }
    }

    /**
     * 删除分类:一期硬删。#7 收口:有子分类或被商品引用(category_id)时禁止删除,先迁移子分类/商品
     */
    public void delete(Long id) {
        Long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("该分类下有 " + childCount + " 个子分类,禁删,先迁移子分类");
        }
        Long productCount = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getCategoryId, id));
        if (productCount != null && productCount > 0) {
            throw new BusinessException("该分类下挂有 " + productCount + " 个商品,禁删,先迁移商品分类");
        }
        categoryMapper.deleteById(id);
    }
}
