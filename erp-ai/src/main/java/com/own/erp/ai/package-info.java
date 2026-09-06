/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : AI 能力层(#6 三期开工 2026-09-06:第 1 层 AI 地基已落地,余量见 TODO.md #6 未勾项)。
 *
 *     <pre>
 *     分层(自下而上,均构建在 Spring AI 之上,不互斥):
 *     ┌ 第3层 AgentScope Java 2.0(2.0.2 GA)    多Agent:智能客服/自动运营 —— 四期
 *     ├ 第2层 Spring AI Alibaba 2.0(2.0.0-M1.1) graph-core 工作流:异常监控/补货建议 —— 三期(未开工)
 *     └ 第1层 Spring AI 2.0(2.0.1 GA)          ChatClient + @Tool 自然语言查询 —— ✅ 2026-09-06 落地
 *     </pre>
 *
 *     分层落位(#6 已勾选部分):
 *     <ul>
 *       <li>chat/    —— ErpChatService(chat 同步 + chatStream 流式)+ ErpChatController(SSE/会话读写侧)</li>
 *       <li>tools/   —— 只读 @Tool 首批四类(Order/Inventory/Goods/Aftersale),取数走 erp-contract 查询契约;
 *                        余量(Shop/Purchase/Delivery/Report/Ads 等)待随对应查询契约扩容,ACOS 无数据不开</li>
 *       <li>config/  —— ErpAiProperties 提示词集中配置(yml erp.ai.system-prompt 可覆盖)</li>
 *       <li>constant/ —— AiConsts 状态/类型/角色词表;entity/mapper/service/controller —— ai_* 三表读写收口</li>
 *       <li>graph/   —— SAA Graph Core 工作流(节点=LLM/工具节点):未开工,下一会话</li>
 *       <li>agent/   —— AgentScope ReActAgent 多 Agent 协作:四期</li>
 *     </ul>
 *     红线:AI 永远只读优先;任何写操作必须人工确认;Prompt 与工具白名单走配置不走硬编码。
 */
package com.own.erp.ai;
