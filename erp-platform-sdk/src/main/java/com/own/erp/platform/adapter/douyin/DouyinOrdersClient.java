package com.own.erp.platform.adapter.douyin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.own.erp.platform.ShopSession;
import com.own.erp.platform.unified.UnifiedOrder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/13
 * @Description : 抖店订单拉取客户端(order.searchList):
 *         - 按「更新时间」窗口拉取(update_time_start/end 秒级,窗口序,拉单策略 1 同 docs/04——
 *           按下单时间会丢状态回传),order_by=update_time 升序翻页,page 从 0 起 size≤100(官方);
 *         - data 返回 order_list + has_more;has_more 归零或空列表即停,超防御上限中止防自旋;
 *         - 每个节点经 {@link DouyinOrderTranslator} 翻译(金额分→元/状态机/收件人/明细,零业务 if);
 *         - 时间窗最大近 90 天(官方),窗口越界由拉单 Job 游标纪律裁剪(不在此处设防,超窗返回部分结果由 uk 幂等兜底)
 */
final class DouyinOrdersClient {

    private static final String METHOD = "order.searchList";
    private static final String PATH = "/order/searchList";
    private static final int SIZE = 100;
    private static final int MAX_PAGES = 100;

    private final DouyinApiSupport support;

    DouyinOrdersClient(DouyinApiSupport support) {
        this.support = support;
    }

    List<UnifiedOrder> pullOrders(ShopSession session, Instant start, Instant end) {
        if (session == null || session.getToken() == null
                || session.getToken().getAccessToken() == null) {
            throw new IllegalStateException("ShopSession 缺抖店 access_token,无法拉取订单");
        }
        List<UnifiedOrder> result = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            ObjectNode paramJson = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            paramJson.put("page", page);
            paramJson.put("size", SIZE);
            paramJson.put("update_time_start", start.getEpochSecond());
            paramJson.put("update_time_end", end.getEpochSecond());
            paramJson.put("order_by", "update_time");
            paramJson.put("order_asc", 1);
            JsonNode data = support.execute(METHOD, PATH, paramJson, session.getToken().getAccessToken());
            JsonNode orderList = data.path("order_list");
            int countThisPage = 0;
            for (JsonNode orderNode : orderList) {
                result.add(DouyinOrderTranslator.translateOrder(
                        orderNode, session.getShopId(), session.getPlatform()));
                countThisPage++;
            }
            if (countThisPage == 0 || !data.path("has_more").asBoolean(false)) {
                return result;
            }
        }
        throw new IllegalStateException("抖店 order.searchList 翻页超出防御上限(单窗口 > " + MAX_PAGES + " 页),中止防游标自旋");
    }
}