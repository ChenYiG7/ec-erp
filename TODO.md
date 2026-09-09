# TODO 清单(由人工逐项实现)

> 约定:代码里所有 `TODO(编号)` 注释都对应本清单;简单 CRUD 已生成并编译通过;
> 复杂逻辑/AI 一律占位,理解 docs 后由你亲手补齐。
>
> 文档边界:docs/ 全部随仓库发布(2026-09-09 起)。
>
> **职责边界(2026-09-09 再划分)**:本清单只含**未完成待办**。
> 已完成功能的全景状态(规划 + 落地合一)见 `docs/02-功能模块规划.md` 各分域状态列;
> 实施过程与拍板细节见 `docs/devlog/`(根目录近期日志 + archive/ 31 篇归档)。
>
> **编号约定**:#1~#23 为历史编号,已完成条目已从本清单移除(全文在 git 历史),
> 编号**不复用、不重排**;代码内残留 `TODO(#3/#6/#10/#12/#17)` 注释为迭代槽位或历史标注,
> 其未完成余量已拆入下方对应分节。
> ⚠️ 建库注意:建表脚本 `docs/sql/01_schema_init.sql` 为唯一正本(CREATE IF NOT EXISTS 幂等,可重复执行);
> 2026-09 历次修订的旧库手工 ALTER 已全部在开发库执行验证,新库直接重跑脚本即可,存量清单存档于 git 历史(TODO.md 重整前版本)。

## 一、当前待办(按优先级;依赖与差距分析见 docs/10 §4/§5)

### P0 —— 阻塞链头(全系统唯一外部依赖)

- [ ] **#3 SP-API 真凭证联调(剩余)**:Seller Central 应用授权 + IAM 权限/role-arn 上线 + getOrders 冒烟;
      SP-API 限流真值按响应头 `x-amzn-RateLimit-Limit` 校准;报表轮询同步阻塞(平台侧生成 15~60 分钟)
      真凭证实测时长后评估异步任务化;AftersaleRefundPullJob 售后拉单接线(避免对假报文产生失败噪音);
      结算报告编排接线(手动触发或低频 Job,无凭证时 Job 空转);翻译 fixture 真凭证样本到位后 `--force` 校准一轮(docs/07 §8)。
- [ ] **一期闭环验收**:真实店铺拉单 → 未绑定订单告警 → 绑定 → 审核 → 发货回传平台成功,全程无人工改库(依赖上条)。

### P1 —— 凭证/资质解卡即动

- [ ] **#20 广告报表**:ad_report_daily 草案激活,Amazon Ads API 报表抓取 → 广告费进利润(#19 装配管线扩容)→ ACOS 分析面(卡广告 API 真凭证,与 #3 同源)。
- [ ] **#17 智能定价**:竞品价数据就位后开题;建议进 ai_suggestion 人工确认后执行(红线不变)。拍板记录:卡数据,不建议现在动。
- [ ] **国内首个平台 adapter**(抖店或淘宝):解锁电子面单取号(CLODOP 批量打单)/签收回传/国内结算链路;
      前置 = ISV 资质申请(周期 1-4 周,应立即启动);**AppKey 资质是第一依赖**(docs/06)。
- [ ] **跨境第二平台 adapter**(Shopee/Temu 择一):验证防腐层 SPI 复制接入成本(单平台 1-2 周,四期口径)。
- [ ] **#17 电子面单打印发货/面单账户管理**:国内平台发货刚需,随国内 adapter 落地(fetchWaybill 取号 + CLODOP 打单)。

### P2 —— 无外部依赖,数据就绪即可开工

- [ ] **#19 周期利润口径**:结算单口径与订单口径差值校准(三口径第二层,docs/02 §14);预估费用模型
      (平台费率表先行估算、结算回后校差,wimoor profitcfg/referralfee 费率表族思路);多币种折算完善。
