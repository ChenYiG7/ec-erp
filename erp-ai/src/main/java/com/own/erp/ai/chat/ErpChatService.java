package com.own.erp.ai.chat;

import cn.hutool.core.util.StrUtil;
import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.service.AiChatMessageService;
import com.own.erp.ai.service.AiChatSessionService;
import com.own.erp.ai.tools.AftersaleTools;
import com.own.erp.ai.tools.GoodsTools;
import com.own.erp.ai.tools.InventoryTools;
import com.own.erp.ai.tools.OrderTools;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AI 对话服务(#6):ChatClient(OpenAI 兼容,DeepSeek/通义/GLM 可切换)+ tools 包只读工具;
 *     同步 chat / 流式 chatStream(SSE)双通道;每轮对话落 ai_chat_session/ai_chat_message 可审计(docs/07 §9):
 *     USER 行提问即落,AI 行完成后落——同步通道 token 用量取 Usage 回填,流式链路 usage 取不到置 NULL;
 *     TOOL 中间行经 AuditingToolCallback 装饰器在工具 invoke 前落(工具名+入参 JSON 截断),双通道统一生效(#6)。
 *     无 AI_API_KEY 时调用友好报错、启动不炸(模型客户端构造不校验 key);仅 HTTP 登录链路可用
 *     (userId 走 CurrentUserApi,Job/系统链路勿调)
 */
@Service
@Slf4j
public class ErpChatService {

    private final ChatClient chatClient;
    private final ErpAiProperties props;
    private final AiRuntimeProperties runtime;
    private final CurrentUserApi currentUserApi;
    private final AiChatSessionService aiChatSessionService;
    private final AiChatMessageService aiChatMessageService;
    /** 只读工具白名单转 ToolCallback 一次构建复用;每请求经 wrapToolCallbacks 包装审计装饰器 */
    private final ToolCallback[] toolCallbacks;

    /** 模型 api-key 原值(仅判空作"AI 未配置"友好报错,不落日志/返回体——docs/07 §7 凭证纪律) */
    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    /** 契约接口注入一律 @Lazy 断构造环:实现收口 erp-api 反向注入域 Service(docs/07 §2.2);
     *  ChatClient.Builder 走 Spring AI 自动装配(模型由 spring.ai.openai.* 配置),build 一次复用 */
    public ErpChatService(ChatClient.Builder chatClientBuilder,
                          ErpAiProperties props,
                          AiRuntimeProperties runtime,
                          @Lazy CurrentUserApi currentUserApi,
                          AiChatSessionService aiChatSessionService,
                          AiChatMessageService aiChatMessageService,
                          OrderTools orderTools,
                          InventoryTools inventoryTools,
                          GoodsTools goodsTools,
                          AftersaleTools aftersaleTools) {
        this.chatClient = chatClientBuilder.build();
        this.props = props;
        this.runtime = runtime;
        this.currentUserApi = currentUserApi;
        this.aiChatSessionService = aiChatSessionService;
        this.aiChatMessageService = aiChatMessageService;
        this.toolCallbacks = ToolCallbacks.from(orderTools, inventoryTools, goodsTools, aftersaleTools);
    }

    /** 同步对话:归属校验 → 标题回填 → USER 落库 → 模型调用 → AI 落库(带 token 用量)→ 回复 */
    public String chat(Long sessionId, String message) {
        String question = prepare(sessionId, message);
        var response = chatClient.prompt()
                .system(runtime.systemPrompt())
                .options(chatOptions())
                .toolCallbacks(wrapToolCallbacks(sessionId))
                .user(question)
                .call()
                .chatResponse();
        String reply = response == null || response.getResult() == null
                ? ""
                : StrUtil.nullToEmpty(response.getResult().getOutput().getText());
        Usage usage = response == null ? null : response.getMetadata().getUsage();
        aiChatMessageService.append(sessionId, AiConsts.ROLE_AI, reply, null,
                usage == null ? null : usage.getPromptTokens().intValue(),
                usage == null ? null : usage.getCompletionTokens().intValue());
        return reply;
    }

    /**
     * 流式对话(SSE):前置同 chat;AI 行在流完成后聚合全文落库,
     * token 用量流式 chunk 不稳定取不到置 NULL(docs/03 §7 "尽力而为"口径)。
     * 错误暴露面(2026-09-07 联调校准):上游模型异常(如密钥失效 401)不得穿透异步路径伪装成
     * "未登录或登录已过期"误导前端——转成一条可见错误帧;失败轮不落 AI 行(USER/TOOL 行已留痕),不污染历史。
     * 模型连接复用 spring.ai.openai.*(Builder 装配期单源);#18 后 system prompt/模型名每请求取值
     * AiRuntimeProperties(DB 覆盖值优先,yml 默认兜底,保存端点即时生效)
     */
    public Flux<String> chatStream(Long sessionId, String message) {
        String question = prepare(sessionId, message);
        StringBuilder full = new StringBuilder();
        AtomicBoolean failed = new AtomicBoolean(false);
        return chatClient.prompt()
                .system(runtime.systemPrompt())
                .options(chatOptions())
                .toolCallbacks(wrapToolCallbacks(sessionId))
                .user(question)
                .stream()
                .content()
                .doOnNext(full::append)
                .onErrorResume(e -> {
                    failed.set(true);
                    log.warn("AI 流式调用失败 :{}", e.getMessage());
                    return Flux.just(StrUtil.format(
                            "【AI 调用失败,请稍后重试或改用同步对话】({})",
                            StrUtil.nullToDefault(e.getMessage(), "未知异常")));
                })
                .doOnComplete(() -> {
                    if (!failed.get()) {
                        aiChatMessageService.append(
                                sessionId, AiConsts.ROLE_AI, full.toString(), null, null, null);
                    }
                });
    }

    /** 对话前置:配置检查 → 归属校验 → 首条消息回填标题 → USER 行落库;返回清洗后问题文本 */
    private String prepare(Long sessionId, String message) {
        requireConfigured();
        String question = StrUtil.trim(message);
        if (StrUtil.isBlank(question)) {
            throw new BusinessException("消息内容不能为空");
        }
        aiChatSessionService.getOwned(sessionId, currentUserApi.currentUserId(), AiConsts.SESSION_SOURCE_CHAT);
        aiChatSessionService.renameIfDefault(sessionId, question);
        aiChatMessageService.append(sessionId, AiConsts.ROLE_USER, question, null, null, null);
        return question;
    }

    /** 每请求包装审计装饰器(持有 sessionId 落 TOOL 行,装饰器不可跨请求复用) */
    private ToolCallback[] wrapToolCallbacks(Long sessionId) {
        return Arrays.stream(toolCallbacks)
                .map(cb -> new AuditingToolCallback(cb, sessionId, aiChatMessageService,
                        props.getToolAuditMaxLength()))
                .toArray(ToolCallback[]::new);
    }

    /**
     * 每请求模型选项(#18 系统设置):模型名热切——DB 覆盖值优先生效,
     * 无覆盖返回 null = 用 yml/Builder 默认模型,不额外建 options(保持原链路)。
     * Spring AI 2.0.1 options() 签名收 Builder 泛型(ChatOptions.Builder<?>),传 builder 不传成品
     */
    private ChatOptions.Builder<?> chatOptions() {
        return runtime.chatModel().map(model -> ChatOptions.<Object>builder().model(model)).orElse(null);
    }

    /** AI 未配置友好报错(启动不炸,调用时拦):密钥走环境变量 AI_API_KEY 或 local.properties 同名键 */
    private void requireConfigured() {
        if (StrUtil.isBlank(apiKey)) {
            throw new BusinessException("AI 能力未配置:请设置环境变量 AI_API_KEY(或 local.properties 同名键)后重启");
        }
    }
}
