package com.own.erp.goods.request.command;

import com.own.erp.goods.entity.Product;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 商品 SPU 写侧入参(docs/07 §1:XxxSaveRequest,创建/更新共用,id 由路径携带不入参)。
 *     刻意不含 id/createdAt/updatedAt(服务端管理)
 */
@Builder
public record ProductSaveRequest(

        /** 内部 SPU 编码,唯一 */
        @NotBlank(message = "SPU编码不能为空")
        String spuCode,

        /** 商品名称 */
        String name,

        /** 分类ID(product_category.id) */
        Long categoryId,

        /** 品牌ID(brand.id) */
        Long brandId,

        /** 销售属性(JSON),如 [{"name":"颜色","values":["黑","白"]}] */
        String attrsJson,

        /** 1=在售 0=停用 */
        Integer status
) {

    /** 请求 → 实体显式逐字段映射(禁反射拷贝);update 时 id 由 Service 从路径参数回填 */
    public Product toEntity() {
        Product product = new Product();
        product.setSpuCode(spuCode);
        product.setName(name);
        product.setCategoryId(categoryId);
        product.setBrandId(brandId);
        product.setAttrsJson(attrsJson);
        product.setStatus(status);
        return product;
    }
}