- [ ] **订单域补课**(docs/02 原规划未编号):订单审核(风控备注/地址校验)、拆合单(按仓/按物流)、内销订单录入。
- [ ] **仓内作业**:盘点单、调拨单域(InventoryService.transfer() 原语已备)、库位/批次评估(二期规划)。
- [ ] **FBA Shipment**:发货计划生成/装箱信息/与平台对账(docs/02 三期规划,无硬阻塞可先行)。
- [ ] **收付款/回款**:采购付款登记、平台回款记录、资金流追踪(docs/02 二期规划)。
- [ ] **头程运费分摊**(利润口径第三层组件,docs/02 三期规划)。
- [ ] **SSE 浏览器实时推送通知**(qihang 对标;前端现有 60s 轮询基础,erp-web 可承接)。
- [ ] **#6 Report tools**:报表域取数契约化后 AI 工具第八类(报表数据面已就绪,ACOS 面卡 #20)。
- [ ] **#24 其他电商平台 adapter 扩展**:在 P1 首个国内 adapter + 跨境第二平台 adapter 落地后,批量接入剩余平台——
      国内(淘宝/京东/拼多多/微信小店/快手/小红书)、跨境(eBay/Shopee/Lazada/TikTok/速卖通/Temu)。
      前置 = 各平台 ISV 资质/AppKey;防腐层 SPI 已就绪,单平台接入复用 `PlatformClient` 契约 + `Unified*` 模型,
      adapter 内只做报文翻译(铁律 3);国内平台随 adapter 落地解锁电子面单取号/签收回传/国内结算链路。
