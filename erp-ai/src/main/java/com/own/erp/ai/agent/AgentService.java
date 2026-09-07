package com.own.erp.ai.agent;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.request.query.AiChatSessionQuery;
import com.own.erp.ai.response.AiChatMessageResponse;
import com.own.erp.ai.response.AiChatSessionResponse;
import com.own.erp.ai.service.AiChatMessageService;
import com.own.erp.ai.service.AiChatSessionService;
import com.own.erp.ai.tools.AftersaleTools;
import com.own.erp.ai.tools.GoodsTools;
import com.own.erp.ai.tools.InventoryTools;
import com.own.erp.ai.tools.OrderTools;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : Agent 服务(四期 agent/ V1.5,AgentScope 2.0.2 ReActAgent 会话式多轮):
 *     会话复用 ai_chat_session/ai_chat_message(source=AGENT 隔离,与 chat 域同构同审计);
 *     每轮 = 归属校验 → USER 行落库 → 历史重放(USER/AI 文本行转 Msg,TOOL 行不重放)→
 *     每请求新建 ReActAgent(Toolkit 桥接会话绑定版工具桥,工具调用落 TOOL 行)→ streamEvents 流式;
 *     模型 = AgentScope 内建 OpenAIChatModel,连接复用 spring.ai.openai.*(与 chat 单一来源);
 *     错误帧兜底同 chatStream 口径(失败轮不落 AI 行)。懒建模型:无 key 启动不炸、调用时拦截。
 *     #18 系统设置接线:prompt/maxIters/history/审计截断/连接三件套(base-url·model)每轮经
 *     AiRuntimeProperties 取值(DB 覆盖优先),保存端点即时生效;api-key 禁入 sys_config(docs/07 §7)
 */
@Slf4j
@Service
public class AgentService {

    /** 同步调用超时护栏(ReAct 多轮 + 工具执行,慢于普通 chat) */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(120);

    private final ErpAiProperties props;
    private final AiRuntimeProperties runtime;
    private final List<ToolCallback> toolCallbacks;
    private final AiChatSessionService aiChatSessionService;
    private final AiChatMessageService aiChatMessageService;
    private final CurrentUserApi currentUserApi;
    private final String apiKey;
    private final String baseUrl;
    private final String modelName;
    /** 连接快照指纹(base-url/model,api-key 不入指纹——凭证轮换重启生效,docs/07 §7);
     *  变更即重建 OpenAIChatModel(#18 连接三件套热切) */
    private volatile String modelFingerprint = "";
    /** 测试注入口;生产为 null,首次调用按 spring.ai.openai.* 构建 */
    private final Model injectedModel;
    private volatile Model model;

    @org.springframework.beans.factory.annotation.Autowired
    public AgentService(OrderTools orderTools,
                        InventoryTools inventoryTools,
                        GoodsTools goodsTools,
                        AftersaleTools aftersaleTools,
                        ErpAiProperties props,
                        AiRuntimeProperties runtime,
                        AiChatSessionService aiChatSessionService,
                        AiChatMessageService aiChatMessageService,
                        @Lazy CurrentUserApi currentUserApi,
                        @Value("${spring.ai.openai.base-url:https://api.deepseek.com}") String baseUrl,
                        @Value("${spring.ai.openai.api-key:}") String apiKey,
                        @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String modelName) {
        // tools/ 四类 @Tool 经 ToolCallbacks.from 转回调(ErpChatService 同款,本地转换非 Bean——
        // 容器内无 ToolCallback Bean,注入 List<ToolCallback> 恒为空,工具面会静默丢失,#6 联调 2026-09-07 勘误)
        this(List.of(ToolCallbacks.from(orderTools, inventoryTools, goodsTools, aftersaleTools)),
                props, runtime, aiChatSessionService, aiChatMessageService, currentUserApi,
                baseUrl, apiKey, modelName);
    }

    /** 装配口:显式回调列表(生产经 ToolCallbacks.from 转换后进入;测试注入桩回调) */
    AgentService(List<ToolCallback> toolCallbacks,
                 ErpAiProperties props,
                 AiRuntimeProperties runtime,
                 AiChatSessionService aiChatSessionService,
                 AiChatMessageService aiChatMessageService,
                 CurrentUserApi currentUserApi,
                 @Value("${spring.ai.openai.base-url:https://api.deepseek.com}") String baseUrl,
                 @Value("${spring.ai.openai.api-key:}") String apiKey,
                 @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String modelName) {
        this.props = props;
        this.runtime = runtime;
        this.toolCallbacks = toolCallbacks;
        this.aiChatSessionService = aiChatSessionService;
        this.aiChatMessageService = aiChatMessageService;
        this.currentUserApi = currentUserApi;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.injectedModel = null;
    }

