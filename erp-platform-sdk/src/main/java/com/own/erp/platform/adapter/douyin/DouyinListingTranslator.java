package com.own.erp.platform.adapter.douyin;

import com.fasterxml.jackson.databind.JsonNode;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.unified.UnifiedProduct;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * @author : chenyi
 * @Date : 2026/9/13
 * @Description : 抖店 /product/listV2 报文 → UnifiedProduct 翻译(防腐层 core,docs/07 §8):
 *         product_list 每个元素 → 商品(含 skus 数组);字段按官方 schema 推导,fixture=推导样例,
 *         真凭证样本到位后 --force 校准(docs/07 §8);金额为分 → 元(与订单口径一致,见 {@link DouyinOrderTranslator});
 *         禁业务 if,状态/编码零翻译逻辑在原报文字段透传(raw_json 兜底)
 */
final class DouyinListingTranslator {

    private DouyinListingTranslator() {
    }

    static UnifiedProduct translateProduct(JsonNode productNode, Long shopId, PlatformType platform) {
        String productId = textOrNull(productNode, "product_id");
        if (productId == null) {
            throw new IllegalArgumentException("抖店商品缺 product_id: " + productNode);
        }
        UnifiedProduct product = UnifiedProduct.builder()
                .platformProductId(productId)
                .shopId(shopId)
                .platform(platform)
                .title(textOrNull(productNode, "name"))
                .categoryId(textOrNull(productNode, "category_id"))
                .status(textOrNull(productNode, "status"))
                .skus(new java.util.ArrayList<>())
                .build();
        JsonNode skus = productNode.path("skus");
        for (JsonNode sku : skus) {
            UnifiedProduct.Sku item = UnifiedProduct.Sku.builder()
                    .platformSkuId(textOrNull(sku, "id"))
                    .props(textOrNull(sku, "spec_detail") == null ? textOrNull(sku, "product_spec") : textOrNull(sku, "spec_detail"))
                    .price(fenToYuanOrNull(sku, "price"))
                    .stock(stock(sku))
                    .sellerSku(textOrNull(sku, "out_sku_id"))
                    .currency("CNY")
                    .build();
            if (item.getPlatformSkuId() == null) {
                throw new IllegalArgumentException("抖店商品 " + productId + " 的 sku 缺 id: " + sku);
            }
            product.getSkus().add(item);
        }
        return product;
    }

    /** 抖店商品售价为分 → 元(2 位 half-up),与订单口径统一;缺省返回 null */
    private static BigDecimal fenToYuanOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return new BigDecimal(value.asText()).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
    }

    private static String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    /** 库存字段抖动兼容:优先 stock_num(item 维度),缺则回落 inventory;皆缺返回 null */
    private static Integer stock(JsonNode sku) {
        int stockNum = sku.path("stock_num").asInt(-1);
        if (stockNum >= 0) {
            return stockNum;
        }
        int inventory = sku.path("inventory").asInt(-1);
        return inventory >= 0 ? inventory : null;
    }
}