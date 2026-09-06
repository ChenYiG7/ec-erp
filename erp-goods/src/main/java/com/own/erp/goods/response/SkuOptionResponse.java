package com.own.erp.goods.response;

import com.own.erp.goods.entity.Product;
import com.own.erp.goods.entity.ProductSku;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : SKU 选项/翻译结构(#7 专条收口):跨页 skuId 列翻译数据源(库存/采购/入库/发货/售后/订单明细行与 SKU 匹配页)。
 *     product_sku 无名称列,对外可读标识 = skuCode(+ SPU 名称拼接,前端与 SkuSelector 同款 "code · name");
 *     瘦身 Response 只带翻译所需字段,不带金额/跨境面(ProductSkuResponse 才是全量对外结构)
 */
@Builder
public record SkuOptionResponse(

        /** 主键 */
        Long id,

        /** 内部 SKU 编码,唯一 */
        String skuCode,

        /** 所属 SPU 名称(product.name);SPU 已删时为 null(SKU 引用校验理论禁此态,防御兜底) */
        String productName
) {

    /** 双表组装显式映射(product 缺失时名称置 null,行不丢——禁用/已删 SPU 的历史单据仍需翻译出编码) */
    public static SkuOptionResponse from(ProductSku sku, Product product) {
        return SkuOptionResponse.builder()
                .id(sku.getId())
                .skuCode(sku.getSkuCode())
                .productName(product == null ? null : product.getName())
                .build();
    }
}
