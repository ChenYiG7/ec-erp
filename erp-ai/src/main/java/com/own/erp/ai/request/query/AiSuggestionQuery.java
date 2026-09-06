package com.own.erp.ai.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AI建议表分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定)
 *     过滤条件对齐 idx_type_status/idx_sku 索引(建议闭环页按类型/状态筛待处理项)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiSuggestionQuery extends PageQuery {

    /** 关联店铺ID(shop.id,精确) */
    private Long shopId;

    /** 关联内部SKU ID(product_sku.id,精确) */
    private Long skuId;

    /** 建议类型(精确,REPLENISH/PRICING/ANOMALY/COPYWRITING) */
    private String suggestionType;

    /** 确认状态(精确,0待确认/1已采纳/2已忽略) */
    private Integer status;
}
