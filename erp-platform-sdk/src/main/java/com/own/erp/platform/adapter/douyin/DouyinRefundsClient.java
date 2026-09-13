package com.own.erp.platform.adapter.douyin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.own.erp.platform.ShopSession;
import com.own.erp.platform.unified.UnifiedRefund;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/13
 * @Description : 抖店售后/退款拉取客户端(afterSale.List):
 *         - 按「更新时间」窗口拉取(update_start_time/end_time 秒级,窗口序——退单状态流转靠更新窗捕获),
 *           page 从 0 起 size≤100(page*size≤5 万官方上限),has_more 归零停,超防御上限中止;
 *         - 默认只能查近 6 个月数据(官方),游标纪律由拉单 Job 裁剪;每个退单节点经 {@link DouyinRefundTranslator} 翻译
 */
final class DouyinRefundsClient {

    private static final String METHOD = "afterSale.List";
    private static final String PATH = "/afterSale/List";
    private static final int SIZE = 100;
    private static final int MAX_PAGES = 100;

    private final DouyinApiSupport support;

    DouyinRefundsClient(DouyinApiSupport support) {
        this.support = support;
    }

    List<UnifiedRefund> pullRefunds(ShopSession session, Instant start, Instant end) {
        if (session == null || session.getToken() == null
                || session.getToken().getAccessToken() == null) {
            throw new IllegalStateException("ShopSession 缺抖店 access_token,无法拉取售后");
        }
        List<UnifiedRefund> result = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            ObjectNode paramJson = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            paramJson.put("update_start_time", start.getEpochSecond());
            paramJson.put("update_end_time", end.getEpochSecond());
            paramJson.put("page", page);
            paramJson.put("size", SIZE);
            JsonNode data = support.execute(METHOD, PATH, paramJson, session.getToken().getAccessToken());
            JsonNode items = data.path("items");
            int countThisPage = 0;
            for (JsonNode refundNode : items) {
                result.add(DouyinRefundTranslator.translateRefund(
                        refundNode, session.getShopId(), session.getPlatform()));
                countThisPage++;
            }
            if (countThisPage == 0 || !data.path("has_more").asBoolean(false)) {
                return result;
            }
        }
        throw new IllegalStateException("抖店 afterSale.List 翻页超出防御上限,中止防游标自旋");
    }
}