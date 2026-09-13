package com.own.erp.platform.adapter.douyin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.unified.UnifiedOrder;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/13
 * @Description : 抖店订单翻译器单测:以官方 schema 推导样例(fixture 目录随 amazon 惯例)做映射断言
 *         ——金额分→元、状态机、收件人拼接、明细行、raw_json 留存(翻译出错可回溯);
 *         未知状态抛异常禁静默吞(拉单重试);fixture 为推导样例,真凭证样本到位后 --force 校准(docs/07 §8)
 */
class DouyinOrderTranslatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Long SHOP_ID = 7L;

    private JsonNode orderNode() {
        try (InputStream in = getClass().getResourceAsStream("/douyin/get-order-search-list-sample.json")) {
            return MAPPER.readTree(in).path("data").path("order_list").get(0);
        } catch (Exception e) {
            throw new IllegalStateException("fixture 读取失败", e);
        }
    }

    @Test
    void translatesOrderFieldsMoneyInFenToYuanAndMountsItems() {
        UnifiedOrder order = DouyinOrderTranslator.translateOrder(orderNode(), SHOP_ID, PlatformType.DOUYIN);

        assertEquals("4200000000000000001", order.getPlatformOrderId());
        assertEquals(PlatformType.DOUYIN, order.getPlatform());
        // order_status 3 = 已发货 → SHIPPED
        assertEquals(UnifiedOrder.OrderStatus.SHIPPED, order.getStatus());
        // 金额分→元(除以 100 保 2 位)
        assertEquals(0, order.getTotalAmount().compareTo(new BigDecimal("109.36")));
        assertEquals(0, order.getPostageAmount().compareTo(new BigDecimal("8.00")));
        assertEquals(0, order.getDiscountAmount().compareTo(new BigDecimal("10.00")));
        assertEquals("CNY", order.getCurrency());
        assertEquals(0, BigDecimal.ONE.compareTo(order.getExchangeRate()));
        // 收件人(省市区 + 详细地址拼接)
        assertEquals("张三", order.getReceiverName());
        assertEquals("13800000000", order.getReceiverPhone());
        assertEquals("广东省 深圳市 南山区 科技园路1号", order.getReceiverAddress());
        assertEquals("518000", order.getReceiverZip());
        // 明细两行:行号/platformSkuId/商户编码/数量/单价(分→元)
        assertEquals(2, order.getItems().size());
        UnifiedOrder.Item first = order.getItems().get(0);
        assertEquals("4200000000000000001-001", first.getPlatformOrderItemId());
        assertEquals("15000000000001", first.getPlatformSkuId());
        assertEquals("ERP-SKU-001", first.getSellerSku());
        assertEquals(2, first.getQuantity());
        assertEquals(0, first.getUnitPrice().compareTo(new BigDecimal("41.00")));
        assertEquals("黑色", first.getSkuProps());
        // 留言/备注 + raw_json 留存回溯(翻译出错可重放)
        assertEquals("深色优先", order.getBuyerMessage());
        assertEquals("轻拿轻放", order.getRemark());
        assertTrue(order.getRawJson().contains("4200000000000000001"), order.getRawJson());
    }

    @Test
    void rejectsUnknownStatus() {
        JsonNode bad = orderNode().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) bad).put("order_status", 99);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> DouyinOrderTranslator.translateOrder(bad, SHOP_ID, PlatformType.DOUYIN));
        assertTrue(exception.getMessage().contains("未知抖店订单状态"), exception.getMessage());
    }

    @Test
    void rejectsMissingOrderId() {
        JsonNode bad = orderNode().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) bad).remove("order_id");

        assertThrows(IllegalArgumentException.class,
                () -> DouyinOrderTranslator.translateOrder(bad, SHOP_ID, PlatformType.DOUYIN));
    }
}