# P3 四期 AI 深化路线图实施计划书(RAG×agent/意图识别/多语言/更多角色)

| 元信息 | 值 |
|---|---|
| TODO 条目 | #6:RAG 接入 agent 域(当前只接 chat)、意图识别/多语言客服、agent 更多角色(四期 AI 深化) |
| 性质 | **路线图式计划**(四期深化:本文沉淀方向、依赖与开工路径,防止到时从零摸索) |
| 触发条件 | ①RAG×agent:RAG 在 chat 侧被验证有检索价值(命中率/被引用率可评估)且 agent 使用频率起来;②意图识别:AI 客服场景被真实定义(谁是用户、答什么、错答代价);③多语言:跨境客服语种需求真实出现;④更多角色:出现 SUPPORT/OPS 覆盖不了的真实岗位场景 |
| 前置依赖 | RAG 库内容积累到有效规模(当前向量库内容=手工上传文档,规模有限);kb 文档原文存储(#25 OSS 接入后可扩 PDF/DOCX) |
| 明确不做 | 自研向量库/换 embedding 方案(SimpleVectorStore 到瓶颈再评估,触发点=文档量级);语音/IM 渠道接入;AI 主动外呼类(超出 ERP 射程) |

## 一、背景与现状(2026-09-10 代码事实)

- **RAG**(erp-ai `kb/`):`KbController.upload`(仅 .txt/.md/.markdown ≤1MB)→ `KbIngestService.ingest` 分块 → chunk 文本存 `ai_kb_chunk.content`;`KbVectorIndex` 封装 SimpleVectorStore(JSON 文件持久化,`erp.ai.kb.index-path`,构造加载/坏文件空索引起步/重建换引用/内部锁串行);**检索注入 chat 双通道**(agent 域未接)。
- **agent**(erp-ai `agent/`):AgentScope `ReActAgent` 双角色 SUPPORT/OPS;`SpringAiAgentToolBridge` 桥接零复制(工具白名单与 chat 同源扩容);会话持久化 `ai_chat_session/message`(source 列隔离 CHAT/AGENT);历史重放截断。
- **工具审计**:AuditingToolCallback 全工具调用落 TOOL 行。
- **依赖面**:embedding 模型走 `AI_EMBEDDING_MODEL` 配置;LLM 走 OPENAI_* 配置。

## 二、方向卡片(每个=独立小立项,触发后各自走 add-domain 式细化)

### 2.1 RAG 接入 agent 域

- 接入点二选一(开工时拍板):
  - **方案 A(推荐)**:检索做成**只读 @Tool**(KbTools:queryKb(question)→topK 片段),经 SpringAiAgentToolBridge 进白名单——agent 按需自取,ReAct 范式天然匹配,零侵入 chat 侧现状;
  - 方案 B:agent 会话前置固定注入检索上下文(同 chat 双通道做法)——简单但每轮都检索,token 浪费且答非所问时无法自纠。
- A 方案要点:检索结果带 source 引用(可追溯);片数字上限;工具审计自动覆盖。
- 前置验收:chat 侧检索质量评估(抽 20 个真实问题人工判命中率),<50% 先修 kb 内容质量再谈接入 agent。

### 2.2 意图识别

- 定位:**客服场景的分流器**(问售前/售后/物流/操作 how-to → 路由到不同工具集或话术),不是独立功能。
- 实现形态预设:小模型/规则词表先分流(便宜),低置信度回落全工具集;意图词表走 sys_config 热更(#18 口径)。
- **先决拍板(触发后第一件事)**:客服的"用户"是谁(内部客服人员 vs 终端买家?当前 agent 面向内部运营,若开放买家则触达渠道/安全边界/数据权限全部重估——**这是本卡片最大的未知数,可能推翻形态**)。

### 2.3 多语言客服

- 依赖 2.2 先立(无意图分流的多语言=直接全量多语言 prompt,token 成本失控)。
- 形态预设:LLM 原生多语能力为主(不引翻译链路);语言检测+按语言话术模板(kb 文档多语言版本管理是真实工作量——**kb 文档表需加 language 维度**,随触发时 add-table)。
- 跨境场景真实语种需求(如 Shopee 本地语)出现才开工,不预做。

### 2.4 agent 更多角色

- 现状双角色 SUPPORT/OPS 的角色=工具白名单+system prompt 组合(SpringAiAgentToolBridge 同源)。
- 候选角色(按依赖排):**FINANCE**(接 ReportQueryApi/#6 Report tools 落地后的报表数据面+利润契约)、**PURCHASE**(采购建议工作流确认闭环的对话化)——都依赖对应数据契约先就绪。
- 纪律:新角色=白名单子集+独立 system prompt,复用现有桥,**禁复制 agent 编排骨架**;会话 source 列加角色词表。

## 三、开工路径(触发后通用)

1. 先跑对应触发条件的评估动作(检索命中率抽样/客服场景定义会/语种清单/岗位访谈)。
2. 按 2.x 卡片拍板方案 → 走对应流程(工具类=Report tools 计划同款"契约→@Tool→白名单";表变更=add-table)。
3. 验证铁则:TOOL 审计行证据 + DB SQL 对照,回复文本不作为验证依据(用户 memory 铁则)。
4. 每卡片独立提交、独立 devlog(拍板级)。

## 四、验收标准(按卡片)

- RAG×agent:agent 会话中检索工具被真实调用(TOOL 行)且答案引用 source;无关问题不触发检索(ReAct 自主判断)。
- 意图识别:分流准确率抽检达标(阈值开工时定);低置信回落路径可用。
- 多语言:非中文问题按语种应答且话术模板命中;kb 多语言文档可分别检索。
- 新角色:工具面=白名单子集(越界工具不可达);会话 source 隔离正确。

## 五、红线提醒

- **AI 工具只读、写操作人工确认**(铁律 7):新角色的工具面同样全部只读;客服场景若涉"代客操作"(改订单/退款)一律落到 ai_suggestion 人工确认,禁直连写。
- 买家对话场景(若 2.2 拍板开放):PII 不出契约、对话留痕、无 PII 进 LLM 之外第三方——先过安全评审再动工。
- token 成本:检索片数/意图降级/多语言 prompt 全部设上限键,无上令禁上线。
- 向量数据可重建纪律不变(不入正本,SimpleVectorStore JSON 只是索引)。

## 六、交接边界

1. 2.2 客服用户定位(内部 vs 买家)是整个 AI 深化线的前置拍板,触发后第一件事。
2. RAG 接入形态 A/B 拍板。
3. 各卡片触发的先后由业务真实需求拉动,不做排期承诺。
4. 本路线图随四期启动时逐卡片展开为独立计划书(届时在本目录加 p4- 文件)。
