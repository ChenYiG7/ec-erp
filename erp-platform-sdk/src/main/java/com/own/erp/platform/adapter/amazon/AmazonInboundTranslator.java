package com.own.erp.platform.adapter.amazon;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.own.erp.platform.PlatformAddress;
import com.own.erp.platform.PlatformInboundPlanRequest;
import com.own.erp.platform.PlatformInboundShipment;
import com.own.erp.platform.PlatformTransportContent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : Amazon Fulfillment Inbound Shipment API v0 ↔ 平台中立模型翻译器(#35 fba-shipment V2,
 *         防腐层内平台差异唯一容身处,报文翻译零业务 if,docs/07 §8):
 *         - 字段名取自官方 fulfillment-inbound-api-model(InboundShipmentPlans/ShipmentData/ItemData/
 *           TransportResult 等),**fixture 为官方 schema 推导样例,真凭证样本到位后 --force 校准一轮**;
 *         - 计划结果:InboundShipmentPlans[].Items[].Quantity → quantityPlanned(平台拆分分配量);
 *         - 收货拉取:ShipmentData[].ShipmentStatus 原样透出(平台枚举字面量,领域侧映射状态机),
 *           ItemData[].QuantityShipped/QuantityReceived → 对账量(fba_shipment_diff SHORT/EXTRA/OK 判定输入);
 *         - 请求体构建:Jackson 树模型(SKU/承运商名是外部值,禁手工字符串拼接,docs/07 §7 注入防护),
 *           partnered 分支只做协议结构选择(TransportDetailInput 二选一),不含业务语义;
 *         - 核心字段缺失(ShipmentId/DestinationFulfillmentCenterId 等)抛异常禁静默(docs/07 §8)
 */