    /** 测试装配口:注入假 Model 与消息服务桩,免真实客户端 */
    AgentService(ErpAiProperties props, AiRuntimeProperties runtime, Model model, List<ToolCallback> toolCallbacks,
                 AiChatSessionService aiChatSessionService, AiChatMessageService aiChatMessageService,
                 CurrentUserApi currentUserApi, String apiKey) {
        this.props = props;
        this.runtime = runtime;
        this.injectedModel = model;
        this.toolCallbacks = toolCallbacks;
        this.aiChatSessionService = aiChatSessionService;
        this.aiChatMessageService = aiChatMessageService;
        this.currentUserApi = currentUserApi;
        this.apiKey = apiKey;
        this.baseUrl = "http://localhost";
        this.modelName = "fake";
    }

    /** 新建 Agent 会话(source=AGENT,与 chat 域会话列表隔离;role 不落会话——会话不绑角色,可跨角色续聊) */
    public Long createSession(String title) {
        requireConfigured();
        return aiChatSessionService.create(currentUserApi.currentUserId(), title,
                AiConsts.SESSION_SOURCE_AGENT);
    }

    /** 我的 Agent 会话分页(仅 source=AGENT,与 chat 域列表隔离) */
    public Page<AiChatSessionResponse> pageSessions(AiChatSessionQuery query) {
        return aiChatSessionService
                .pageMine(currentUserApi.currentUserId(), query, AiConsts.SESSION_SOURCE_AGENT);
    }

    /** 会话历史消息(时间正序;仅本人 source=AGENT 会话可查,跨源同报"会话不存在") */
    public List<AiChatMessageResponse> listMessages(Long sessionId) {
        aiChatSessionService.getOwned(sessionId, currentUserApi.currentUserId(),
                AiConsts.SESSION_SOURCE_AGENT);
        return aiChatMessageService.listBySessionId(sessionId);
    }

    /**
     * 流式对话:历史重放多轮 → ReAct(思考→调只读工具→观察→作答)→ TextBlock delta 逐段吐出。
     * AI 行在流完成后聚合落库;失败轮只留 USER/TOOL 行、吐错误帧(同 chatStream 口径)
     */
    public Flux<String> chatStream(AgentRole role, Long sessionId, String message) {
        requireConfigured();
        String question = StrUtil.trim(message);
        if (StrUtil.isBlank(question)) {
            throw new BusinessException("消息内容不能为空");
        }
        aiChatSessionService.getOwned(sessionId, currentUserApi.currentUserId(),
                AiConsts.SESSION_SOURCE_AGENT);
        aiChatSessionService.renameIfDefault(sessionId, question);
        aiChatMessageService.append(sessionId, AiConsts.ROLE_USER, question, null, null, null);

        ReActAgent agent = buildAgent(role, sessionId);
        List<Msg> inputs = replayInputs(sessionId);
        StringBuilder full = new StringBuilder();
        AtomicBoolean failed = new AtomicBoolean(false);
        return agent.streamEvents(inputs)
                .mapNotNull(this::textDeltaOf)
                .doOnNext(full::append)
                .onErrorResume(e -> {
                    failed.set(true);
                    log.warn("Agent 流式调用失败 :{}", e.getMessage());
                    return Flux.just(StrUtil.format(
                            "【Agent 调用失败,请稍后重试】({})",
                            StrUtil.nullToDefault(e.getMessage(), "未知异常")));
                })
                .doOnComplete(() -> {
                    if (!failed.get() && full.length() > 0) {
                        aiChatMessageService.append(sessionId, AiConsts.ROLE_AI,
                                full.toString(), null, null, null);
                    }
                });
    }

    /** 同步对话:聚合全部帧后返回(错误帧原样作为回复,与 SSE 形态一致) */
    public String chatSync(AgentRole role, Long sessionId, String message) {
        return String.join("", chatStream(role, sessionId, message).collectList().block(CALL_TIMEOUT));
    }

