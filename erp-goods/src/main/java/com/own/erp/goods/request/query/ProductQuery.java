package com.own.erp.goods.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 商品 SPU 分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductQuery extends PageQuery {

    /** 商品名模糊匹配(可选) */
    private String keyword;

    /** 分类ID 精确过滤(可选) */
    private Long categoryId;

    /** 状态过滤 1启用 0停用(可选,空=不过滤;#17 文案生成扫启用商品,2026-09-08 加) */
    private Integer status;
}
