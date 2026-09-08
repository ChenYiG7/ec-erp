package com.own.erp.ai.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 知识库粘贴文本接入入参(docs/07 §1:写侧 command + @Valid 落点,record+@Builder);
 *     content 字符数上限的精确校验在 KbIngestService(读 ErpAiProperties 配置,注解硬编码做不到动态值)
 */
@Builder
public record KbTextUploadCommand(

        /** 文档标题(可空,默认取内容首行截断) */
        @Size(max = 128, message = "标题不能超过 128 字")
        String title,

        /** 文档正文(粘贴文本) */
        @NotBlank(message = "文档内容不能为空")
        String content
) {

    /** 手写 toString 脱敏(知识库正文可能含业务资料,防整体入日志;docs/07 §1 ④) */
    @Override
    public String toString() {
        return "KbTextUploadCommand(title=" + title + ",content=***)";
    }
}
