package com.own.erp.ai.graph;

import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 取数节点(#17 SAA Graph 采购建议工作流,拍板口径:输入复用补货计算单一来源):
 *     低库存扫描(LowStockScanner)+ 建议量计算(ReplenishCalculator)两共享组件组合,
 *     阈值/覆盖天数/动销窗口/最小建议量与补货工作流同源(runtime replenish 四键,#18 热更)——
 *     "哪些 SKU 缺、缺多少"两工作流口径强一致,采购建议是补货建议的供应商级聚合,不自算第二套公式;
 *     SKU 级去重不做(去重键在供应商,收口 aggregate 节点——映射完成后才知道组归属);
 *     扫描护栏走 erp.ai.purchase.*(yml),取数只走只读契约(铁律 2/7)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseCollectNode implements NodeAction {

    private final LowStockScanner scanner;
    private final ReplenishCalculator calculator;
    private final ErpAiProperties props;
    private final AiRuntimeProperties runtime;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        int lowStockThreshold = runtime.replenishLowStockThreshold();
        LowStockScanner.ScanResult scan = scanner.scan(lowStockThreshold,
                props.getPurchase().getScanPageSize(), props.getPurchase().getScanMaxRows());
        List<ReplenishItem> calculated = calculator.calculate(scan.items(),
                runtime.replenishSalesWindowDays(), runtime.replenishCoverageDays(),
                runtime.replenishMinSuggestQty());
        log.info("采购建议取数完成:扫描 {} 行,补货建议 SKU {} 个(与补货工作流同口径)",
                scan.scanned(), calculated.size());
        return Map.of(PurchaseStateKeys.KEY_ITEMS, calculated,
                PurchaseStateKeys.KEY_SCANNED, scan.scanned());
    }
}
