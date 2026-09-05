/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : AI 能力层(全部留 TODO 由人工实现,依赖已就位、版本见根 pom)。
 *
 *     <pre>
 *     分层(自下而上,均构建在 Spring AI 之上,不互斥):
 *     ┌ 第3层 AgentScope Java 2.0(2.0.0-RC5)  多Agent:智能客服/自动运营 —— 四期
 *     ├ 第2层 Spring AI Alibaba 2.0(2.0.0-M1.1) graph-core 工作流:异常监控/补货建议 —— 三期
 *     └ 第1层 Spring AI 2.0(2.0.0-M5)          ChatClient + @Tool 自然语言查询 —— 三期
 *     </pre>
 *
 *     规划(详细方案见 docs/05-技术选型与AI方案.md):
 *     <ul>
 *       <li>chat/    —— ChatClient 装配、SSE 流式接口(占位:ErpChatService)</li>
 *       <li>tools/   —— 只读 @Tool 集:查订单/查库存/查销量/查广告ACOS,供自然语言查询调用</li>
 *       <li>graph/   —— SAA Graph Core 工作流:拉单异常诊断、滞销补货建议(节点=LLM/工具节点)</li>
 *       <li>agent/   —— AgentScope ReActAgent:多Agent协作(客服Agent + 运营Agent + 审批人)</li>
 *     </ul>
 *     红线:AI 永远只读优先;任何写操作必须人工确认;Prompt 与工具白名单走配置不走硬编码。
 */
package com.own.erp.ai;
