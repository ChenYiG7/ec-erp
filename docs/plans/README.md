# docs/plans — P2/P3 待办实施计划书索引

> 本目录是 TODO.md「当前待办」P2 + P3 待办的**实施级计划书**,供执行模型(或人工)逐项投喂实施。
> 计划书只做规划与拍板建议,**本身不含业务代码**;实现仍须遵守根 CLAUDE.md 铁律与 docs/07(docs/09)。
> 生成于 2026-09-10,现状事实(类/表/端点)以当日代码为准;执行前若仓库已前进了,以代码为准。
>
> **2026-09-12 重整**:已实施的计划书移入 `archive/` 子目录归档(头部带实施标注,内容保留不删);
> 主体已落地但余量未清的计划书留在本目录(头部带实施标注 + 余量指引)。

## 使用方式

- 单项执行:把对应计划书全文 + 根 CLAUDE.md + docs/07(后端)/docs/09(前端) 一起投喂;计划书自包含现状事实,不需要再读 TODO.md。
- 逐项开工提示词:直接复制 [execution-prompts.md](execution-prompts.md) 对应条目(含 2026-09-10 预拍板、一功能一会话的上下文协议),无需自拼;**已完成项的提示词仅存档,不再投喂**。
- 每份计划书固定八节:元信息 / 背景与现状 / 方案设计 / 实施步骤 / 表结构草案 / 验收标准 / 红线提醒 / 交接边界。
- 「拍板点」= 必须人工确认才能继续的决策;执行模型遇到未拍板的拍板点应停下询问,不得自选。
- 复杂逻辑(AI/算法/复合事务)在执行时按铁律 1 处理:写 `TODO(新编号)` + 实现指引登记进 TODO.md,或由人工确认后实现。

## 计划书清单(未完成项)

| 计划书 | TODO 条目 | 性质 | 状态 |
|---|---|---|---|
| [order-review-split.md](order-review-split.md) | 订单域补课(审核/拆合单/内销) | 业务铺开 | 主体已落地(2026-09-11,#29),余量=环境依赖+拍板待定 |
| [warehouse-ops.md](warehouse-ops.md) | 仓内作业(盘点/调拨单/库位批次评估) | 业务铺开 | 主体已落地(2026-09-11,#30),余量=调拨在途等拍板项 |
| [payment-receipt.md](payment-receipt.md) | 收付款/回款 | 业务铺开 | 主体已落地(2026-09-11,#31),余量=存量库迁移+拍板挂起 |
| [first-mile-freight.md](first-mile-freight.md) | 头程运费分摊 | 业务深化 | 主体已落地(2026-09-11,#33),余量=存量库重放+四期不做项 |
| [fba-shipment.md](fba-shipment.md) | FBA Shipment | 平台对接 | 未开工(V1 内部数据面零凭证依赖,可先行) |
| [24-adapter-playbook.md](24-adapter-playbook.md) | #24 平台 adapter 批量扩展 | SOP 手册 | 未开工(卡 ISV 资质/AppKey) |
| [26-frontend-audit.md](26-frontend-audit.md) | #26 前端走查优化 | 流程 SOP | 未开工(功能稳定后统一走查) |

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

## 已完成归档(archive/)

> 实施完成即移入 `archive/`,头部保留实施标注(实施日期/devlog 链接/实施差异),内容不删除。

| 计划书 | TODO 条目 | 实施日期 | devlog |
|---|---|---|---|
| [archive/06-report-tools.md](archive/06-report-tools.md) | #6 Report tools(AI 第八类) | 2026-09-10 | [TODO6-Reporttools第八类落地](../devlog/TODO6-Reporttools第八类落地.md) |
| [archive/25-oss-storage.md](archive/25-oss-storage.md) | #25 OSS 对象存储(RustFS) | 2026-09-11 | [TODO25-OSS对象存储(RustFS)](../devlog/TODO25-OSS对象存储(RustFS).md) |
| [archive/28-deploy-docker.md](archive/28-deploy-docker.md) | #28 一键本地部署 + Docker | 2026-09-11 | —(工程底座,实施事实见 commit e00f24e 与 README 部署节) |
| [archive/sse-notify.md](archive/sse-notify.md) | SSE 实时推送通知 | 2026-09-12 | [2026-09-12-SSE实时推送落地](../devlog/2026-09-12-SSE实时推送落地.md) |
| [archive/27-rbac-enhance.md](archive/27-rbac-enhance.md) | #27 权限模块增强 | 2026-09-12 | [TODO27-权限增强(数据权限店铺轴收口)](../devlog/TODO27-权限增强(数据权限店铺轴收口).md) |
| [archive/19-profit-caliber.md](archive/19-profit-caliber.md) | #19 周期利润口径(含 #32 校差) | 2026-09-11 主 + 09-12 校差 | [TODO19-周期利润口径](../devlog/TODO19-周期利润口径.md)(#32 补篇同文件) |

## 建议执行顺序(2026-09-10 定版;✅=已完成归档)

| 序 | 计划书 | 状态 |
|---|---|---|
| 1 | 06-report-tools | ✅ 2026-09-10 |
| 2 | 25-oss-storage | ✅ 2026-09-11 |
| 3 | 28-deploy-docker | ✅ 2026-09-11 |
| 4 | order-review-split | ✅ 主体 2026-09-11(#29) |
| 5 | warehouse-ops | ✅ 主体 2026-09-11(#30) |
| 6 | payment-receipt | ✅ 主体 2026-09-11(#31) |
| 7 | 19-profit-caliber | ✅ 2026-09-11 主体 + 09-12 #32 校差落地(已归档) |
| 8 | first-mile-freight | ✅ 主体 2026-09-11(#33) |
| 9 | sse-notify | ✅ 2026-09-12 |
| 10 | 27-rbac-enhance | ✅ 2026-09-12(三会话收口) |
| 11 | 26-frontend-audit(分轮) | 未开工——全部功能落地稳定后统一走查 |
| 12 | 24-adapter-playbook | 未开工——卡 ISV 资质/AppKey,解卡后按手册逐平台(一平台一会话) |

- **条件插队**:fba-shipment(V1 内部数据面、零凭证依赖)——FBA 业务先行则随时插队开工。
- **P3 七项不进排期**:按各自触发条件拉动(execution-prompts.md 每条第 0 步自带核对,条件不满足不开工)。

## 维护纪律

- 某项计划实施完成:TODO.md 对应条目按既有纪律移除/收口,计划书**移入 `archive/` 子目录**并在头部追加一行
  「已于 YYYY-MM-DD 实施,devlog 见 xxx」;主体落地但余量未清的暂留本目录,头部标注实施事实 + 余量指引,
  余量清零后移入 archive/。
- 计划与代码现状漂移(执行中改了设计):改计划书同 commit 提交,保持「计划=拍板事实」。
- 纯 CRUD/同款批量任务不写 devlog 的既有纪律不变;本目录文档本身不替代 devlog。
