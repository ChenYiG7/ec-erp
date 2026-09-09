# 四期 agent/ V1 落地(AgentScope 2.0.2 ReActAgent 双角色)

- 日期: 2026-09-07
- 收尾提交: 见 feat(四期) 提交

## 拍板
- **双角色**:SUPPORT 客服(tools/ 四类全量只读)/ OPS 运营(白名单 queryInventory/searchProducts/findSkuByCode,
  订单/售后工具不开放)——角色分工先立起来,白名单收口 AgentRole 枚举。
- **工具零复制**:tools/ 只读 @Tool 四类(Spring AI ToolCallback 形态)经 SpringAiAgentToolBridge 桥接成
  AgentScope AgentTool(name/description/parameters=JSON schema Map/callAsync=参数序列化→callback.call→
  ToolResultBlock);逻辑单一来源仍在 tools/,聊天域与 Agent 域共用;**工具执行失败转错误结果回给模型**
  (ReAct 自行决策,不炸整个调用)。
- **模型**:AgentScope 内建 OpenAIChatModel(官方 openai-java 协议),连接复用 spring.ai.openai.*
  占位符递归解析(env/local.properties),与 chat 域单一来源;不写 Spring AI ChatModel 桥(勘察时以为要写,
  实测有内建实现,免)。
- **懒装配**:构造期不建客户端,首次调用 ensureAgents()(无 key 启动不炸 + requireConfigured 拦截,
  同 ErpChatService 口径)。
- **V1 单轮无状态不落库**:多轮记忆(InMemoryMemory)/流式 streamEvents/审计落库留四期推进。

## 实测坑(全部当场炸出当场修)
1. **双构造器 Spring 选不出来**:public(生产)+package-private(测试注入)并存 → "No default constructor
   found"启动炸;public 构造器必须标 @Autowired。
2. **@PathVariable 枚举大小写敏感**:/agents/support/chat 转 AgentRole 失败 400;AgentRole.fromPath
   (trim+toUpperCase)收口,未知角色友好报错。
3. **AgentTool 桥接取参**:ToolCallParam.getInput() 读独立 input 字段(与 toolUseBlock.getInput() 平行),
   测试构造只设后者会拿到 null。
4. **AgentScope ChatResponse.content(List<ContentBlock>)** 是列表不是单块;ToolResultBlock 取值是
   getOutput()(非 Msg 的 getContentBlocks(Class))。
5. curl 中文 body 在 Windows GBK shell 下必炸(JSON UTF-8 解析错)——联调用 ASCII 消息或改 PowerShell。

## 真调验证(DashScope qwen-plus)
- POST /api/ai/agents/support/chat:模型自发调 queryInventory(经桥接)→ 真实库存 → 文本结论(1.2s)。
- POST /api/ai/agents/ops/chat:中文回复库存口径盘面(在库/占用/在途/可用),运营 prompt 生效。
- 契约快照 83 路径(+/api/ai/agents/{role}/chat)。

## 未尽(四期余量)
- 多轮记忆/会话持久化、streamEvents 流式端点、Agent 审计落库、更多角色(选品/定价随工具面扩容)。

## 勘误(2026-09-07,V1.5 联调炸出)
- **上文"真调验证"结论不成立**:"模型自发调 queryInventory"实为幻觉——当时 AgentService 注入
  `List<ToolCallback>`,容器内无该类 Bean,注入恒为空列表,Toolkit 空、请求不带 tools 字段,工具从未真调;
  1.2s 返回两轮 ReAct 也跑不完,当时只看回复文本即下结论,被模型骗过(引用了不存在的 get_sku_inventory)。
- 修复见 TODO.md #6 V1.5 段:生产构造器改注入 tools/ 四类 + ToolCallbacks.from 本地转换,
  真调复验以 TOOL 审计行 + boundedElastic 线程 inventory SELECT + 真实分仓数据(149+300=449)为准。
- 方法论:工具/外呼类联调,验证只认审计行、DB SQL、副作用证据;回复文本一律视为不可信。
