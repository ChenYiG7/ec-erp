package com.own.erp.ai.graph;

import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.service.AiSuggestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : ReplenishCollectNode 单测(#6,AIR:mock 扫描组件/建议服务,不依赖数据库):
 *     节点职责 = 组件组合(阈值/护栏参数透传)+ 待确认建议去重;
 *     扫描口径(分页/跨仓合并/空页)收口 LowStockScannerTest
 */
class ReplenishCollectNodeTest {

    private LowStockScanner scanner;
    private ErpAiProperties props;
    private AiRuntimeProperties runtime;
    private AiSuggestionService aiSuggestionService;
    private ReplenishCollectNode node;

    @BeforeEach
    void setUp() {
        scanner = mock(LowStockScanner.class);
        props = new ErpAiProperties();
        runtime = RuntimePropsStub.of(props);
        aiSuggestionService = mock(AiSuggestionService.class);
        when(aiSuggestionService.findPendingSkuIds(anyString())).thenReturn(Set.of());
        node = new ReplenishCollectNode(scanner, props, runtime, aiSuggestionService);
    }

    private ReplenishItem item(Long skuId, int available, int transit) {
        return ReplenishItem.builder().skuId(skuId).qtyAvailable(available).qtyTransit(transit)
                .suggestQty(0).summary("").build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void passesRuntimeThresholdAndYmlGuardrailsToScanner() throws Exception {
        when(scanner.scan(anyInt(), anyInt(), anyInt()))
                .thenReturn(new LowStockScanner.ScanResult(List.of(item(1L, 3, 0)), 5));

        Map<String, Object> result = node.apply(null);

        // 阈值走 runtime(#18),护栏走 yml 默认(2026-09-07 拍板语义)
        verify(scanner).scan(10, props.getReplenish().getScanPageSize(), props.getReplenish().getScanMaxRows());
        assertEquals(5, result.get(ReplenishStateKeys.KEY_SCANNED));
        assertEquals(1, ((List<ReplenishItem>) result.get(ReplenishStateKeys.KEY_ITEMS)).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pendingSuggestionDeduped() throws Exception {
        // 去重(2026-09-07 拍板):同 SKU 已存在待确认建议即跳过;扫描计数不受影响
        when(scanner.scan(anyInt(), anyInt(), anyInt())).thenReturn(new LowStockScanner.ScanResult(
                new ArrayList<>(List.of(item(1L, 3, 0), item(2L, 3, 0))), 2));
        when(aiSuggestionService.findPendingSkuIds("REPLENISH")).thenReturn(Set.of(1L));

        Map<String, Object> result = node.apply(null);

        assertEquals(2, result.get(ReplenishStateKeys.KEY_SCANNED));
        List<ReplenishItem> items = (List<ReplenishItem>) result.get(ReplenishStateKeys.KEY_ITEMS);
        assertEquals(1, items.size());
        assertEquals(2L, items.get(0).skuId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyScanSkipsDedupQuery() throws Exception {
        // 无命中不查库(去重前置空判)
        when(scanner.scan(anyInt(), anyInt(), anyInt()))
                .thenReturn(new LowStockScanner.ScanResult(List.of(), 0));

        Map<String, Object> result = node.apply(null);

        assertTrue(((List<ReplenishItem>) result.get(ReplenishStateKeys.KEY_ITEMS)).isEmpty());
        org.mockito.Mockito.verifyNoInteractions(aiSuggestionService);
    }
}
