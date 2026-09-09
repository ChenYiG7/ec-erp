# TODO(#6) AI地基:三期开工(版本GA定版+查询契约+chat双通道+建议闭环)

- 日期: 2026-09-06
- 备注: 本任务不产生提交(用户手动管 git);提交拆分由人工按域执行

## 拍板
- 版本定版:Spring AI 2.0.1(GA)/ AgentScope 2.0.2(GA),repo1.maven.org metadata 核实后由上一提交落根 pom;SAA 2.0 线无 GA 保持 2.0.0-M1.1(1.1.2.3 GA 对齐 Boot3,不可降级),graph/ 开工前再核实。
- 查询契约四件收口 erp-contract:计划原文三个,执行补 AftersaleQueryApi(tools 含 AftersaleTools,erp-ai 禁横向依赖 erp-aftersale);分页归一收口在各 filter record 自身(page()/size():int 组件 @Builder 不设=0 → 归一默认 1/20 钳 100),Impl 一行透传。计划笔误"Controller 放 erp-api"不采纳——docs/07 §2.2 controller 只在各模块自己包内,ErpChatController/AiSuggestionController 均在 erp-ai。
- 会话两表(ai_chat_session/ai_chat_message)不生成 controller,ErpChatController 作会话读写唯一入口;归属校验收口 AiChatSessionService.getOwned,不存在与非本人统一报"会话不存在"(防探测他人会话 ID 存在性);AiChatSessionQuery.userId 为服务端强制覆盖字段。

## 改动
- erp-contract +5:QueryPage(查询契约通用分页)+ OrderQueryApi/InventoryQueryApi/GoodsQueryApi/AftersaleQueryApi(filter/行视图全 record 嵌套,不引 MP 类型);erp-api contract/impl +4(委托各域 Service.page/getById,Response→契约 record 显式逐字段)+ 单测四件;erp-goods ProductService.getSkuByCode(sku_code 唯一键精确查,GoodsQueryApi 落点)。
- erp-ai 主体:pom 补 erp-contract/web/validation/MP 三件套;ai_suggestion/ai_chat_session/ai_chat_message 三表八件套(readOnly=true,2.2 生成);AiConsts;AiSuggestionQuery 补过滤 + Mapper casAdopt/casIgnore(0→1/0→2 同 UPDATE 回填 confirmed_by/at)+ Service adopt/ignore/save + Controller 两动作端点;AiChatSessionService(create/pageMine/getOwned/renameIfDefault)/AiChatMessageService(append 词表校验/listBySessionId);tools/ 四类只读 @Tool;ErpAiProperties(system prompt 集中,yml erp.ai.system-prompt 可覆盖);ErpChatService(chat/chatStream,ChatClient.Builder 注入,api-key 判空友好报错);ErpChatController(SSE + chat-sync + 会话读侧)。
- 测试:erp-ai 24 用例(域测试骨架改造 + ErpChatServiceTest 深桩 mock ChatClient 全程不出网 + testgen-ai.txt 产 AiSuggestionStateMachineTest 守卫四类);13 模块全量 mvn test BUILD SUCCESS。
- 文档:TODO.md #6 勾选改写 + #17 落位表开工注记;CLAUDE.md 版本表/铁律2/erp-contract 行/erp-ai 行;docs/07 §2.2 图补 erp-ai 消费方与四个查询契约 Impl;docs/03 §7 定稿已在表变更时完成。

## 坑
- Git Bash(MSYS)调 mvn 传 `-Dpath=/api/...` 被路径转换污染成 `D:/Java/Git/api/...`(生成的 @RequestMapping 即错);`MSYS_NO_PATHCONV=1` 又破坏 mvn 自身(ClassNotFoundException classworlds Launcher)。规避:codegen 尽量不传以 / 开头的 path(会话表 readOnly 不带 controller,直接省略 -Dpath),已污染文件手改一处。
- Mockito 对包装类型 mock 默认返回原始默认值(CurrentUserApi.currentUserId() 默认 **0L 而非 null**,Primitives.defaultValue 语义)——testgen spec stub 实参写 `null` 与实参 0L 不匹配,cas"命中"分支误报脱靶;spec 改 `stub=ID, 0L` 后 -Dforce 重跑通过。
- Spring AI 2.0.1:Usage.getPromptTokens 返回 **Integer**(单测 thenReturn(100L) 编译错);`-pl erp-ai` 单模块编译走本地仓库旧 erp-contract 报"找不到 CurrentUserApi"——新契约未 install,联编必须带 `-am`。

## 未尽
- TOOL 中间行不落库(Spring AI 工具执行循环不透出中间消息,ai_chat_message.role 词表已预留):TODO(#6) 槽,升级后评估 ToolCallback 钩子补 TOOL 行;流式链路 token 用量取不到置 NULL。
- graph/ SAA 补货建议工作流(下一会话)、库存预警规则引擎(#17"三期最先")、前端聊天页/AI 建议页(另会话,后端端点已备)、tools 余量(Shop/Purchase/Delivery/Report 随对应查询契约扩容,ACOS 无数据不开)。
- SSE 真模型冒烟未做(本地无 AI_API_KEY,仅验证友好报错路径);#3 真凭证联调时一并验证 deepseek-chat 全链路。
