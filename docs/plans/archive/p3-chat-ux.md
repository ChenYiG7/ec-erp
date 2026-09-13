# P3 前端 AI 页面体验增强实施计划书(markdown 渲染/停止生成/建议结构化渲染)

> **落地状态(2026-09-13)**:§2.1 markdown 渲染已落地(拍板 marked 18.0.12 + dompurify 3.4.15,`-E` 钉版;
> `src/components/AiMarkdown/index.vue` 统一入口,流式纯文本/完成态渲染分态,DOMPurify USE_PROFILES html +
> afterSanitizeAttributes 链接新窗 noopener 钩子;MessageList 接入 chat/agent 两页,真机渲染+注入净化实测通过,
> marked 懒加载 chunk ~13KB gzip);§2.2 停止生成已于 2026-09-12 落地;§2.3 payloadJson 结构化渲染仍未触发
> (schema 未收敛前不开工);代码块复制按钮(§2.1 可后置项)未做。
> **余量已删除(2026-09-13 优先级重整)**:§2.3 payloadJson 结构化渲染与 §2.1 代码块复制按钮移出排期,
> 本计划书不再投喂;明细见 TODO.md「已删除条目」。
> **已归档(2026-09-13 文档整理)**:主体落地(§2.1/§2.2)、余量已删除,移入 archive/ 存档。

| 元信息 | 值 |
|---|---|
| TODO 条目 | #6:前端聊天页 markdown 渲染/停止生成(引库需拍板);ai_suggestion payloadJson 结构化渲染(随前端评估) |
| 性质 | **触发条件式计划**(引第三方库是拍板项,渲染体验痛点真实出现即触发) |
| 触发条件 | ①markdown:AI 回复中代码块/表格/列表频繁且阅读受损(用几次即知);②停止生成:长回复流式输出时用户确需中断(等不了自然结束);③结构化渲染:ai_suggestion 实际 payload 形态稳定后(payloadJson 各工作流 schema 已收敛) |
| 前置依赖 | 无;全部为 erp-web 前端改动 |
| 明确不做 | 后端消息格式改造;会话历史的后端分页优化(独立小项);agent 页面复用本方案时只做接入不改设计 |

## 一、背景与现状(2026-09-10 代码事实)

- **聊天链路**:`ErpChatController` SSE 流式(`POST /api/ai/chat/sessions/{id}/chat`,Flux<String>,裸 `data:` 帧+[DONE]);前端 `src/utils/sse.ts` `postSse(url, body, onChunk)`(fetch 手解,chat/agent 两域在用);聊天页 `src/views/ai/chat/`、agent 页 `src/views/ai/agent/`。
- **现状缺口**:前端把 AI 回复按纯文本拼接渲染(markdown 源码直接可见);无中断手段(fetch 未暴露 AbortController 使用);消息历史逐条整段渲染。
- **ai_suggestion**:`status` 三态 cas 确认闭环(adopt/ignore)已有端点;`payload_json` 为 JSON 列,各工作流(补货/采购/文案/选品/异常)payload 结构各自为政,前端目前按原始 JSON 或字段平铺展示(以页面实际为准,执行时核对 `src/views/ai/ai-suggestion/`)。
- docs/09 纪律:门禁四件;依赖新增需拍板(本项目前端依赖定版管理,docs/09 §1);禁拼 URL/禁 import axios(§2);页面 ≤300 行(§9)。

## 二、方案设计

### 2.1 markdown 渲染(拍板点①:引库选择)

- 候选(均为轻量、无重依赖):
  - **`marked` + `DOMPurify`(推荐)**:marked 极小解析快,DOMPurify 消 XSS(AI 输出是不可信输入,**净化是硬要求不是选项**);
  - `markdown-it` + 插件:生态全但体积大些;
  - 不引库手写解析:**禁**(XSS 与边界 case 风险,省下的体积不值得)。
- 渲染策略:**流式期间**按纯文本(或节流重渲染,500ms 一帧)增量追加;**消息完成后**一次性 markdown 渲染(避免逐 token 重解析卡顿);代码块加复制按钮(体验加分项,可后置)。
- 样式适配暗色主题(docs/09;代码块配色走 UnoCSS/EP 变量,不硬编码色值)。

### 2.2 停止生成

- `postSse` 扩展支持传入 `AbortSignal`(fetch 原生 abort,流断开即停渲染);聊天/agent 页加"停止"按钮(生成中可见)。
- **后端无需配合**:SSE 断连后服务端 Flux 取消语义自带(chat 先例);但需核对后端对 abort 的处理(连接断开日志噪音可接受即可,不算 bug)。
- 中断后的消息状态:已收到的部分保留落库显示(以现状落库时机为准——若后端整段完成后才落库,中断即丢,这是**拍板点②**:接受"中断=丢弃"或后端改增量落库;建议 V1 接受丢弃,标注"已停止"占位)。

### 2.3 ai_suggestion payloadJson 结构化渲染

- 前置:先**收敛 schema 文档**(每类 suggestion 的 payloadJson 字段表——从后端各工作流 Persist 节点核对,产出一张字段表进 docs/09 或本目录,前端按契约渲染);schema 未收敛前不开工(触发条件的一部分)。
- 渲染形态按类型分派:补货/采购=表格(建议量/当前量/建议值)+一键采纳;文案=文本块+复制+采纳;选品=评分卡;异常=风险条目列表。
- 采纳/忽略按钮复用既有 adopt/ignore 端点(cas 失败提示刷新,双错误形态收口 base.ts 不重复处理)。

## 三、开工路径

1. 拍板引库 → `pnpm add marked dompurify @types/...` → 封装 `src/components/AiMarkdown/index.vue`(统一入口,聊天/agent/建议三处复用;组件 ≤300 行)。
2. 聊天页接 AiMarkdown(完成后渲染态)+ 流式节流;agent 页同步接入。
3. postSse 加 AbortSignal 参数(向后兼容,不传不变)+ 停止按钮 + "已停止"占位态。
4. ai_suggestion:schema 字表态 → 按类型分派渲染组件 → 采纳闭环回归。
5. 门禁四件 + 暗色主题逐页过 + agent/chat 两页走查。

## 四、验收标准

- markdown:代码块/表格/列表正确渲染;`<script>` 等注入样本被 DOMPurify 净化(安全用例);暗色主题无刺眼白底。
- 流式:输出过程不卡顿(节流生效);停止后请求真取消(Network 面断开),页面无悬挂 loading。
- 建议:五类 suggestion 各按分派形态渲染;采纳/忽略与后端 cas 一致;payload 缺字段不白屏(容错渲染)。
- 门禁四件全绿;不新增禁用依赖;openapi 快照无变化则不提交。

## 五、红线提醒

- **XSS 净化是硬红线**:AI 输出按不可信输入处理,markdown 渲染必经 DOMPurify(或等价),禁 v-html 裸插。
- 金额字段(建议里的金额)string 直存直显禁 parseFloat(docs/09 §6)。
- 引库版本钉死写 package.json,禁浮动 tag;评估包体积(marked+dompurify 合计 <100KB gzip 可接受)。
- postSse 是 chat/agent 共用通道:改动向后兼容,禁破坏既有调用方。

## 六、交接边界

1. 引库选择拍板(推荐 marked+DOMPurify)。
2. 中断语义(中断=丢弃 vs 后端增量落库)拍板——涉及后端改动,前端会话只登记不改后端。
3. payloadJson schema 收敛由谁产出(建议后端会话核对 Persist 节点后给字段表,前端照表渲染)。
