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
 * @Description : 文案生成工作流(#17 三期候选「产品描述生成」V1 落地,SAA Graph Core,
 *     照 Purchase/Anomaly 工作流母本;落位表拍板:listing 文案生成产出进 ai_suggestion,
 *     人工采纳后复制使用,V1 不自动回填平台):START → collect(扫启用商品+去重+材料装配)→
 *     条件边(无待生成商品直达 END,零 LLM 成本)→ generate(LLM 批量产文案,可降级零产出)→
 *     persist(落 ai_suggestion) → END。
 *     图在构造器装配并 compile 一次持有(节点为无状态单例,可重复 invoke);
 *     各状态键 REPLACE 策略。触发:POST /api/ai/copywriting/run(登录即可);
 *     V1 不接定时(文案采纳是人工编辑节奏,批量生成手动触发即够,定时接线待实际使用拍板)
 */
@Slf4j
@Service
public class CopywritingWorkflow {

    private final CompiledGraph compiledGraph;

    public CopywritingWorkflow(CopyCollectNode collectNode,
                               CopyGenerateNode generateNode,
                               CopyPersistNode persistNode) {
        try {
            KeyStrategyFactory keyStrategyFactory = new KeyStrategyFactoryBuilder()
                    .addStrategy(CopyStateKeys.KEY_ITEMS, KeyStrategy.REPLACE)
                    .addStrategy(CopyStateKeys.KEY_SCANNED, KeyStrategy.REPLACE)
                    .addStrategy(CopyStateKeys.KEY_SKIPPED, KeyStrategy.REPLACE)
                    .addStrategy(CopyStateKeys.KEY_DEGRADED, KeyStrategy.REPLACE)
                    .addStrategy(CopyStateKeys.KEY_PERSISTED, KeyStrategy.REPLACE)
                    .build();
            // 条件边分支串必须是 mappings key:无待生成商品直达 END,否则进生成节点
            StateGraph graph = new StateGraph("copywriting", keyStrategyFactory)
                    .addNode("collect", AsyncNodeAction.node_async(collectNode))
                    .addNode("generate", AsyncNodeAction.node_async(generateNode))
                    .addNode("persist", AsyncNodeAction.node_async(persistNode))
                    .addEdge(StateGraph.START, "collect")
                    .addConditionalEdges("collect",
                            AsyncEdgeAction.edge_async(state -> hasItems(state)
                                    ? "generate" : StateGraph.END),
                            Map.of("generate", "generate", StateGraph.END, StateGraph.END))
                    .addEdge("generate", "persist")
                    .addEdge("persist", StateGraph.END);
            this.compiledGraph = graph.compile();
        } catch (GraphStateException e) {
            // 图装配错误属编程期错误,启动即失败优先于运行期才发现
            throw new IllegalStateException("文案生成工作流图装配失败", e);
        }
    }

    private static boolean hasItems(OverAllState state) {
        List<?> items = state.value(CopyStateKeys.KEY_ITEMS, List.class).orElse(List.of());
        return !items.isEmpty();
    }

    /** 执行工作流:返回运行摘要;引擎异常统一包业务异常(手动触发时直接可见) */
    public CopyRunResult run() {
        try {
            Optional<OverAllState> result = compiledGraph.invoke(Map.of());
            OverAllState state = result.orElseThrow(() -> new BusinessException("文案生成工作流执行失败:无状态返回"));
            return CopyRunResult.builder()
                    .scannedCount(state.value(CopyStateKeys.KEY_SCANNED, Integer.class).orElse(0))
                    .pendingSkippedCount(state.value(CopyStateKeys.KEY_SKIPPED, Integer.class).orElse(0))
                    .generatedCount(state.value(CopyStateKeys.KEY_PERSISTED, Integer.class).orElse(0))
                    .degraded(state.value(CopyStateKeys.KEY_DEGRADED, Boolean.class).orElse(false))
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文案生成工作流执行异常", e);
            throw new BusinessException("文案生成工作流执行失败: " + e.getMessage());
        }
    }
}
