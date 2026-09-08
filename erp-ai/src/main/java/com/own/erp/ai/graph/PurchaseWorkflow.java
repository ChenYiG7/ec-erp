package com.own.erp.ai.graph;

import com.own.erp.common.exception.BusinessException;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 采购建议工作流(#17 三期候选「智能采购建议」V1 落地,SAA Graph Core,
 *     照 ReplenishWorkflow 母本;落位表拍板:建议层叠加 #10 采购域之上,只产建议进 ai_suggestion,
 *     不碰状态机与单据):START → collect(低库存扫描+补货量计算,复用补货共享组件同口径)→
 *     aggregate(按最新采购供应商聚合+去重)→ 条件边(无供应商组直达 END,零 LLM 成本)→
 *     summarize(LLM 摘要,可降级)→ persist(落 ai_suggestion) → END。
 *     图在构造器装配并 compile 一次持有(节点为无状态单例,可重复 invoke);
 *     各状态键 REPLACE 策略。触发:POST /api/ai/purchase/run(登录即可);
 *     V1 不接定时(采购是人类决策节奏,建议量随人工确认闭环滚动),定时接线待实际使用节奏拍板
 */
@Slf4j
@Service
public class PurchaseWorkflow {

    private final CompiledGraph compiledGraph;

    public PurchaseWorkflow(PurchaseCollectNode collectNode,
                            PurchaseAggregateNode aggregateNode,
                            PurchaseSummarizeNode summarizeNode,
                            PurchasePersistNode persistNode) {
        try {
            KeyStrategyFactory keyStrategyFactory = new KeyStrategyFactoryBuilder()
                    .addStrategy(PurchaseStateKeys.KEY_ITEMS, KeyStrategy.REPLACE)
                    .addStrategy(PurchaseStateKeys.KEY_GROUPS, KeyStrategy.REPLACE)
                    .addStrategy(PurchaseStateKeys.KEY_NO_SUPPLIER, KeyStrategy.REPLACE)
                    .addStrategy(PurchaseStateKeys.KEY_SCANNED, KeyStrategy.REPLACE)
                    .addStrategy(PurchaseStateKeys.KEY_DEGRADED, KeyStrategy.REPLACE)
                    .addStrategy(PurchaseStateKeys.KEY_PERSISTED, KeyStrategy.REPLACE)
                    .build();
            // 条件边分支串必须是 mappings key:无供应商组直达 END,否则进摘要节点
            StateGraph graph = new StateGraph("purchase", keyStrategyFactory)
                    .addNode("collect", AsyncNodeAction.node_async(collectNode))
                    .addNode("aggregate", AsyncNodeAction.node_async(aggregateNode))
                    .addNode("summarize", AsyncNodeAction.node_async(summarizeNode))
                    .addNode("persist", AsyncNodeAction.node_async(persistNode))
                    .addEdge(StateGraph.START, "collect")
                    .addEdge("collect", "aggregate")
                    .addConditionalEdges("aggregate",
                            AsyncEdgeAction.edge_async(state -> hasGroups(state)
                                    ? "summarize" : StateGraph.END),
                            Map.of("summarize", "summarize", StateGraph.END, StateGraph.END))
                    .addEdge("summarize", "persist")
                    .addEdge("persist", StateGraph.END);
            this.compiledGraph = graph.compile();
        } catch (GraphStateException e) {
            // 图装配错误属编程期错误,启动即失败优先于运行期才发现
            throw new IllegalStateException("采购建议工作流图装配失败", e);
        }
    }

    private static boolean hasGroups(OverAllState state) {
        List<?> groups = state.value(PurchaseStateKeys.KEY_GROUPS, List.class).orElse(List.of());
        return !groups.isEmpty();
    }

    /** 执行工作流:返回运行摘要;引擎异常统一包业务异常(手动触发时直接可见) */
    public PurchaseRunResult run() {
        try {
            Optional<OverAllState> result = compiledGraph.invoke(Map.of());
            OverAllState state = result.orElseThrow(() -> new BusinessException("采购建议工作流执行失败:无状态返回"));
            return PurchaseRunResult.builder()
                    .scannedCount(state.value(PurchaseStateKeys.KEY_SCANNED, Integer.class).orElse(0))
                    .suggestedSkuCount(state.value(PurchaseStateKeys.KEY_ITEMS, List.class)
                            .map(List::size).orElse(0))
                    .noSupplierCount(state.value(PurchaseStateKeys.KEY_NO_SUPPLIER, Integer.class).orElse(0))
                    .groupCount(state.value(PurchaseStateKeys.KEY_GROUPS, List.class)
                            .map(List::size).orElse(0))
                    .persistedCount(state.value(PurchaseStateKeys.KEY_PERSISTED, Integer.class).orElse(0))
                    .degraded(state.value(PurchaseStateKeys.KEY_DEGRADED, Boolean.class).orElse(false))
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("采购建议工作流执行异常", e);
            throw new BusinessException("采购建议工作流执行失败: " + e.getMessage());
        }
    }
}