    /**
     * 历史重放:USER/AI 文本行转 Msg(id 升序,含本轮刚落的 USER 行);TOOL 行不重放(工具细节不影响连续性)。
     * 长度截断:只重放最近 historyMaxMessages 行(erp.ai.agent.history-max-messages,防长会话 token 膨胀;
     * 本轮提问是重放集合最后一行,恒不被截掉)
     */
    private List<Msg> replayInputs(Long sessionId) {
        List<AiChatMessageResponse> replayable = new ArrayList<>();
        for (AiChatMessageResponse row : aiChatMessageService.listBySessionId(sessionId)) {
            if (AiConsts.ROLE_USER.equals(row.role()) || AiConsts.ROLE_AI.equals(row.role())) {
                replayable.add(row);
            }
        }
        int max = Math.max(runtime.agentHistoryMaxMessages(), 1);
        int from = replayable.size() > max ? replayable.size() - max : 0;
        List<Msg> inputs = new ArrayList<>();
        for (AiChatMessageResponse row : replayable.subList(from, replayable.size())) {
            if (AiConsts.ROLE_USER.equals(row.role())) {
                inputs.add(Msg.builder().role(MsgRole.USER).textContent(row.content()).build());
            } else {
                inputs.add(Msg.builder().role(MsgRole.ASSISTANT).textContent(row.content()).build());
            }
        }
        if (inputs.isEmpty()) {
            throw new BusinessException("会话历史为空,对话失败");
        }
        return inputs;
    }

    /** 事件 → 文本增量:仅 TextBlockDeltaEvent 出货,其余事件(工具调用/结果等)静默 */
    private String textDeltaOf(AgentEvent event) {
        return event instanceof TextBlockDeltaEvent delta ? delta.getDelta() : null;
    }

    private ReActAgent buildAgent(AgentRole role, Long sessionId) {
        Toolkit toolkit = new Toolkit();
        toolCallbacks.stream()
                .filter(cb -> role.allows(cb.getToolDefinition().name()))
                .map(cb -> new SpringAiAgentToolBridge(cb, sessionId, aiChatMessageService,
                        runtime.toolAuditMaxLength()))
                .forEach(toolkit::registerAgentTool);
        return ReActAgent.builder()
                .name("erp-" + role.name().toLowerCase())
                .sysPrompt(sysPromptOf(role))
                .model(model())
                .toolkit(toolkit)
                .maxIters(runtime.agentMaxIters())
                .build();
    }

    private String sysPromptOf(AgentRole role) {
        return role == AgentRole.OPS ? runtime.agentOpsPrompt() : runtime.agentSupportPrompt();
    }

    /** 角色可用的工具名(包级可见,单测断言白名单过滤;非执行态桥,不带审计) */
    List<String> listBridgedToolNames(AgentRole role) {
        Toolkit toolkit = new Toolkit();
        toolCallbacks.stream()
                .filter(cb -> role.allows(cb.getToolDefinition().name()))
                .map(SpringAiAgentToolBridge::new)
                .forEach(toolkit::registerAgentTool);
        return List.copyOf(toolkit.getToolNames());
    }

    /**
     * 模型懒建单例(OpenAI 兼容协议,官方 openai-java 实现;无 key 时 requireConfigured 已拦截)。
     * #18 连接热切:每轮经 DB 覆盖值(base-url/model)与装配期 yml 值比对生成指纹,
     * 连接参数变更 → 重建 OpenAIChatModel(旧实例无引用后自然 GC);api-key 不入指纹——
     * 凭证轮换(env/local.properties)需重启生效,DB 无 api-key 键(安全红线 docs/07 §7)
     */
    private Model model() {
        String effectiveBaseUrl = runtime.agentBaseUrl().orElse(baseUrl);
        String effectiveModel = runtime.agentModel().orElse(modelName);
        String fingerprint = effectiveBaseUrl + "|" + effectiveModel;
        if (model == null || !fingerprint.equals(modelFingerprint)) {
            synchronized (this) {
                if (model == null || !fingerprint.equals(modelFingerprint)) {
                    model = injectedModel != null ? injectedModel : OpenAIChatModel.builder()
                            .apiKey(apiKey)
                            .baseUrl(effectiveBaseUrl)
                            .modelName(effectiveModel)
                            .build();
                    modelFingerprint = fingerprint;
                }
            }
        }
        return model;
    }

    private void requireConfigured() {
        if (StrUtil.isBlank(apiKey)) {
            throw new BusinessException(
                    "AI 能力未配置:请设置环境变量 OPENAI_API_KEY(或 local.properties AI_API_KEY)后重启");
        }
    }
}
