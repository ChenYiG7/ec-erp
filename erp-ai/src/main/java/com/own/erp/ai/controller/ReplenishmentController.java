package com.own.erp.ai.controller;

import com.own.erp.ai.graph.ReplenishRunResult;
import com.own.erp.ai.graph.ReplenishWorkflow;
import com.own.erp.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AI 补货建议(#6 SAA Graph 工作流):手动触发端点,登录即可(与 chat 同权限口径)。
 *     建议只产出到 ai_suggestion(人工采纳闭环),不碰业务单据;定时调度 TODO(#6) 待拍板间隔后接
 */
@Tag(name = "AI补货建议", description = "SAA Graph 补货建议工作流:程序取数算建议量,LLM 写摘要(可降级);产出进 ai_suggestion 待人工确认")
@RestController
@RequestMapping("/api/ai/replenishment")
@RequiredArgsConstructor
public class ReplenishmentController {

    private final ReplenishWorkflow replenishWorkflow;

    /** 手动触发一轮补货建议工作流(取数→计算→摘要→落库),返回运行摘要 */
    @Operation(summary = "触发补货建议工作流", description = "扫描低库存 SKU 生成补货建议写入 AI 建议表,返回运行摘要;LLM 不可用时摘要自动降级为模板")
    @PostMapping("/run")
    public Result<ReplenishRunResult> run() {
        return Result.ok(replenishWorkflow.run());
    }
}
