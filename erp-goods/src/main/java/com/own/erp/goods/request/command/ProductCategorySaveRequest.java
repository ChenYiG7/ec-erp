package com.own.erp.goods.request.command;

import com.own.erp.goods.entity.ProductCategory;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 商品分类写侧入参(docs/07 §1:XxxSaveRequest,创建/更新共用,id 由路径携带不入参)。
 *     刻意不含 id/createdAt/updatedAt(服务端);parentId 不传按根节点(parent_id=0)处理
 */
@Builder
public record ProductCategorySaveRequest(

        /** 父分类ID,根节点为 0(未传按根处理) */
        Long parentId,

        /** 分类名称 */
        @NotBlank(message = "分类名称不能为空")
        String name,

        /** 同级排序,小在前 */
        Integer sort,

        /** 1=启用 0=禁用 */
        Integer status
) {

    /** 请求 → 实体显式逐字段映射(禁反射拷贝);update 时 id 由 Service 从路径参数回填 */
    public ProductCategory toEntity() {
        ProductCategory category = new ProductCategory();
        category.setParentId(parentId);
        category.setName(name);
        category.setSort(sort);
        category.setStatus(status);
        return category;
    }
}
