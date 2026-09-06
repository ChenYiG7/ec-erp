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
 * @Date : 2026/9/6
 * @Description : 补货建议工作流(#6 SAA Graph Core,TODO 原文"取数LLM"已拍板偏离为
 *     程序取数+程序计算,LLM 只写报告——确定性强/省 token/铁律 8):
 *     START → collect(取数跨仓合并) → calculate(算建议量) → 条件边(无可补项直达 END) →
 *     summarize(LLM 摘要,可降级) → persist(落 ai_suggestion) → END。
 *     图在构造器装配并 compile 一次持有(节点为无状态单例,可重复 invoke);
 *     各状态键 REPLACE 策略。触发:POST /api/ai/replenishment/run(登录即可),
 *     TODO(#6): 定时接线(每日低峰,参考 AlertJob 模式)待拍板间隔后接
 */
@Slf4j
@Service
public class ReplenishWorkflow {

    private final CompiledGraph compiledGraph;

    public ReplenishWorkflow(ReplenishCollectNode collectNode,
                             ReplenishCalculateNode calculateNode,
                             ReplenishSummarizeNode summarizeNode,
                             ReplenishPersistNode persistNode) {
        try {
            KeyStrategyFactory keyStrategyFactory = new KeyStrategyFactoryBuilder()
                    .addStrategy(ReplenishStateKeys.KEY_ITEMS, KeyStrategy.REPLACE)
                    .addStrategy(ReplenishStateKeys.KEY_SCANNED, KeyStrategy.REPLACE)
                    .addStrategy(ReplenishStateKeys.KEY_DEGRADED, KeyStrategy.REPLACE)
                    .addStrategy(ReplenishStateKeys.KEY_PERSISTED, KeyStrategy.REPLACE)
                    .build();
            // 条件边分支串必须是 mappings key:无低库存项直达 END,否则进摘要节点
            StateGraph graph = new StateGraph("replenish", keyStrategyFactory)
                    .addNode("collect", AsyncNodeAction.node_async(collectNode))
                    .addNode("calculate", AsyncNodeAction.node_async(calculateNode))
                    .addNode("summarize", AsyncNodeAction.node_async(summarizeNode))
                    .addNode("persist", AsyncNodeAction.node_async(persistNode))
                    .addEdge(StateGraph.START, "collect")
                    .addEdge("collect", "calculate")
                    .addConditionalEdges("calculate",
                            AsyncEdgeAction.edge_async(state -> hasItems(state)
                                    ? "summarize" : StateGraph.END),
                            Map.of("summarize", "summarize", StateGraph.END, StateGraph.END))
                    .addEdge("summarize", "persist")
                    .addEdge("persist", StateGraph.END);
            this.compiledGraph = graph.compile();
        } catch (GraphStateException e) {
            // 图装配错误属编程期错误,启动即失败优先于运行期才发现
            throw new IllegalStateException("补货工作流图装配失败", e);
        }
    }

    private static boolean hasItems(OverAllState state) {
        List<?> items = state.value(ReplenishStateKeys.KEY_ITEMS, List.class).orElse(List.of());
        return !items.isEmpty();
    }

    /** 执行工作流:返回运行摘要;引擎异常统一包业务异常(手动触发时直接可见) */
    public ReplenishRunResult run() {
        try {
            Optional<OverAllState> result = compiledGraph.invoke(Map.of());
            OverAllState state = result.orElseThrow(() -> new BusinessException("补货工作流执行失败:无状态返回"));
            return ReplenishRunResult.builder()
                    .scannedCount(state.value(ReplenishStateKeys.KEY_SCANNED, Integer.class).orElse(0))
                    .suggestedCount(state.value(ReplenishStateKeys.KEY_ITEMS, List.class)
                            .map(list -> list.size()).orElse(0))
                    .persistedCount(state.value(ReplenishStateKeys.KEY_PERSISTED, Integer.class).orElse(0))
                    .degraded(state.value(ReplenishStateKeys.KEY_DEGRADED, Boolean.class).orElse(false))
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("补货工作流执行异常", e);
            throw new BusinessException("补货工作流执行失败: " + e.getMessage());
        }
    }
}
