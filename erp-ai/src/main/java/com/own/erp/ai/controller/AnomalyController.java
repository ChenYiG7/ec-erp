package com.own.erp.ai.controller;

import com.own.erp.ai.graph.AnomalyRunResult;
import com.own.erp.ai.graph.AnomalyWorkflow;
import com.own.erp.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : AI 订单异常检测(#6 两段式:规则先筛+LLM 评分):手动触发端点,登录即可
 *     (与补货/chat 同权限口径)。产出只进 ai_suggestion(人工复核闭环),不碰业务单据;
 *     定时调度 TODO(#6): 与补货同张 TODO 待拍板(接前先拍去重语义);HIGH 推通知随实际告警量评估
 */
@Tag(name = "AI订单异常检测", description = "SAA Graph 异常检测工作流:程序规则筛(WAIT_PAY/WAIT_SHIP 两态四规则)+ LLM 批量评分(可降级规则回落);产出进 ai_suggestion 待人工复核")
@RestController
@RequestMapping("/api/ai/anomaly")
@RequiredArgsConstructor
public class AnomalyController {

    private final AnomalyWorkflow anomalyWorkflow;

    /** 手动触发一轮订单异常检测工作流(规则筛→LLM 评分→落库),返回运行摘要 */
    @Operation(summary = "触发订单异常检测工作流", description = "扫描 WAIT_PAY/WAIT_SHIP 订单按四规则筛可疑单,LLM 批量评分(不可用时规则回落)后写入 AI 建议表,返回运行摘要")
    @PostMapping("/run")
    public Result<AnomalyRunResult> run() {
        return Result.ok(anomalyWorkflow.run());
    }
}
