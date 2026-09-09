package com.own.erp.ai.controller;

import com.own.erp.ai.graph.SelectionRunResult;
import com.own.erp.ai.graph.SelectionWorkflow;
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
 * @Description : AI 智能选品(#17 落位表「智能选品」三期提前,SAA Graph 工作流):手动触发端点,
 *     登录即可(与 chat/补货/采购同权限口径)。建议只产出到 ai_suggestion(人工确认闭环,
 *     不碰商品/广告/库存表);V1 不接定时(选品是运营决策节奏,手动触发即够,TODO(#17) 槽位)
 */
@Tag(name = "AI智能选品", description = "SAA Graph 智能选品工作流:启用商品 SKU 三维加权评分(销量规模/动销趋势/毛利率)+ 风险定级,LLM 写推荐理由(可降级);产出进 ai_suggestion 待人工确认")
@RestController
@RequestMapping("/api/ai/selection")
@RequiredArgsConstructor
public class SelectionController {

    private final SelectionWorkflow selectionWorkflow;

    /** 手动触发一轮选品评分工作流(取数→评分→摘要→落库),返回运行摘要 */
    @Operation(summary = "触发智能选品工作流", description = "对启用商品 SKU 做三维加权评分(销量规模/动销趋势/毛利率)与风险定级,"
            + "综合分降序入选写AI建议表,返回运行摘要;零候选直接空轮,LLM 不可用时推荐理由自动降级为模板")
    @PostMapping("/run")
    public Result<SelectionRunResult> run() {
        return Result.ok(selectionWorkflow.run());
    }
}
