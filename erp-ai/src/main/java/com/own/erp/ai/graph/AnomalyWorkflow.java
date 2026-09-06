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
 * @Date : 2026/9/7
 * @Description : 订单异常检测工作流(#6 SAA Graph Core,docs/02 §13 两段式拍板:规则引擎先筛→
 *     LLM 只评可疑样本控成本):
 *     START → scan(纯程序四规则筛) → 条件边(无可疑单直达 END,零 LLM 成本) →
 *     score(LLM 批量评分,可降级) → persist(落 ai_suggestion) → END。
 *     图在构造器装配并 compile 一次持有(节点为无状态单例,可重复 invoke);
 *     各状态键 REPLACE 策略。触发:POST /api/ai/anomaly/run(登录即可),
 *     TODO(#6): 定时接线(与补货同张 TODO 待拍板,接前先拍去重语义)
 */
@Slf4j
@Service
public class AnomalyWorkflow {

    private final CompiledGraph compiledGraph;

    public AnomalyWorkflow(AnomalyScanNode scanNode,
                           AnomalyScoreNode scoreNode,
                           AnomalyPersistNode persistNode) {
        try {
            KeyStrategyFactory keyStrategyFactory = new KeyStrategyFactoryBuilder()
                    .addStrategy(AnomalyStateKeys.KEY_ITEMS, KeyStrategy.REPLACE)
                    .addStrategy(AnomalyStateKeys.KEY_SCANNED, KeyStrategy.REPLACE)
                    .addStrategy(AnomalyStateKeys.KEY_DEGRADED, KeyStrategy.REPLACE)
                    .addStrategy(AnomalyStateKeys.KEY_PERSISTED, KeyStrategy.REPLACE)
                    .addStrategy(AnomalyStateKeys.KEY_LLM_SCORED, KeyStrategy.REPLACE)
                    .build();
            // 条件边分支串必须是 mappings key:无可疑单直达 END,否则进评分节点
            StateGraph graph = new StateGraph("anomaly", keyStrategyFactory)
                    .addNode("scan", AsyncNodeAction.node_async(scanNode))
                    .addNode("score", AsyncNodeAction.node_async(scoreNode))
                    .addNode("persist", AsyncNodeAction.node_async(persistNode))
                    .addEdge(StateGraph.START, "scan")
                    .addConditionalEdges("scan",
                            AsyncEdgeAction.edge_async(state -> hasItems(state)
                                    ? "score" : StateGraph.END),
                            Map.of("score", "score", StateGraph.END, StateGraph.END))
                    .addEdge("score", "persist")
                    .addEdge("persist", StateGraph.END);
            this.compiledGraph = graph.compile();
        } catch (GraphStateException e) {
            // 图装配错误属编程期错误,启动即失败优先于运行期才发现
            throw new IllegalStateException("订单异常工作流图装配失败", e);
        }
    }

    private static boolean hasItems(OverAllState state) {
        List<?> items = state.value(AnomalyStateKeys.KEY_ITEMS, List.class).orElse(List.of());
        return !items.isEmpty();
    }

    /** 执行工作流:返回运行摘要;引擎异常统一包业务异常(手动触发时直接可见) */
    public AnomalyRunResult run() {
        try {
            Optional<OverAllState> result = compiledGraph.invoke(Map.of());
            OverAllState state = result.orElseThrow(() -> new BusinessException("订单异常工作流执行失败:无状态返回"));
            return AnomalyRunResult.builder()
                    .scannedCount(state.value(AnomalyStateKeys.KEY_SCANNED, Integer.class).orElse(0))
                    .suspiciousCount(state.value(AnomalyStateKeys.KEY_ITEMS, List.class)
                            .map(list -> list.size()).orElse(0))
                    .persistedCount(state.value(AnomalyStateKeys.KEY_PERSISTED, Integer.class).orElse(0))
                    .llmScoredCount(state.value(AnomalyStateKeys.KEY_LLM_SCORED, Integer.class).orElse(0))
                    .degraded(state.value(AnomalyStateKeys.KEY_DEGRADED, Boolean.class).orElse(false))
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("订单异常工作流执行异常", e);
            throw new BusinessException("订单异常工作流执行失败: " + e.getMessage());
        }
    }
}
