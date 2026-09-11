# docs/plans — P2/P3 待办实施计划书索引

> 本目录是 TODO.md「当前待办」P2 13 项 + P3 7 组待办的**实施级计划书**,供执行模型(或人工)逐项投喂实施。
> 计划书只做规划与拍板建议,**本身不含业务代码**;实现仍须遵守根 CLAUDE.md 铁律与 docs/07(docs/09)。
> 生成于 2026-09-10,现状事实(类/表/端点)以当日代码为准;执行前若仓库已前进了,以代码为准。

## 使用方式

- 单项执行:把对应计划书全文 + 根 CLAUDE.md + docs/07(后端)/docs/09(前端) 一起投喂;计划书自包含现状事实,不需要再读 TODO.md。
- 逐项开工提示词:直接复制 [execution-prompts.md](execution-prompts.md) 对应条目(含 2026-09-10 预拍板、一功能一会话的上下文协议),无需自拼。
- 每份计划书固定八节:元信息 / 背景与现状 / 方案设计 / 实施步骤 / 表结构草案 / 验收标准 / 红线提醒 / 交接边界。
- 「拍板点」= 必须人工确认才能继续的决策;执行模型遇到未拍板的拍板点应停下询问,不得自选。
- 复杂逻辑(AI/算法/复合事务)在执行时按铁律 1 处理:写 `TODO(新编号)` + 实现指引登记进 TODO.md,或由人工确认后实现。

## 计划书清单

