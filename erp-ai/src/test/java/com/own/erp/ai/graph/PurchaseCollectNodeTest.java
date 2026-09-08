package com.own.erp.ai.graph;

import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : PurchaseCollectNode 单测(#17,AIR:mock 共享组件):
 *     组合透传断言——低库存阈值与补货四参数同源 runtime(replenish 键,#18 热更),
 *     扫描护栏走 erp.ai.purchase.* yml;公式/扫描语义分别在 LowStockScannerTest/
 *     ReplenishCalculatorTest 收口
 */
class PurchaseCollectNodeTest {

    private LowStockScanner scanner;
    private ReplenishCalculator calculator;
    private ErpAiProperties props;
    private AiRuntimeProperties runtime;
    private PurchaseCollectNode node;

    @BeforeEach
    void setUp() {
        scanner = mock(LowStockScanner.class);
        calculator = mock(ReplenishCalculator.class);
        props = new ErpAiProperties();
        runtime = RuntimePropsStub.of(props);
        node = new PurchaseCollectNode(scanner, calculator, props, runtime);
    }

    @Test
    @SuppressWarnings("unchecked")
    void delegatesToSharedComponentsWithReplenishParams() throws Exception {
        when(scanner.scan(anyInt(), anyInt(), anyInt()))
                .thenReturn(new LowStockScanner.ScanResult(
                        List.of(ReplenishItem.builder().skuId(1L).qtyAvailable(3).qtyTransit(0)
                                .suggestQty(0).summary("").build()), 5));
        when(calculator.calculate(any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(ReplenishItem.builder().skuId(1L).qtyAvailable(3).qtyTransit(0)
                        .suggestQty(18).summary("").build()));

        Map<String, Object> result = node.apply(null);

        // 阈值/窗口/覆盖/下限 = 补货同源键;护栏 = purchase 段
        verify(scanner).scan(10, props.getPurchase().getScanPageSize(), props.getPurchase().getScanMaxRows());
        verify(calculator).calculate(any(), org.mockito.ArgumentMatchers.eq(30),
                org.mockito.ArgumentMatchers.eq(14), org.mockito.ArgumentMatchers.eq(10));
        assertEquals(5, result.get(PurchaseStateKeys.KEY_SCANNED));
        List<ReplenishItem> items = (List<ReplenishItem>) result.get(PurchaseStateKeys.KEY_ITEMS);
        assertEquals(1, items.size());
        assertEquals(18, items.get(0).suggestQty());
    }
}
