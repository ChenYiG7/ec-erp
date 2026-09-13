package com.own.erp.platform.adapter.amazon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.erp.platform.PlatformAddress;
import com.own.erp.platform.PlatformInboundPlanRequest;
import com.own.erp.platform.PlatformInboundShipment;
import com.own.erp.platform.PlatformTransportContent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : Amazon Inbound 翻译器单测:计划结果/收货状态/对账行翻译 + 请求体构建(partnered 两态)
 *     + 核心字段缺失即拒(docs/07 §8 禁静默);fixture 为官方 fulfillment-inbound-api-model
 *     schema 推导样例,真凭证样本到位后 --force 校准一轮
 */
class AmazonInboundTranslatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void translatesPlanPayloadToPlatformShipments() throws Exception {
        JsonNode payload = MAPPER.readTree("""
                {"InboundShipmentPlans":[
                {"ShipmentId":"FBA15ABC123","DestinationFulfillmentCenterId":"PHX7",
                 "Items":[{"SellerSKU":"ERP-SKU-001","Quantity":10,"FulfillmentNetworkSKU":"X001"},
                          {"SellerSKU":"ERP-SKU-002","Quantity":5}]}]}""");

        List<PlatformInboundShipment> shipments = AmazonInboundTranslator.translatePlanPayload(payload);

