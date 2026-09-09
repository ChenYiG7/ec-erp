# 四期 agent/ 开工勘察(AgentScope 2.0.2 GA 实测)

- 日期: 2026-09-07(仅勘察,未写码;结论写进 TODO.md #6 agent/ 条目,下会话可直接开工)

## API 勘察实录(javap 反编译 ~/.m2 jar)
- `agentscope-spring-boot-starter-2.0.2`:仅 AgentscopeAutoConfiguration + 三 Properties
  (ModelProperties/AgentProperties/AgentscopeProperties)。装配 `agentscopeReActAgent(Model, Memory, Toolkit, props)`
  **单例**——多 Agent 必须自建 Bean(注意默认 Bean 冲突,或干脆不用 starter 自动装配)。
- `io.agentscope.core.model.Model`(接口,5 方法 4 default):
  `Flux<ChatResponse> stream(List<Msg>, List<ToolSchema>, GenerateOptions)` + getModelName。
  → **Spring AI ChatModel 桥接 adapter 一个类可通**,复用 spring.ai.openai.*(base-url/key/model),
  不重复建键;ChatResponse$Builder 组装面待细看(内容块/工具调用对齐)。
- `ReActAgent`:call(String)→Mono<Msg>(同步)/ streamEvents(Msg)→Flux<AgentEvent>(流式)/
  interrupt/状态(stateForCall)/AutoCloseable;ReAct 循环、结构化输出(STRUCTURED_OUTPUT_TOOL_NAME)、
  确认流(CONFIRM_SINK_KEY)框架自带。
- Memory:InMemoryMemory / StateBackedMemory / LongTermMemory(Hook 型);V1 用 InMemoryMemory 即可。
- Toolkit:ReflectiveFunctionTool 反射注册 + ToolGroup/ToolEmitter——Spring AI @Tool(现有 tools/ 四类)
  是另一形态,复用要么桥接要么反射重标(开工拍板,倾向"运营 Agent 直接反射注册新只读方法,少一层桥")。
- AGUI adapter(agui/ 包)是前端事件流协议适配,四期前端若做 Agent 对话页可评估,不急。

## V1 建议切法(总/mid 量级)
1. Model 桥接 adapter(Spring AI → agentscope)单测用假 Model 桩(罐头 ChatResponse)。
2. agent/ 两 Agent:客服(店铺/订单/库存/商品/售后查询话术)/ 运营(库存+建议只读+预警解读),
   各自 sys prompt 收口配置类(docs/07 §9)。
3. 端点:POST /api/ai/agents/{role}/chat 同步先行(登录即可,同 chat 口径);流式接口等 SSE 校准收尾。
4. 审计:复用 ai_chat_message role 词表加 AGENT?或独立 ai_agent_message——开工拍板。
5. 真调依赖:AI_API_KEY 真值(SSE 联调同款卡点,local.properties 现为 14 字符占位值)。
