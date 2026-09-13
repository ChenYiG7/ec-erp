package com.own.erp.ai.service;

import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiSuggestion;
import com.own.erp.contract.InventoryQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026-9-12
 * @Description : SplitAdviceService 单测(#29 余量「自动拆单建议」方案 A,AIR:mock 契约+Service):
 *     多仓履约产建议(分组 payload 键/缺货注明)/单仓整单履约不产/全缺货不产;
 *     最优仓选择 = 可用量最大(并列取仓 ID 小者)
 */
class SplitAdviceServiceTest {

    private InventoryQueryApi inventoryQueryApi;
    private AiSuggestionService aiSuggestionService;
    private SplitAdviceService service;

    @BeforeEach
    void setUp() {
        inventoryQueryApi = mock(InventoryQueryApi.class);
        aiSuggestionService = mock(AiSuggestionService.class);
        when(aiSuggestionService.save(any())).thenReturn(1L);
        service = new SplitAdviceService(inventoryQueryApi, aiSuggestionService);
    }

    private void stubInventory(Long skuId, Long warehouseId, int available) {
        when(inventoryQueryApi.pageInventory(InventoryQueryApi.InventoryFilter.builder()
                .skuId(skuId).pageNo(1).pageSize(100).build()))
                .thenReturn(com.own.erp.contract.QueryPage.of(List.of(
                        InventoryQueryApi.InventoryView.builder()
                                .skuId(skuId).warehouseId(warehouseId).qtyAvailable(available).build()), 1));
    }

    @Test
    void multiWarehouseOrderProducesGroupedAdvice() {
        // sku11 独仓 A 可用 10;sku12 独仓 B 可用 10 → 需两仓履约,产一条聚合建议
        stubInventory(11L, 100L, 10);
        stubInventory(12L, 200L, 10);

        service.advise(new SplitAdviceService.AdviceInput(1L, 7L, "P-001", List.of(
                new SplitAdviceService.AdviceInput.Item(11L, 3),
                new SplitAdviceService.AdviceInput.Item(12L, 2))));

        org.mockito.ArgumentCaptor<AiSuggestion> captor =
                org.mockito.ArgumentCaptor.forClass(AiSuggestion.class);
        verify(aiSuggestionService).save(captor.capture());
        AiSuggestion saved = captor.getValue();
        assertEquals(AiConsts.TYPE_SPLIT_ADVICE, saved.getSuggestionType());
        assertEquals(AiConsts.REF_TYPE_SHOP_ORDER, saved.getRefType());
        assertEquals(1L, saved.getRefId());
        assertEquals(7L, saved.getShopId());
        assertEquals(AiConsts.STATUS_PENDING, saved.getStatus());
        assertNull(saved.getRiskLevel(), "拆单建议无风险语义,riskLevel 不设");
        assertTrue(saved.getSummary().contains("P-001"));
        assertTrue(saved.getSummary().contains("仓 100") && saved.getSummary().contains("仓 200"));
        assertTrue(saved.getPayloadJson().contains("\"skuId\":11"));
        assertTrue(saved.getPayloadJson().contains("\"warehouseId\":200"));
        assertTrue(saved.getPayloadJson().contains("\"noStockSkus\":[]"));
    }

    @Test
    void singleWarehouseFulfilmentProducesNoAdvice() {
        // 两行 SKU 都在同一仓可履约 → 不产建议(无噪音)
        stubInventory(11L, 100L, 10);
        stubInventory(12L, 100L, 10);

        service.advise(new SplitAdviceService.AdviceInput(1L, 7L, "P-001", List.of(
                new SplitAdviceService.AdviceInput.Item(11L, 3),
                new SplitAdviceService.AdviceInput.Item(12L, 2))));

        verifyNoInteractions(aiSuggestionService);
    }

    @Test
    void outOfStockItemsNotedAndSkipped() {
        // sku12 全无库存:不进分组;sku11 单仓 → 无需拆单,不产建议
        stubInventory(11L, 100L, 10);
        stubInventory(12L, 100L, 0);

        service.advise(new SplitAdviceService.AdviceInput(1L, 7L, "P-001", List.of(
                new SplitAdviceService.AdviceInput.Item(11L, 3),
                new SplitAdviceService.AdviceInput.Item(12L, 2))));

        verifyNoInteractions(aiSuggestionService);
    }

    @Test
    void picksWarehouseWithMostAvailable() {
        // 并列语义反证:仓 200 可用 3 < 仓 100 可用 5 → 选仓 100(可用最大)
        when(inventoryQueryApi.pageInventory(InventoryQueryApi.InventoryFilter.builder()
                .skuId(11L).pageNo(1).pageSize(100).build()))
                .thenReturn(com.own.erp.contract.QueryPage.of(List.of(
                        InventoryQueryApi.InventoryView.builder().skuId(11L).warehouseId(200L).qtyAvailable(3).build(),
                        InventoryQueryApi.InventoryView.builder().skuId(11L).warehouseId(100L).qtyAvailable(5).build()), 2));
        stubInventory(12L, 100L, 10);

        service.advise(new SplitAdviceService.AdviceInput(1L, 7L, "P-001", List.of(
                new SplitAdviceService.AdviceInput.Item(11L, 3),
                new SplitAdviceService.AdviceInput.Item(12L, 2))));

        verifyNoInteractions(aiSuggestionService);
    }
}
