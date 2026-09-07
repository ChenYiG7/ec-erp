package com.own.erp.ai.agent;

import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.request.query.AiChatSessionQuery;
import com.own.erp.ai.response.AiChatMessageResponse;
import com.own.erp.ai.service.AiChatMessageService;
import com.own.erp.ai.service.AiChatSessionService;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : AgentService 单测(四期 agent/ V1.5,AIR:注入假 Model + mock 会话/消息服务,不出网):
 *     多轮历史重放(USER/AI 行转 Msg 进模型入参)、USER/AI 行落库时序、无 key/空消息拦截、角色工具白名单
 */
class AgentServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 9L;
    private static final String ANSWER = "可用库存 300 件";

    private ErpAiProperties props;
    private AiRuntimeProperties runtime;
    private AiChatSessionService sessionService;
    private AiChatMessageService messageService;
    private CurrentUserApi currentUserApi;
    private final List<List<Msg>> capturedModelInputs = new ArrayList<>();
    private AgentService service;

    @BeforeEach
    void setUp() {
            props = new ErpAiProperties();
        runtime = com.own.erp.ai.graph.RuntimePropsStub.of(props);
        sessionService = mock(AiChatSessionService.class);
        messageService = mock(AiChatMessageService.class);
        currentUserApi = mock(CurrentUserApi.class);
        when(currentUserApi.currentUserId()).thenReturn(USER_ID);
        when(sessionService.getOwned(SESSION_ID, USER_ID, AiConsts.SESSION_SOURCE_AGENT)).thenReturn(
                com.own.erp.ai.entity.AiChatSession.builder().id(SESSION_ID).userId(USER_ID).build());

        ToolCallback orderCallback = toolCallbackNamed("listOrders");
        ToolCallback inventoryCallback = toolCallbackNamed("queryInventory");
        // 假模型:记录模型入参,吐纯文本应答(无工具调用 → ReAct 单轮收敛)
        Model fakeModel = new Model() {
            @Override
            public Flux<ChatResponse> stream(List<Msg> msgs, List<ToolSchema> tools,
                                             GenerateOptions options) {
                capturedModelInputs.add(msgs);
                return Flux.just(ChatResponse.builder()
                        .content(List.of(TextBlock.builder().text(ANSWER).build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "fake";
            }
        };
        service = new AgentService(props, runtime, fakeModel, List.of(orderCallback, inventoryCallback),
                sessionService, messageService, currentUserApi, "test-key");
    }

    private ToolCallback toolCallbackNamed(String name) {
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name(name).description(name).inputSchema("{\"type\":\"object\"}").build());
        return callback;
    }

    private AiChatMessageResponse row(String role, String content) {
        return AiChatMessageResponse.builder()
                .sessionId(SESSION_ID).role(role).content(content).build();
    }

    @Test
    void chatSyncReplaysHistoryAndPersistsRows() {
        // 历史:USER→AI→本轮 USER(listBySessionId 升序,含本轮刚落的 USER 行)
        when(messageService.listBySessionId(SESSION_ID)).thenReturn(List.of(
                row("USER", "昨天的上下文"), row("AI", "之前的回答"), row("USER", "新问题")));

        String reply = service.chatSync(AgentRole.SUPPORT, SESSION_ID, "新问题");

        assertEquals(ANSWER, reply);
        // 重放:首条为 Agent 预置 SYSTEM(sysPrompt),随后三条历史按序转 Msg
        assertEquals(4, capturedModelInputs.get(0).size());
        assertEquals(MsgRole.SYSTEM, capturedModelInputs.get(0).get(0).getRole());
        assertEquals(MsgRole.USER, capturedModelInputs.get(0).get(1).getRole());
        assertEquals(MsgRole.ASSISTANT, capturedModelInputs.get(0).get(2).getRole());
        assertEquals(MsgRole.USER, capturedModelInputs.get(0).get(3).getRole());
        // 落库时序:先 USER 行,后 AI 行(聚合全文)
        verify(messageService).append(eq(SESSION_ID), eq("USER"), eq("新问题"), isNull(), isNull(), isNull());
        verify(messageService).append(eq(SESSION_ID), eq("AI"), eq(ANSWER), isNull(), isNull(), isNull());
    }

    @Test
    void replayTruncatesToConfiguredHistoryMax() {
        // 5 行历史,上限 2 → 只重放最近 2 行,加 Agent 预置 SYSTEM 共 3 条(防长会话 token 膨胀)
        props.getAgent().setHistoryMaxMessages(2);
        when(messageService.listBySessionId(SESSION_ID)).thenReturn(List.of(
                row("USER", "旧问题1"), row("AI", "旧回答1"), row("USER", "旧问题2"),
                row("AI", "旧回答2"), row("USER", "新问题")));

        service.chatSync(AgentRole.SUPPORT, SESSION_ID, "新问题");

        List<Msg> inputs = capturedModelInputs.get(0);
        assertEquals(3, inputs.size());
        // 尾部截断:最近两行 = ASSISTANT(旧回答2)→ USER(本轮提问恒在);头部截断会是 USER→AI 反序
        assertEquals(MsgRole.ASSISTANT, inputs.get(1).getRole());
        assertEquals(MsgRole.USER, inputs.get(2).getRole());
    }

    @Test
    void chatStreamEmitsTextDeltas() {
        when(messageService.listBySessionId(SESSION_ID)).thenReturn(List.of(row("USER", "hi")));

        List<String> deltas = service.chatStream(AgentRole.SUPPORT, SESSION_ID, "hi")
                .collectList().block();

        assertEquals(List.of(ANSWER), deltas);
    }

    @Test
    void modelFailureEmitsErrorFrameAndSkipsAiRow() {
        when(messageService.listBySessionId(SESSION_ID)).thenReturn(List.of(row("USER", "hi")));
        Model brokenModel = new Model() {
            @Override
            public Flux<ChatResponse> stream(List<Msg> msgs, List<ToolSchema> tools,
                                             GenerateOptions options) {
                return Flux.error(new RuntimeException("upstream down"));
            }

            @Override
            public String getModelName() {
                return "fake";
            }
        };
        AgentService broken = new AgentService(props, runtime, brokenModel, List.of(),
                sessionService, messageService, currentUserApi, "test-key");

        List<String> frames = broken.chatStream(AgentRole.SUPPORT, SESSION_ID, "hi")
                .collectList().block();

        assertEquals(1, frames.size());
        assertTrue(frames.get(0).contains("Agent 调用失败"));
        // 失败轮不落 AI 行
        verify(messageService, never()).append(eq(SESSION_ID), eq("AI"), any(), any(), any(), any());
    }

    @Test
    void blankApiKeyBlocksAtCallTimeNotStartup() {
        AgentService noKey = new AgentService(props, runtime, fakeModel(), List.of(),
                sessionService, messageService, currentUserApi, "");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> noKey.chatSync(AgentRole.SUPPORT, SESSION_ID, "hi"));
        assertTrue(ex.getMessage().contains("AI 能力未配置"));
    }

    @Test
    void blankMessageRejected() {
        assertTrue(assertThrows(BusinessException.class,
                        () -> service.chatSync(AgentRole.SUPPORT, SESSION_ID, "  "))
                .getMessage().contains("消息内容不能为空"));
    }

    @Test
    void opsRoleFiltersToolWhiteList() {
        // OPS 白名单只含 queryInventory:listOrders 不开放
        assertEquals(2, service.listBridgedToolNames(AgentRole.SUPPORT).size());
        assertEquals(1, service.listBridgedToolNames(AgentRole.OPS).size());
        assertTrue(service.listBridgedToolNames(AgentRole.OPS).contains("queryInventory"));
    }

    private Model fakeModel() {
        return new Model() {
            @Override
            public Flux<ChatResponse> stream(List<Msg> msgs, List<ToolSchema> tools,
                                             GenerateOptions options) {
                return Flux.just(ChatResponse.builder()
                        .content(List.of(TextBlock.builder().text(ANSWER).build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "fake";
            }
        };
    }
}
