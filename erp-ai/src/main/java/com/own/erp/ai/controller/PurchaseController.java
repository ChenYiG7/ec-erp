package com.own.erp.ai.controller;

import com.own.erp.ai.graph.PurchaseRunResult;
import com.own.erp.ai.graph.PurchaseWorkflow;
import com.own.erp.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : AI 采购建议(#17 三期候选,SAA Graph 工作流):手动触发端点,登录即可
 *     (与 chat/补货同权限口径)。建议只产出到 ai_suggestion(人工采纳闭环,不碰采购单据);
 *     V1 不接定时,调度接线待实际使用节奏拍板(TODO(#17) 槽位)
 */
@Tag(name = "AI采购建议", description = "SAA Graph 采购建议工作流:补货缺口按供应商聚合为采购计划,LLM 写摘要(可降级);产出进 ai_suggestion 待人工确认")
@RestController
@RequestMapping("/api/ai/purchase")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseWorkflow purchaseWorkflow;

    /** 手动触发一轮采购建议工作流(取数→聚合→摘要→落库),返回运行摘要 */
    @Operation(summary = "触发采购建议工作流", description = "将低库存补货缺口按最新采购供应商聚合为采购计划建议写入 AI 建议表,"
            + "返回运行摘要;无采购历史的 SKU 不纳入,LLM 不可用时摘要自动降级为模板")
    @PostMapping("/run")
    public Result<PurchaseRunResult> run() {
        return Result.ok(purchaseWorkflow.run());
    }
}
