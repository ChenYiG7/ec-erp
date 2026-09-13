package com.own.erp.platform.adapter.douyin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.own.erp.platform.ShopSession;
import com.own.erp.platform.unified.UnifiedProduct;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/13
 * @Description : 抖店商品同步客户端(product.listV2):
 *         - 全量商品列表快照(库存/编码为实时状态,ProductPullJob 低频拉全量对账,uk 幂等 upsert),
 *           时间窗入参不参与过滤(与亚马逊 listing 报表同语义,PRODUCT 游标仅作拉取频率控制,docs/04);
 *         - 分页:page 从 0 起 size≤100,has_more 归零停,超防御上限中止防自旋;
 *         - 每个商品节点经 {@link DouyinListingTranslator} 翻译
 */
final class DouyinProductClient {

    private static final String METHOD = "product.listV2";
    private static final String PATH = "/product/listV2";
    private static final int SIZE = 100;
    private static final int MAX_PAGES = 100;

    private final DouyinApiSupport support;

    DouyinProductClient(DouyinApiSupport support) {
        this.support = support;
    }

    List<UnifiedProduct> pullProducts(ShopSession session, Instant start, Instant end) {
        if (session == null || session.getToken() == null
                || session.getToken().getAccessToken() == null) {
            throw new IllegalStateException("ShopSession 缺抖店 access_token,无法拉取商品");
        }
        List<UnifiedProduct> result = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            ObjectNode paramJson = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            paramJson.put("page", page);
            paramJson.put("size", SIZE);
            paramJson.put("use_cursor", false);
            JsonNode data = support.execute(METHOD, PATH, paramJson, session.getToken().getAccessToken());
            JsonNode productList = data.path("product_list");
            int countThisPage = 0;
            for (JsonNode productNode : productList) {
                result.add(DouyinListingTranslator.translateProduct(
                        productNode, session.getShopId(), session.getPlatform()));
                countThisPage++;
            }
            if (countThisPage == 0 || !data.path("has_more").asBoolean(false)) {
                return result;
            }
        }
        throw new IllegalStateException("抖店 product.listV2 翻页超出防御上限,中止防游标自旋");
    }
}