final class AmazonInboundTranslator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AmazonInboundTranslator() {
    }

    /** buildPlanRequestBody:createInboundShipmentPlan 请求体(ShipFromAddress/标签偏好/计划行) */
    static String buildPlanRequestBody(PlatformInboundPlanRequest request) {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("ShipFromAddress", buildAddress(request.shipFromAddress()));
        root.put("LabelPrepPreference", request.labelPrepPreference());
        root.put("ShipToCountryCode", request.shipToCountryCode());
        ArrayNode items = root.putArray("InboundShipmentPlanRequestItems");
        for (PlatformInboundPlanRequest.Item item : request.items()) {
            ObjectNode node = items.addObject();
            node.put("SellerSKU", item.sellerSku());
            node.put("Quantity", item.quantity());
        }
        return writeBody(root, "入库计划请求体序列化失败");
    }

    /** translatePlanPayload:payload.InboundShipmentPlans[] → 平台发货单列表(拆分建议,quantityPlanned) */
    static List<PlatformInboundShipment> translatePlanPayload(JsonNode payload) {
        List<PlatformInboundShipment> shipments = new ArrayList<>();
        for (JsonNode plan : payload.path("InboundShipmentPlans")) {
            shipments.add(PlatformInboundShipment.builder()
                    .shipmentId(required(plan, "ShipmentId"))
                    .destinationFulfillmentCenter(required(plan, "DestinationFulfillmentCenterId"))
                    .items(translatePlanItems(plan.path("Items")))
                    .build());
        }
        return shipments;
    }

    /** translateShipmentsPayload:payload.ShipmentData[] → 平台发货单状态列表(无行级明细) */
    static List<PlatformInboundShipment> translateShipmentsPayload(JsonNode payload) {
        List<PlatformInboundShipment> shipments = new ArrayList<>();
        for (JsonNode shipment : payload.path("ShipmentData")) {
            shipments.add(PlatformInboundShipment.builder()
                    .shipmentId(required(shipment, "ShipmentId"))
                    .destinationFulfillmentCenter(required(shipment, "DestinationFulfillmentCenterId"))
                    .status(required(shipment, "ShipmentStatus"))
                    .build());
        }
        return shipments;
    }

    /** translateShipmentItemsPayload:payload.ItemData[] → 按平台发货单 ID 分组的对账行(Shipped/Received) */
    static Map<String, List<PlatformInboundShipment.Item>> translateShipmentItemsPayload(JsonNode payload) {
        Map<String, List<PlatformInboundShipment.Item>> itemsByShipment = new HashMap<>();
        for (JsonNode itemNode : payload.path("ItemData")) {
            String shipmentId = required(itemNode, "ShipmentId");
            itemsByShipment.computeIfAbsent(shipmentId, k -> new ArrayList<>())
                    .add(PlatformInboundShipment.Item.builder()
                            .sellerSku(itemNode.path("SellerSKU").asText(null))
                            .quantityShipped(intOrNull(itemNode, "QuantityShipped"))
                            .quantityReceived(intOrNull(itemNode, "QuantityReceived"))
                            .build());
        }
        return itemsByShipment;
    }

    /** translateTransportResult:payload.TransportResult.IsSuccess → 受理结果(IsSuccess 缺失即抛禁静默) */
    static boolean translateTransportResult(JsonNode payload) {
        JsonNode result = payload.path("TransportResult");
        if (result.isMissingNode() || !result.hasNonNull("IsSuccess")) {
            throw new IllegalStateException("putTransportContent 响应缺 TransportResult.IsSuccess,拒绝静默");
        }
        return result.path("IsSuccess").asBoolean();
    }

    /** buildTransportRequestBody:putTransportContent 请求体(partnered 分支只做协议结构选择) */
    static String buildTransportRequestBody(PlatformTransportContent content) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode detail = root.putObject("TransportDetailInput");
        if (content.partnered()) {
            ObjectNode partnered = detail.putObject("PartneredSmallParcelData");
            ObjectNode contact = partnered.putObject("Contact");
            contact.put("Name", content.contactName());
            contact.put("Phone", content.contactPhone());
            ArrayNode boxList = partnered.putArray("BoxList");
            for (PlatformTransportContent.Box box : content.boxes()) {
                ObjectNode boxNode = boxList.addObject();
                ObjectNode dimensions = boxNode.putObject("Dimensions");
                dimensions.put("Length", box.length());
                dimensions.put("Width", box.width());
                dimensions.put("Height", box.height());
                dimensions.put("Unit", box.dimensionUnit());
                ObjectNode weight = boxNode.putObject("Weight");
                weight.put("Value", box.weight());
                weight.put("Unit", box.weightUnit());
            }
        } else {
            ObjectNode nonPartnered = detail.putObject("NonPartneredSmallParcelData");
            nonPartnered.put("CarrierName", content.carrierName());
        }
        return writeBody(root, "板箱回传请求体序列化失败");
    }

    private static List<PlatformInboundShipment.Item> translatePlanItems(JsonNode itemsNode) {
        List<PlatformInboundShipment.Item> items = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            items.add(PlatformInboundShipment.Item.builder()
                    .sellerSku(itemNode.path("SellerSKU").asText(null))
                    .quantityPlanned(itemNode.path("Quantity").asInt(0))
                    .build());
        }
        return items;
    }

    /** Address 模型(DistrictOrCounty 可选字段不透出,领域侧未采集;联调按真实下单地址校准) */
    private static ObjectNode buildAddress(PlatformAddress address) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("Name", address.name());
        node.put("AddressLine1", address.addressLine1());
        putIfNotBlank(node, "AddressLine2", address.addressLine2());
        node.put("City", address.city());
        putIfNotBlank(node, "StateOrRegion", address.stateOrRegion());
        putIfNotBlank(node, "PostalCode", address.postalCode());
        node.put("CountryCode", address.countryCode());
        return node;
    }

    private static void putIfNotBlank(ObjectNode node, String field, String value) {
        if (StrUtil.isNotBlank(value)) {
            node.put(field, value);
        }
    }

    private static Integer intOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).asInt() : null;
    }

    private static String writeBody(ObjectNode root, String errorMessage) {
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(errorMessage, e);
        }
    }

    /** 必填字段缺失即拒(禁静默,docs/07 §8):上游调用方记 pull_log 走连续失败告警 */
    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (StrUtil.isBlank(value)) {
            throw new IllegalStateException("Inbound 报文缺必填字段 " + field + ",拒绝静默翻译");
        }
        return value;
    }
}
