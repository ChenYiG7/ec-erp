package com.own.erp.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.ai.request.query.AiSuggestionQuery;
import com.own.erp.ai.response.AiSuggestionResponse;
import com.own.erp.ai.service.AiSuggestionService;
import com.own.erp.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AI建议表:AiSuggestion 域整域收口,一律走 AiSuggestionService(docs/07 §2.1)。
 *     系统写入表:人工侧仅查询与确认动作(采纳/忽略);AI 产出落库走 Service 内部 save,不开放 HTTP 写接口。
 *     采纳只落建议表确认状态,业务动作(补货/改价)由人工走对应业务接口——AI 永不直写业务表(docs/07 §7)
 */
@Tag(name = "AI建议", description = "AI 建议闭环:查询 + 人工确认(采纳/忽略);AI 产出经内部入口落库,无 HTTP 写接口")
@RestController
@RequestMapping("/api/ai/suggestions")
@RequiredArgsConstructor
public class AiSuggestionController {

    private final AiSuggestionService aiSuggestionService;

    @Operation(summary = "分页查询AI建议")
    @GetMapping
    public Result<Page<AiSuggestionResponse>> page(AiSuggestionQuery query) {
        return Result.ok(aiSuggestionService.page(query));
    }

    @Operation(summary = "AI建议详情", description = "不存在返回 null data")
    @GetMapping("/{id}")
    public Result<AiSuggestionResponse> get(@PathVariable Long id) {
        return Result.ok(aiSuggestionService.getById(id));
    }

    @Operation(summary = "采纳建议", description = "0待确认→1已采纳,条件更新即守卫(cas 脱靶=已处理/不存在,报错);"
            + "仅落确认状态,业务动作由人工走对应业务接口")
    @PostMapping("/{id}/adopt")
    public Result<Void> adopt(@PathVariable Long id) {
        aiSuggestionService.adopt(id);
        return Result.ok();
    }

    @Operation(summary = "忽略建议", description = "0待确认→2已忽略,条件更新即守卫,与采纳终态互斥")
    @PostMapping("/{id}/ignore")
    public Result<Void> ignore(@PathVariable Long id) {
        aiSuggestionService.ignore(id);
        return Result.ok();
    }
}