| 计划书 | TODO 条目 | 性质 | 前置 |
|---|---|---|---|
| [25-oss-storage.md](25-oss-storage.md) | #25 OSS 对象存储(RustFS) | 工程底座 | 无 |
| [28-deploy-docker.md](28-deploy-docker.md) | #28 一键本地部署 + Docker | 工程底座 | 无(吸收 #25 RustFS 编排) |
| [19-profit-caliber.md](19-profit-caliber.md) | #19 周期利润口径 | 业务深化 | 无(结算拉取接线卡 #3 凭证) |
| [first-mile-freight.md](first-mile-freight.md) | 头程运费分摊 | 业务深化 | 无(product_sku 重量体积在本计划内补) |
| [order-review-split.md](order-review-split.md) | 订单域补课(审核/拆合单/内销) | 业务铺开 | 无 |
| [warehouse-ops.md](warehouse-ops.md) | 仓内作业(盘点/调拨单/库位批次评估) | 业务铺开 | 无 |
| [payment-receipt.md](payment-receipt.md) | 收付款/回款 | 业务铺开 | 无(回款自动派生可选衔接结算域) |
| [fba-shipment.md](fba-shipment.md) | FBA Shipment | 平台对接 | 无(V1 内部数据面,SP-API 接入卡 #3) |
| [sse-notify.md](sse-notify.md) | SSE 实时推送通知 | 体验增强 | 无 |
| [06-report-tools.md](06-report-tools.md) | #6 Report tools(AI 第八类) | AI 增强 | 无(ACOS 面卡 #20) |
| [24-adapter-playbook.md](24-adapter-playbook.md) | #24 平台 adapter 批量扩展 | SOP 手册 | P1 首个国内/跨境第二 adapter(资质) |
| [26-frontend-audit.md](26-frontend-audit.md) | #26 前端走查优化 | 流程 SOP | SSE 断线重连项建议待 sse-notify 落地后走查 |
| [27-rbac-enhance.md](27-rbac-enhance.md) | #27 权限模块增强 | 纵深增强 | 无(①②内部有先后) |

## P3 计划书(触发条件式,2026-09-10)

> P3 条目均为「拍板挂起」:**每份计划书头部带触发条件,条件不满足不开工**;开工路径/验收标准已预设,触发后可直接投喂。
> **移动端(小程序/H5)与微信业绩推送:用户拍板 2026-09-10 暂不考虑,不做规划**;将来重启时先补产品定位拍板。

| 计划书 | TODO 条目 | 触发条件摘要 |
|---|---|---|
| [p3-alert-monitor-expansion.md](p3-alert-monitor-expansion.md) | #6 HIGH 异常推通知 + 同买家规则 + 预警模型扩容 | 异常量级数据积累 / 刷单案例或契约 V2 窗口 / 新规则数据源就绪 |
| [p3-ai-workflow-scheduling.md](p3-ai-workflow-scheduling.md) | #6 采购/文案/选品三工作流定时接线 | 采纳率评估达标(第 0 步是统计 ai_suggestion 采纳率) |
| [p3-chat-ux.md](p3-chat-ux.md) | #6 聊天页 markdown 渲染/停止生成/建议结构化渲染 | 阅读痛点真实出现 + 引库拍板;payload schema 收敛 |
| [p3-ai-deepening.md](p3-ai-deepening.md) | #6 RAG×agent/意图识别/多语言/更多角色(四期路线图) | 检索价值验证/客服场景定义/语种需求/岗位场景;前置拍板=客服用户定位 |
| [p3-fulfill-extensions.md](p3-fulfill-extensions.md) | #11 供应商代发/海外仓流程 + 回传异步化 | 真实代发/海外仓订单流;真凭证实测回传耗时超标 |
| [p3-aftersale-refund-cap.md](p3-aftersale-refund-cap.md) | #12 超退上限按发货量收紧 | 实证数据出现现有口径放行过的超退样本 |
| [p3-goods-ai-extensions.md](p3-goods-ai-extensions.md) | #17 供应商比价/文案风格 V2/listing 回填/选品权重自调优 | 各自独立:报价数据面/双平台使用/adapter 上架 API/上架回流数据 |

## 建议执行顺序(2026-09-10 定版,单线程逐项)

> 排序原则:①投喂协议先用最小项试跑;②底座先行(#28 吸收 #25 的 RustFS 编排);③业务线按「真实订单进来前的运营刚需 → 钱视线」排;④前端走查放功能稳定后;⑤卡资质的垫底。

| 序 | 计划书 | 规模 | 先后理由 |
|---|---|---|---|
| 1 | 06-report-tools | S | 最小、零依赖、机械性最高——当投喂协议的试金石;且 AI 能查报表后,后续各项验收查数都方便 |
| 2 | 25-oss-storage | S | 底座第一件;#28 要吸收它的 RustFS 编排 |
| 3 | 28-deploy-docker | M | 底座收口,之后所有功能在可复现环境上验证 |
| 4 | order-review-split | M | 真实订单进来前补审核闸口/内销录入,直接服务 P0 闭环验收的「审核」环节 |
| 5 | warehouse-ops | M-L | 实仓运营刚需(盘点/调拨);与 4 相互独立,可按急迫度对调 |
| 6 | payment-receipt | M | 采购付款/平台回款,资金流可视 |
| 7 | 19-profit-caliber | M-L | 周期利润口径(三口径第二层);结算拉取接线仍卡 #3 凭证,费率表+预估模型本体可先建 |
| 8 | first-mile-freight | M | 利润第三层,紧跟 7 做(分摊/资本化拍板已预拍板,省重新热身) |
| 9 | sse-notify | S-M | 体验线;落地后 #26 的 SSE 断线重连走查项才有对象 |
| 10 | 27-rbac-enhance(3 会话:部门→审计→数据权限) | L | 多人使用前完成即可,单人阶段不阻塞任何事 |
| 11 | 26-frontend-audit(分轮) | M | 全部功能落地稳定后统一走查——早做会被后续新页面作废 |
| 12 | 24-adapter-playbook | — | 卡 ISV 资质/AppKey,解卡后按手册逐平台(一平台一会话) |

- **条件插队**:fba-shipment(V1 内部数据面、零凭证依赖)——FBA 业务先行则插到 5 前后;不插队则放 9 之后任意空档。
- **P3 七项不进排期**:按各自触发条件拉动(execution-prompts.md 每条第 0 步自带核对,条件不满足不开工)。

## 维护纪律

- 某项计划实施完成:TODO.md 对应条目按既有纪律移除/收口,计划书头部追加一行「已于 YYYY-MM-DD 实施,devlog 见 xxx」后**保留归档**,不删除。
- 计划与代码现状漂移(执行中改了设计):改计划书同 commit 提交,保持「计划=拍板事实」。
- 纯 CRUD/同款批量任务不写 devlog 的既有纪律不变;本目录文档本身不替代 devlog。