        assertEquals(1, shipments.size());
        assertEquals("FBA15ABC123", shipments.get(0).shipmentId());
        assertEquals("PHX7", shipments.get(0).destinationFulfillmentCenter());
        assertEquals(2, shipments.get(0).items().size());
        assertEquals("ERP-SKU-001", shipments.get(0).items().get(0).sellerSku());
        assertEquals(10, shipments.get(0).items().get(0).quantityPlanned());
        assertNull(shipments.get(0).items().get(0).quantityReceived());
    }

    @Test
    void translatesShipmentStatusAndItemQuantities() throws Exception {
        JsonNode shipmentsPayload = MAPPER.readTree("""
                {"ShipmentData":[
                {"ShipmentId":"FBA15ABC123","DestinationFulfillmentCenterId":"PHX7",
                 "ShipmentStatus":"RECEIVED"}]}""");
        JsonNode itemsPayload = MAPPER.readTree("""
                {"ItemData":[
                {"ShipmentId":"FBA15ABC123","SellerSKU":"ERP-SKU-001","QuantityShipped":10,"QuantityReceived":8},
                {"ShipmentId":"FBA15ABC123","SellerSKU":"ERP-SKU-002","QuantityShipped":5,"QuantityReceived":6},
                {"ShipmentId":"FBA-OTHER","SellerSKU":"ERP-SKU-003","QuantityShipped":1,"QuantityReceived":0}]}""");

        List<PlatformInboundShipment> shipments = AmazonInboundTranslator.translateShipmentsPayload(shipmentsPayload);
        var itemsByShipment = AmazonInboundTranslator.translateShipmentItemsPayload(itemsPayload);

        assertEquals("RECEIVED", shipments.get(0).status());
        assertNull(shipments.get(0).items());
        // 对账行按 ShipmentId 分组:同单两行(SHORT/EXTRA 判定输入),他单不串
        assertEquals(2, itemsByShipment.size());
        assertEquals(2, itemsByShipment.get("FBA15ABC123").size());
        assertEquals(8, itemsByShipment.get("FBA15ABC123").get(0).quantityReceived());
        assertEquals(6, itemsByShipment.get("FBA15ABC123").get(1).quantityReceived());
        assertEquals(1, itemsByShipment.get("FBA-OTHER").size());
    }

    @Test
    void buildsPlanRequestBodyWithAddressAndItems() throws Exception {
        String body = AmazonInboundTranslator.buildPlanRequestBody(PlatformInboundPlanRequest.builder()
                .shipToCountryCode("US").labelPrepPreference("SELLER_LABEL")
                .shipFromAddress(PlatformAddress.builder()
                        .name("ERP 仓").addressLine1("No.1 Example Rd").addressLine2("Suite 2")
                        .city("Shenzhen").stateOrRegion("GD").postalCode("518000").countryCode("CN").build())
                .items(List.of(PlatformInboundPlanRequest.Item.builder().sellerSku("ERP-SKU-001").quantity(10).build()))
                .build());

        JsonNode root = MAPPER.readTree(body);
        assertEquals("ERP 仓", root.path("ShipFromAddress").path("Name").asText());
        assertEquals("No.1 Example Rd", root.path("ShipFromAddress").path("AddressLine1").asText());
        assertEquals("Suite 2", root.path("ShipFromAddress").path("AddressLine2").asText());
        assertEquals("CN", root.path("ShipFromAddress").path("CountryCode").asText());
        assertEquals("SELLER_LABEL", root.path("LabelPrepPreference").asText());
        assertEquals("US", root.path("ShipToCountryCode").asText());
        assertEquals("ERP-SKU-001", root.path("InboundShipmentPlanRequestItems").get(0).path("SellerSKU").asText());
        assertEquals(10, root.path("InboundShipmentPlanRequestItems").get(0).path("Quantity").asInt());
    }

    @Test
    void buildsTransportRequestBodyForPartneredAndNonPartnered() throws Exception {
        JsonNode partnered = MAPPER.readTree(AmazonInboundTranslator.buildTransportRequestBody(
                PlatformTransportContent.builder()
                        .partnered(true).contactName("chenyi").contactPhone("13800000000")
                        .boxes(List.of(PlatformTransportContent.Box.builder()
                                .length(new BigDecimal("60")).width(new BigDecimal("40")).height(new BigDecimal("40"))
                                .dimensionUnit("cm").weight(new BigDecimal("10")).weightUnit("kg").build()))
                        .build()));
        JsonNode nonPartnered = MAPPER.readTree(AmazonInboundTranslator.buildTransportRequestBody(
                PlatformTransportContent.builder().partnered(false).carrierName("UPS").build()));

        assertTrue(partnered.path("TransportDetailInput").has("PartneredSmallParcelData"));
        assertEquals("chenyi", partnered.path("TransportDetailInput").path("PartneredSmallParcelData")
                .path("Contact").path("Name").asText());
        assertEquals("kg", partnered.path("TransportDetailInput").path("PartneredSmallParcelData")
                .path("BoxList").get(0).path("Weight").path("Unit").asText());
        assertFalse(partnered.path("TransportDetailInput").has("NonPartneredSmallParcelData"));
        // 非合作:仅承运商名,协议结构二选一(非业务 if)
        assertEquals("UPS", nonPartnered.path("TransportDetailInput").path("NonPartneredSmallParcelData")
                .path("CarrierName").asText());
        assertFalse(nonPartnered.path("TransportDetailInput").has("PartneredSmallParcelData"));
    }

    @Test
    void translatesTransportResultIsSuccessBothStates() throws Exception {
        assertTrue(AmazonInboundTranslator.translateTransportResult(
                MAPPER.readTree("{\"TransportResult\":{\"IsSuccess\":true,\"Message\":\"Ok\"}}")));
        assertFalse(AmazonInboundTranslator.translateTransportResult(
                MAPPER.readTree("{\"TransportResult\":{\"IsSuccess\":false,\"Message\":\"rejected\"}}")));
    }

    @Test
    void missingRequiredFieldsRejectedSilentlyNever() {
        // 计划结果缺 ShipmentId
        JsonNode planMissing = MAPPER.valueToTree(java.util.Map.of("InboundShipmentPlans",
                List.of(java.util.Map.of("DestinationFulfillmentCenterId", "PHX7"))));
        IllegalStateException planException = assertThrows(IllegalStateException.class,
                () -> AmazonInboundTranslator.translatePlanPayload(planMissing));
        assertTrue(planException.getMessage().contains("ShipmentId"), planException.getMessage());

        // 状态拉取缺 ShipmentStatus(前两必填字段齐备)
        JsonNode shipmentMissing = MAPPER.valueToTree(java.util.Map.of("ShipmentData",
                List.of(java.util.Map.of("ShipmentId", "FBA15ABC123",
                        "DestinationFulfillmentCenterId", "PHX7"))));
        IllegalStateException statusException = assertThrows(IllegalStateException.class,
                () -> AmazonInboundTranslator.translateShipmentsPayload(shipmentMissing));
        assertTrue(statusException.getMessage().contains("ShipmentStatus"), statusException.getMessage());

        // 板箱结果缺 IsSuccess
        JsonNode transportMissing = MAPPER.valueToTree(java.util.Map.of("TransportResult",
                java.util.Map.of("Message", "Ok")));
        IllegalStateException transportException = assertThrows(IllegalStateException.class,
                () -> AmazonInboundTranslator.translateTransportResult(transportMissing));
        assertTrue(transportException.getMessage().contains("IsSuccess"), transportException.getMessage());
    }
}