- [ ] **#25 OSS 对象存储(RustFS)**:引入 [RustFS](https://rustfs.com)(Rust 实现 S3 兼容对象存储)替代本地文件存储,
      统一管理 Excel 导出文件(erp-report POI)、RAG 知识库文档原文、截图等静态资源。
      后端通过 S3 协议对接(RustFS 兼容 S3 API),配置面走 sys_config 热更(Endpoint/AccessKey/SecretKey/Bucket,
      SecretKey 走 ValueType.SECRET 掩码回显同 #14 SMTP 授权码口径);erp-common 提供统一 `OssService` 收口上传/下载/预签名 URL,
      各业务模块走契约不直连 SDK。本地开发用 RustFS 单节点 Docker 起,#28 Docker 部署时一并编排。
- [ ] **#26 前端页面优化与 Bug 修复**:在实际操作前端页面过程中发现 Bug 再逐项清理——
      不预先造 Bug 清单,而是**实际走查每个业务页面**(订单/库存/商品/采购/发货/售后/财务/报表/AI 对话/系统设置)发现问题后登记并修复。
      范围含:交互响应异常、数据加载竞态、表单校验遗漏、分页/筛选状态丢失、样式适配(暗色主题/响应式)、
      动态菜单权限边界、SSE 连接断线重连、加载态/空态/错误态覆盖。每轮走查产出 issue 列表,按影响面排序逐项修。
      门禁:oxlint 0 错 / oxfmt 归一 / vue-tsc 通过 / pnpm build 通过(docs/09)。
- [ ] **#27 权限模块增强**:当前 RBAC 已落地(用户/角色/菜单/字典 + JWT 按角色鉴权),本项为**纵深权限**增强——
      ①**数据权限**(按人/按部门/按店铺过滤数据,MyBatis-Plus 拦截器或 SQL 条件注入,`CurrentUserApi` 已有用户上下文);
      ②**操作审计**(用户操作级日志,当前仅 AI 工具调用审计,扩到关键业务操作——订单状态推进/库存动账/发货回传/财务勾稽/采购审核);
      ③**部门组织架构**(部门树 + 用户归属,为数据权限和人员业绩核算铺路);
      ④**字段级权限**(敏感字段脱敏已部分落地,评估是否需按角色控制可见性)。
      前置:①依赖部门表新增 + 拦截器;②依赖审计表 + AOP;③依赖部门树 DDL;④随实际需求拍板粒度。
- [ ] **#28 工程化部署(一键本地 + Docker)**:
      **一键本地部署**:`scripts/` 下提供启动脚本(建库 → 配置检查 → 构建后端 → 启动 → 前端 dev/prod 一条龙),
      降低新开发者上手成本;校验 ERP_JWT_SECRET / ERP_TOKEN_KEY 两个必填环境变量,缺失即友好报错。
      **Docker 部署**:`Dockerfile`(多阶段构建:Maven 编译 → JRE 21 运行镜像)+ `docker-compose.yml` 编排
      erp-api / MySQL / Redis / RustFS(#25)四服务,环境变量注入密钥,volume 挂载数据持久化;
      前端独立 Nginx 镜像或嵌入 erp-api 静态资源(拍板)。目标:新环境 `docker compose up -d` 即跑通。

### P3 —— 拍板挂起(需真实使用数据/业务确认后拍板)

- [ ] #6:HIGH 级异常推通知(随实际告警量评估);「同买家批量下单」规则(契约无 buyer 字段,PII 不出契约,随 V2 契约扩容)。
- [ ] #6:采购/文案/选品三工作流定时接线(人工决策节奏,随实际使用拍板;补货/异常两工作流已接定时)。
- [ ] #6:前端聊天页 markdown 渲染/停止生成(引库需拍板);ai_suggestion payloadJson 结构化渲染(随前端评估)。
- [ ] #6:RAG 接入 agent 域(当前只接 chat)、意图识别/多语言客服、agent 更多角色(四期 AI 深化)。
- [ ] #11:FBA/OVERSEAS 发货单类型的供应商代发与海外仓发货流程(待业务确认后细化)。
- [ ] #11:发货回传异步化(当前 AFTER_COMMIT 同步执行,真凭证联调实测耗时后再评估;需独立 executor,禁复用 pullScheduler)。
- [ ] #12:超退上限按发货量收紧精确口径(当前按订单行数量放宽防误拦,随发货域数据完善后评估;Service javadoc 槽位)。
- [ ] #17:供应商比价(随多供应商数据积累评估)、文案平台风格适配 V2、listing 改写回填(随 adapter 上架类接口扩容)、选品权重自调优(四期评估)。
- [ ] 移动端(小程序/H5)与微信业绩推送(对标差距新增项,大工程,随产品定位拍板)。
- [ ] 预警模型扩容(当前 5 规则,向领星 29+ 模型形态靠拢:Listing 变动/关键词排名/库龄/店铺绩效;部分依赖新拉取类型 MONITOR_*)。

### 四期+ —— 长期规划(维持 docs/02 分期口径)

- [ ] 多商户激活(merchant_id 已预留,#触发点见 docs/01 演进预案)。
- [ ] OpenAPI 对外授权(appKey/appSecret,docs/02 三期规划)、XXL-Job 可视化调度(任务多了再上,docs/05)。
- [ ] 组合装/组装拆分(评估)、1688 线上采购对接(四期评估,依赖资质;wimoor 镜像表族过度设计不学)、
      委外加工、物流商 API 对接(4PX/云途)、运费比价。
- [ ] BI 自定义驾驶舱、多维成本分摊(MSKU/ASIN/Listing/店铺/站点/负责人)、人员业绩核算
      (轻量做法:product_sku 加 owner/developer 两列)。
- [ ] 库存时序预测(Prophet/ARIMA 类,当前 V2 为统计口径)、库存质量维度(良品/残品分账)、库存自动同步到销售链接。
- [ ] 部门、操作审计(用户操作级,当前仅 AI 工具调用审计)、数据权限按人、买家邮件/评论管理、AI 评论分析。

## 二、红线与坑(写代码时必读)

### SQL 兼容性红线(mapper XML)
- 开发库 = MySQL 9.7.2 LTS,自定义 SQL 一律 9.7.2 原生形态:
  - VALUES 行 upsert 统一 8.0.19+ 行别名 `AS new`(四处 XML);**行别名定义后 ODKU 内列引用必须全限定**(`new.` 前缀=本条插入值、表名前缀=冲突行现值,未限定一律 1052 歧义);弃用 VALUES(col)。
  - INSERT...SELECT 场景行别名不支持(1064):直传用源表别名引用,聚合值用派生表别名引用。
  - "每组取排序键最大一行"用 ROW_NUMBER() 窗口函数。
- **单测 mock Mapper 永远测不出 XML 语法错误,自定义 SQL 必须真库验证**(scripts/validate_*.py 家族);
  **教训**:SalesSnapshotJob 曾连日语法错误静默空跑(单销量表零行,补货/滞销规则拿空数据)——改 SQL/换引擎当天必须重跑验证脚本。
- MySQL 5.7 无递归 CTE:日期轴类需求不进 SQL,Service 层逐日对齐(#22 先例)。

### MyBatis-Plus 3.5.9+ 的坑(已规避)
- Boot 4 必须用 `mybatis-plus-spring-boot4-starter` + 额外引 `mybatis-plus-extension`(分页插件)+ `mybatis-plus-jsqlparser`;
- **`IService`/`ServiceImpl` 已移除**:统一 Mapper 做通用 CRUD、Service 只装业务逻辑(plain @Service);
- MP 3.5.17 BaseMapper insert/updateById 有 Collection 重载,mockito any() 需类型化 any(Entity.class);
- LambdaQueryWrapper `.in()`/`.set()` 急切解析列元数据,纯单测环境炸——eq 逐条或 BaseMapper 内建方法规避(docs/07 §10);
- 若启动报 AgentScope/SAA 自动装配错误,临时注释 erp-ai 对应 starter。

### 遗留槽位提示
- 代码内 `TODO(编号)` 注释当前集中在:erp-ai(各 Controller/Graph 节点迭代槽位)、AftersaleOrderService(同步 upsert 剩余槽位)、AmazonClient(报表轮询异步化)——均为迭代预留非阻塞未完成,余量见上方对应分节。
