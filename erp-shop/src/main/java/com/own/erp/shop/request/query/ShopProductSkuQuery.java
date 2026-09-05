package com.own.erp.shop.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : SKU映射分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定;pageNo/pageSize 钳制 ≤500)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShopProductSkuQuery extends PageQuery {

    /** 店铺商品ID(shop_product.id) */
    private Long shopProductId;

    /** 绑定的内部SKU(product_sku.id),NULL=未绑定 */
    private Long skuId;

    /** 0待匹配 1商家编码自动 2人工 */
    private Integer matchStatus;
}
