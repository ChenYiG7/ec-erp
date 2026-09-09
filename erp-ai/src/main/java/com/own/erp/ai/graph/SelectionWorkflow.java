package com.own.erp.ai.graph;

import com.own.erp.common.exception.BusinessException;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 智能选品工作流(#17 落位表「智能选品」三期提前,2026-09-08 用户拍板;
 *     SAA Graph Core,照 Anomaly/Purchase 工作流母本):
 *     START → collect(纯程序取数装配,零 token) → 条件边(零候选直达 END) →
 *     score(纯程序三维加权评分,确定性可复算) → summarize(LLM 写推荐理由,可降级) →
 *     persist(落 ai_suggestion) → END。
 *     图在构造器装配并 compile 一次持有(节点为无状态单例,可重复 invoke);各状态键 REPLACE 策略。
 *     触发:POST /api/ai/selection/run(登录即可,同补货/采购);V1 仅手动触发不接定时
 *     (选品是运营决策节奏,手动触发即够——同文案工作流拍板口径),TODO(#17) 槽位
 */
@Slf4j
@Service
public class SelectionWorkflow {

    private final CompiledGraph compiledGraph;

    public SelectionWorkflow(SelectionCollectNode collectNode,
                             SelectionScoreNode scoreNode,
                             SelectionSummarizeNode summarizeNode,
                             SelectionPersistNode persistNode) {
        try {
            KeyStrategyFactory keyStrategyFactory = new KeyStrategyFactoryBuilder()
                    .addStrategy(SelectionStateKeys.KEY_CANDIDATES, KeyStrategy.REPLACE)
                    .addStrategy(SelectionStateKeys.KEY_SELECTED, KeyStrategy.REPLACE)
                    .addStrategy(SelectionStateKeys.KEY_SCANNED, KeyStrategy.REPLACE)
                    .addStrategy(SelectionStateKeys.KEY_DEGRADED, KeyStrategy.REPLACE)
                    .addStrategy(SelectionStateKeys.KEY_PERSISTED, KeyStrategy.REPLACE)
                    .build();
            // 条件边分支串必须是 mappings key:零候选直达 END,否则进评分节点
            StateGraph graph = new StateGraph("selection", keyStrategyFactory)
                    .addNode("collect", AsyncNodeAction.node_async(collectNode))
                    .addNode("score", AsyncNodeAction.node_async(scoreNode))
                    .addNode("summarize", AsyncNodeAction.node_async(summarizeNode))
                    .addNode("persist", AsyncNodeAction.node_async(persistNode))
                    .addEdge(StateGraph.START, "collect")
                    .addConditionalEdges("collect",
                            AsyncEdgeAction.edge_async(state -> hasCandidates(state)
                                    ? "score" : StateGraph.END),
                            Map.of("score", "score", StateGraph.END, StateGraph.END))
                    .addEdge("score", "summarize")
                    .addEdge("summarize", "persist")
                    .addEdge("persist", StateGraph.END);
            this.compiledGraph = graph.compile();
        } catch (GraphStateException e) {
            // 图装配错误属编程期错误,启动即失败优先于运行期才发现
            throw new IllegalStateException("智能选品工作流图装配失败", e);
        }
    }

    private static boolean hasCandidates(OverAllState state) {
        List<?> candidates = state.value(SelectionStateKeys.KEY_CANDIDATES, List.class)
                .orElse(List.of());
        return !candidates.isEmpty();
    }

    /** 执行工作流:返回运行摘要;引擎异常统一包业务异常(手动触发时直接可见) */
    public SelectionRunResult run() {
        try {
            Optional<OverAllState> result = compiledGraph.invoke(Map.of());
            OverAllState state = result.orElseThrow(() -> new BusinessException("智能选品工作流执行失败:无状态返回"));
            return SelectionRunResult.builder()
                    .scannedCount(state.value(SelectionStateKeys.KEY_SCANNED, Integer.class).orElse(0))
                    .candidateCount(state.value(SelectionStateKeys.KEY_CANDIDATES, List.class)
                            .map(list -> list.size()).orElse(0))
                    .selectedCount(state.value(SelectionStateKeys.KEY_SELECTED, List.class)
                            .map(list -> list.size()).orElse(0))
                    .persistedCount(state.value(SelectionStateKeys.KEY_PERSISTED, Integer.class).orElse(0))
                    .degraded(state.value(SelectionStateKeys.KEY_DEGRADED, Boolean.class).orElse(false))
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("智能选品工作流执行异常", e);
            throw new BusinessException("智能选品工作流执行失败: " + e.getMessage());
        }
    }
}
