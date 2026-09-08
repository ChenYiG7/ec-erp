package com.own.erp.ai.controller;

import com.own.erp.ai.graph.CopyRunResult;
import com.own.erp.ai.graph.CopywritingWorkflow;
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
 * @Description : AI 文案生成(#17 三期候选「产品描述生成」,SAA Graph 工作流):手动触发端点,
 *     登录即可(与 chat/补货/采购同权限口径)。建议只产出到 ai_suggestion
 *     (人工采纳闭环,采纳后文案在详情 payload 复制使用,V1 不自动回填平台 listing);
 *     V1 不接定时,调度接线待实际使用节奏拍板(TODO(#17) 槽位)
 */
@Tag(name = "AI文案生成", description = "SAA Graph 文案生成工作流:为商品库启用商品批量生成 listing 文案建议"
        + "(标题/五点描述/商品描述/关键词),LLM 不可用时本轮零产出;产出进 ai_suggestion 待人工确认")
@RestController
@RequestMapping("/api/ai/copywriting")
@RequiredArgsConstructor
public class CopywritingController {

    private final CopywritingWorkflow copywritingWorkflow;

    /** 手动触发一轮文案生成工作流(扫描→去重→LLM 生成→落库),返回运行摘要 */
    @Operation(summary = "触发文案生成工作流", description = "扫描商品库启用商品,跳过已有待确认文案建议的商品,"
            + "为其余商品批量生成 listing 文案建议写入 AI 建议表,返回运行摘要;"
            + "LLM 不可用时本轮零产出(degraded=true),不产生垃圾建议")
    @PostMapping("/run")
    public Result<CopyRunResult> run() {
        return Result.ok(copywritingWorkflow.run());
    }
}
