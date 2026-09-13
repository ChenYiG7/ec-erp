package com.own.erp.platform.adapter.douyin;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.own.erp.platform.PlatformShipment;
import com.own.erp.platform.ShopSession;

/**
 * @author : chenyi
 * @Date : 2026/9/13
 * @Description : 抖店物流回传客户端(order.logisticsAdd):
 *         - 官方当前**只支持整单出库**(入参只能传父 order_id,行级 product_orders 仅带 SN/IMEI 等溯源码、
 *           不推进子单状态)→ 回传以订单粒度发运,SKU 行级明细不作为发货推进的依据(docs/04 差异表);
 *         - 物流公司编码 company_code 必填(可从 /order/logisticsCompanyList 列举,编外物流用 company 名称兜底);
 *         - 发货地址 address_id/退货地址 after_sale_address_id 可选,未配置不传(禁空串脏值);
 *         - 失败(业务 code!=0 / HTTP 非 2xx)上抛 → 上层记 pull_log 告警但**不回滚本地发货**(docs/04,平台侧可补)
 */
final class DouyinLogisticsClient {

    private static final String METHOD = "order.logisticsAdd";
    private static final String PATH = "/order/logisticsAdd";

    private final DouyinApiSupport support;

    DouyinLogisticsClient(DouyinApiSupport support) {
        this.support = support;
    }

    void uploadTracking(ShopSession session, PlatformShipment shipment) {
        if (session == null || session.getToken() == null
                || session.getToken().getAccessToken() == null) {
            throw new IllegalStateException("ShopSession 缺抖店 access_token,无法回传发货");
        }
        ObjectNode paramJson = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        paramJson.put("order_id", shipment.platformOrderId());
        paramJson.put("logistics_code", shipment.trackingNo());
        if (shipment.carrierCode() != null && !shipment.carrierCode().isBlank()) {
            paramJson.put("company_code", shipment.carrierCode());
        }
        if (shipment.carrierName() != null && !shipment.carrierName().isBlank()) {
            paramJson.put("company", shipment.carrierName());
        }
        if ((shipment.carrierCode() == null || shipment.carrierCode().isBlank())
                && (shipment.carrierName() == null || shipment.carrierName().isBlank())) {
            throw new IllegalStateException("发货运单号缺失或物流公司(carrierCode/carrierName 至少其一)未提供");
        }
        if (shipment.platformOrderId() == null || shipment.platformOrderId().isBlank()
                || shipment.trackingNo() == null || shipment.trackingNo().isBlank()) {
            throw new IllegalStateException("发货回传缺平台订单号或运单号(order_id/logistics_code 必填)");
        }
        // 整单出库:不传行级明细(官方 product_orders 仅溯源码用,不推进子单状态)
        support.execute(METHOD, PATH, paramJson, session.getToken().getAccessToken());
    }
}