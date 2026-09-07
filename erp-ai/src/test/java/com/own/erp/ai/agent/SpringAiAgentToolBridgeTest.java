package com.own.erp.ai.agent;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolCallParam;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : SpringAiAgentToolBridge 单测(四期 agent/,AIR:mock Spring AI ToolCallback):
 *     名称/描述/参数 schema 透传解析、调用委托(入参 Map→JSON→call)、工具失败转错误结果不炸调用
 */
class SpringAiAgentToolBridgeTest {

    private ToolCallback callback(String result) {
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name("queryInventory")
                .description("查询库存")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"skuId\":{\"type\":\"integer\"}}}")
                .build());
        if (result != null) {
            when(callback.call("{\"skuId\":1}")).thenReturn(result);
        } else {
            when(callback.call(org.mockito.ArgumentMatchers.anyString()))
                    .thenThrow(new RuntimeException("db down"));
        }
        return callback;
    }

    private ToolCallParam param() {
        return ToolCallParam.builder()
                .toolUseBlock(ToolUseBlock.builder()
                        .id("call-1").name("queryInventory")
                        .input(Map.of("skuId", 1))
                        .build())
                .input(Map.of("skuId", 1))
                .build();
    }

    @Test
    void bridgesNameDescriptionAndParsedSchema() {
        SpringAiAgentToolBridge bridge = new SpringAiAgentToolBridge(callback("ok"));

        assertEquals("queryInventory", bridge.getName());
        assertEquals("查询库存", bridge.getDescription());
        assertEquals("object", bridge.getParameters().get("type"));
    }

    @Test
    void callAsyncDelegatesAndWrapsResult() {
        ToolResultBlock result = new SpringAiAgentToolBridge(callback("库存 300")).callAsync(param()).block();

        assertEquals("call-1", result.getId());
        assertEquals("queryInventory", result.getName());
        assertEquals("库存 300", resultText(result));
    }

    @Test
    void toolFailureBecomesErrorResultNotBlowUp() {
        // 工具失败作为结果回给模型(ReAct 自行决策),不阻断整个 Agent 调用
        ToolResultBlock result = new SpringAiAgentToolBridge(callback(null)).callAsync(param()).block();

        assertTrue(resultText(result).contains("工具执行失败"));
    }

    private String resultText(ToolResultBlock result) {
        return ((TextBlock) result.getOutput().get(0)).getText();
    }
}
