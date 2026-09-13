# TODO 清单(由人工逐项实现)

> 约定:代码里所有 `TODO(编号)` 注释都对应本清单;简单 CRUD 已生成并编译通过;
> 复杂逻辑/AI 一律占位,理解 docs 后由你亲手补齐。
>
> 文档边界:docs/ 全部随仓库发布(2026-09-09 起)。
>
> **职责边界(2026-09-09 划分,2026-09-12/09-13 重整)**:本清单只含**未完成待办**(含已落地功能的余量)。
> 已完成功能的全景状态(规划 + 落地合一)见 `docs/02-功能模块规划.md` 各分域状态列;
> 实施过程与拍板细节见 `docs/devlog/`(根目录近期日志 + archive/ 38 篇归档);
> 已实施计划书归档见 `docs/plans/archive/`。
>
> **编号约定**:#1~#35 中已完成条目已从本清单移除(全文在 git 历史;2026-09-10 后落地的
> #6(Report tools/三工作流定时/markdown 渲染)/#11 发货回传异步化/#19/#21/#25/#27/#28/#29/#30/#31/
> #32/#33/#35/SSE 推送的拍板细节见 docs/devlog/ 对应篇与对应计划书(docs/plans/ 及 archive/),
> #26 走查轮次记录见 `docs/plans/26-frontend-audit.md` §七),编号**不复用、不重排**;
> 代码内残留 `TODO(#3/#6/#10/#12/#17/#34)` 注释为迭代槽位或历史标注,其未完成余量已拆入下方对应分节。
> ⚠️ 建库注意:建表脚本 `docs/sql/01_schema_init.sql` 为唯一正本(CREATE IF NOT EXISTS 幂等,可重复执行);
> 2026-09 历次修订的旧库手工 ALTER 已全部在开发库执行验证(2026-09-12 全量收口),新库直接重跑脚本即可;
> **已建库对齐统一跑 `python scripts/replay_schema_migration.py`**(幂等重放正本 + 历史加列判存补齐,重跑安全);
> 存量清单存档于 git 历史(TODO.md 重整前版本)。
>
> **优先级重整(2026-09-13 拍板,细节见 `docs/devlog/2026-09-13-优先级重整(国内平台先行).md`)**:
> ①首要任务改为**国内首个平台 adapter(抖店)**(原 P1 升 P0;原 P0 #3 Amazon 真凭证联调降为 P1 解卡即动,
> 一期闭环验收拆国内/Amazon 双线,先到先跑);②删除一批不再排期条目(智能定价/四期 AI 深化/#17 四小项/
> 移动端/预警模型扩容/原四期+ 全部等),明细与理由见 §一末「已删除条目」——对应计划书
> p3-ai-deepening/p3-goods-ai-extensions 已删除、p3-chat-ux/p3-ai-workflow-scheduling 已归档
> (2026-09-13 文档整理,全文在 git 历史可溯,预拍板摘要存于 docs/plans/execution-prompts.md)。

## 一、当前待办(按优先级;依赖与差距分析见 docs/10 §4/§5)

### P0 —— 首要任务:国内首个平台 adapter(抖店,2026-09-13 拍板)

- [x] **抖店 adapter 脱机开发**:**核心已落地并有测试**(erp-platform-sdk,2026-09-13 本会话):
      DouyinSigner(签名)/DouyinApiSupport(签名+GET+信封解包)/DouyinTokenClient(OAuth 换 token+刷新即轮换)/
      DouyinOrdersClient+/DouyinOrderTranslator(拉单)/DouyinProductClient+/DouyinListingTranslator(商品)/
      DouyinRefundsClient+/DouyinRefundTranslator(售后)/DouyinLogisticsClient(发货回传整单出库)/
      DouyinClient(facade, `erp.adapter.douyin.enabled` 默认关),假服务单测 141 通过(AIR)。
      仍待办(随 #17 / 真凭证联调):
      ⑥电子面单取号 fetchWaybill=DouyinClient 显式抛 UnsupportedOperationException(#17 面单账户/CLODOP 落地后接线);
      ⑧推送/Webhook 进单形态未建(推荐推送只做「触发即时拉」信号,数据面仍走统一 pull,playbook Phase 5 拍板点);
      结算 pullSettlements 走 SPI default 抛 Unsupported(联调拍板后按 getSettleBillDetailV3+下载接线);
      真凭证到位后 `--force` 校准一轮 schema/签名(docs/07 §8)。
- [ ] **#17 电子面单打印发货/面单账户管理**:随抖店 adapter 落地(fetchWaybill 取号 + CLODOP 批量打单,国内发货刚需)。
- [ ] **一期闭环验收(国内线)**:抖店真实店铺拉单 → 未绑定订单告警 → 绑定 → 审核 → 发货回传平台成功,
      全程无人工改库(依赖抖店 AppKey 真凭证;与 Amazon 线并行,先到先跑)。

### P1 —— 凭证解卡即动

- [ ] **#3 SP-API 真凭证联调(剩余)**(原 P0,2026-09-13 优先级重整降级——国内平台先行;
      Amazon Professional 卖家账号审核中,解卡即动):Seller Central 应用授权 + IAM 权限/role-arn 上线 +
      getOrders 冒烟;报表轮询同步阻塞(平台侧生成 15~60 分钟)真凭证实测时长后评估异步任务化;
      翻译 fixture 真凭证样本到位后 `--force` 校准一轮(docs/07 §8)。
      可预做部分已脱机收口(2026-09-12,devlog 见 `docs/devlog/2026-09-12-SP-API外部依赖项预做收口.md`):
      ①限流真值动态校准通道——四个 SpApi*Client 成功/失败响应都读 `x-amzn-RateLimit-Limit` 经
      RateLimitObserver 上报,PlatformRateGuard 按 platform+bucket 收紧 interval(多 endpoint 保守合并取
      最小速率,与配置取更保守者,校准刷新后该桶 setRate 失效重下发,非法值静默忽略;真值到位自动生效,
      guard 单测扩 5 例);②AftersaleRefundPullJob 售后拉单接线(仿 OrderPullJob 窗口游标 + SettlementPullJob
      总开关形态:erp.adapter.refund-pull.enabled 默认 false 关时零噪音,真凭证后开开关即拉;窗口左叠/首拉
      回溯/saveUnifiedRefund 唯一入口/会话店铺回填/连续失败告警,单测 7 例);③修复 PlatformGateway 缺
      pullSettlements 装饰缺陷(装饰器未覆写落接口 default 抛 UOE,结算开关打开也永远静默空转);
      ④结算编排此前已就绪(SettlementPullJob 默认关 + deriveOnParsed 同事务派生)零改动;
      ⑤发货回传异步化激活期余量已收口(2026-09-13,#11 条目;联调实测平台耗时拖长发货响应后
      拍板翻 sync-async/retry-enabled,零代码改动)。
- [ ] **一期闭环验收(Amazon 线)**:依赖 #3,口径同国内线(真实拉单 → 绑定 → 审核 → 发货回传,无人工改库)。
- [ ] **#20 广告报表**:ad_report_daily 草案激活,Amazon Ads API 报表抓取 → 广告费进利润(#19 装配管线扩容)→ ACOS 分析面(卡广告 API 真凭证,与 #3 同源)。
- [x] **#35 SP-API Fulfillment Inbound 客户端(fba-shipment V2)**(客户端 2026-09-12 脱机落地,联调随 #3 真凭证):
      PlatformClient SPI 三 default 方法(createInboundShipmentPlan/putTransportContent/pullInboundShipments,
      非 FBA 平台默认抛 UOE)+ 平台中立模型四 record(PlatformAddress/PlatformInboundPlanRequest/
      PlatformInboundShipment(行级 quantityPlanned/Shipped/Received 三语义)/PlatformTransportContent)+
      SpApiInboundClient(仿 SpApiOrdersClient 形态:SigV4 签名 POST/PUT body 参与载荷/ShipmentIdList 官方
      20 上限分批/NextToken 翻页防御/限流头观测上报/异常只透状态码)+ AmazonInboundTranslator
      (报文翻译零业务 if,fixture 为官方 schema 推导样例);PlatformGateway 装饰限流(计划生成/板箱回传走
      WRITE 桶,收货拉取走 PULL 桶);单测 15 例(client 9 + translator 6),全仓 mvn test 绿。
      余量(随 #3 联调):真报文脱敏 fixture `--force` 校准、putTransportContent partnered 与否拍板、
      领域编排接线(erp-fulfill 经 SPI 回填 platform_shipment_id/对账自动化,FbaReconciliationJob 转兜底)——
      联调校准清单在 `AmazonClient.java` 尾部槽位注释。领域数据面(fba_shipment 五表/状态机/OUT_SHIP 动账/
      diff 对账)已于 2026-09-12 落地 erp-fulfill。

### P2 —— 无外部依赖,余量清理与常开项

> 实施计划书(2026-09-10 拍板,自包含可直接投喂执行模型):FBA Shipment→`docs/plans/fba-shipment.md`、
> #24→`docs/plans/24-adapter-playbook.md`(兼 P0 抖店 adapter 实施手册)、#26→`docs/plans/26-frontend-audit.md`;
> 主体已落地、余量未清的 order-review-split/warehouse-ops/payment-receipt/first-mile-freight 四份同留本目录。
> **已实施计划书归档至 `docs/plans/archive/`**;索引与执行顺序见 `docs/plans/README.md`。
> 投喂方式:逐项现成执行提示词见 `docs/plans/execution-prompts.md`(已完成/已删除项的提示词仅存档不再投喂)。

- [ ] **#29 订单域补课余量**(主体 2026-09-11 落地:订单审核(风控判定+审核状态机)/拆单增强(发货单按仓/按物流多单)/
      内销订单手工录单;自动拆单建议 2026-09-12 落地(方案 A 纯规则零 LLM,详见 git 历史);
      计划书 `docs/plans/order-review-split.md`,devlog 见 `docs/devlog/TODO29-订单域补课(审核拆单内销录单).md`):
      余量仅剩拍板待定:全量待审 vs 风险命中才审(已按后者实现,随真实使用再评)。
- [ ] **#30 仓内作业余量**(主体+调拨在途 2026-09-11/09-12 落地,存量库重放收口;计划书 `docs/plans/warehouse-ops.md`,
      devlog 见 `docs/devlog/TODO30-仓内作业(盘点单调拨单域).md`):余量仅剩评估类:
      盘点冻结/库位批次=评估结论不做;库存质量维度(良品/残品分账)已移出排期(2026-09-13,见「已删除条目」);
      三方对账面随实际使用评估。
- [ ] **FBA Shipment 余量**(V1 内部数据面 2026-09-12 落地 erp-fulfill:五表/状态机 DRAFT→BOXED→SHIPPED→
      RECEIVING→CLOSED/SHIPPED 复合事务 OUT_SHIP 动账/diff 三态对账,店铺存在性校验 2026-09-13 接线;
      实施与拍板细节见 `docs/plans/fba-shipment.md` 头部标注;V2 SP-API Inbound 客户端部分见
      `docs/devlog/2026-09-12-SP-API外部依赖项预做收口.md`):余量:
      ①店铺轴数据权限未注入 FBA 查询(单据带 shop_id,多用户真实使用后按 #27① 口径补);
      ②SHIPPED 逆向(作废重开)流程随实际使用拍板;③FBA 库存全量跟踪(FBA 仓建 inventory 行)延后,
      触发点=FBA 库存差异成为真实痛点。
- [ ] **#31 收付款/回款余量**(主体 2026-09-11 落地 + 账期到期提醒 2026-09-12 落地 + 开发库收口;
      计划书 `docs/plans/payment-receipt.md`,devlog 见 `docs/devlog/TODO31-收付款回款.md`):余量:
      ①其他环境存量库统一跑 scripts/replay_schema_migration.py 幂等重放;
      ②拍板挂起:付款审批流(登记制起步)、已付冗余列(量级上来再评估);
      ③已知边界:未分摊预付/挂账款不进供应商应付视图,只在流水明细核对。
- [ ] **#33 头程运费分摊余量**(主体 2026-09-11 落地 + SKU 汇总接利润报表 2026-09-12 落地 + 开发库收口;
      计划书 `docs/plans/first-mile-freight.md`,devlog 见 `docs/devlog/TODO33-头程运费分摊.md`):余量:
      ①其他环境存量库统一跑 scripts/replay_schema_migration.py 幂等重放;
      ②SHIPPED 后逆向/作废重开流程随实际使用拍板;③SKU 编辑页尺寸录入随 FBA 装箱需求评估
      (箱唛打印 CLODOP、物流商 API 取价=四期明确不做)。
- [ ] **#24 平台横向铺开**(2026-09-13 重整:抖店已单列 P0;原「跨境第二平台(Shopee/Temu 择一)」并入本项):
      国内(淘宝/京东/拼多多/微信小店/快手/小红书)、跨境(eBay/Shopee/Lazada/TikTok/速卖通/Temu)。
      前置 = 各平台 ISV 资质/AppKey;防腐层 SPI 已就绪,单平台接入复用 `PlatformClient` 契约 + `Unified*` 模型,
      adapter 内只做报文翻译(铁律 3);按 `docs/plans/24-adapter-playbook.md` 逐平台执行(一平台一会话),
      国内平台随 adapter 落地解锁电子面单取号/签收回传/国内结算链路。
- [ ] **#26 前端页面优化与 Bug 修复(常开)**:在实际操作前端页面过程中发现 Bug 再逐项清理——
      不预先造 Bug 清单,而是**实际走查每个业务页面**(订单/库存/商品/采购/发货/售后/财务/报表/AI 对话/系统设置)发现问题后登记并修复。
      范围含:交互响应异常、数据加载竞态、表单校验遗漏、分页/筛选状态丢失、样式适配(暗色主题/响应式)、
      动态菜单权限边界、SSE 连接断线重连、加载态/空态/错误态覆盖。每轮走查产出 issue 列表,按影响面排序逐项修。
      **已执行七轮(2026-09-12~09-13 全收口,拍板与实施细节见 `docs/plans/26-frontend-audit.md` §七
      已执行轮次记录)**:round1 39 页全量走查 4 Bug / round2 手写页+公共件竞态与错误路由 9 处 /
      round3 暗色专项+报表可视化(顺带挖出 goodsTrend 构造映射缺列 500)/ round4 表单校验+分页筛选状态
      4 处 / round5 权限边界静态审计零缺陷 / round6 资源泄漏+弹窗残留 5 处 / round7 KeepAlive 激活刷新
      (ProTable onActivated 中心化 + useTable 请求序号守卫,顺带修 FBA/头程 pageRows record 缺列 500
      + 两 validate 脚本扩静态断言)。
      待走查专项:安全(v-html 使用面/XSS 防护/token 存储与登出清理)、性能(路由懒加载核验/重复请求合并)。
      门禁:oxlint 0 错 / oxfmt 归一 / vue-tsc 通过 / pnpm build 通过(docs/09)。

### P3 —— 拍板挂起(最小保留,2026-09-13 重整后)

> 实施计划书(触发条件式——条件不满足不开工):#6 同买家规则→`docs/plans/p3-alert-monitor-expansion.md`、
> #11 代发/海外仓→`docs/plans/p3-fulfill-extensions.md`、#12 超退上限→`docs/plans/p3-aftersale-refund-cap.md`;
> p3-ai-deepening/p3-goods-ai-extensions 整份已删除(计划书文件已删)、p3-chat-ux 余量已删除(计划书已归档)
> (2026-09-13 文档整理,git 历史可溯)。

- [ ] #6:「同买家批量下单」规则(契约无 buyer 字段,PII 不出契约,随 V2 契约扩容;方案 A/B 拍板见 p3-alert-monitor-expansion.md)。
- [ ] #11:FBA/OVERSEAS 发货单类型的供应商代发与海外仓发货流程(待业务确认后细化,p3-fulfill-extensions.md 四问)。
- [ ] #12:超退上限按发货量收紧精确口径(当前按订单行数量放宽防误拦,随发货域数据完善后评估;Service javadoc 槽位)。
- [ ] #27 按需补:详情按 ID 直取/写动作端点不在本期注入面(计划书 Query record 过滤口径)、perm_key 按钮级后端鉴权
      通道不做(展示层 v-auth + admin 方法注解已覆盖当前风险面)——多用户真实使用后按需补。

### 已删除条目(2026-09-13 优先级重整移除——不再排期,重启任一项须先重新拍板)

> 删除原则:卡数据无解 / 纯对标追赶 / 单管理员内部系统用不上。编号不复用;删除条目全文在 git 历史;
> 对应计划书 p3-ai-deepening/p3-goods-ai-extensions 文件已删除、p3-chat-ux 已归档
> (2026-09-13 文档整理;预拍板摘要存于 docs/plans/execution-prompts.md)。

- **智能定价**(原 P1,卡竞品价数据,原拍板即「不建议现在动」)及其数据前置**竞品监控**。
- **#17 四小项**:供应商比价、文案平台风格适配 V2、listing 改写回填、选品权重自调优(原 p3-goods-ai-extensions 计划,已删除)。
- **#6 四期 AI 深化**:RAG 接入 agent 域、意图识别/多语言客服、agent 更多角色(原 p3-ai-deepening 计划,已删除);
  ai_suggestion payloadJson 结构化渲染、代码块复制按钮(原 p3-chat-ux §2.3/§2.1 余量,计划书已归档)。
- **移动端(小程序/H5)与微信业绩推送**(2026-09-10 已拍板暂不考虑,本次彻底移出清单)。
- **预警模型扩容**(向领星 29+ 模型形态靠拢:Listing 变动/关键词排名/库龄/店铺绩效——纯对标追赶不追,
  现有六规则够用;p3-alert-monitor-expansion.md §2.3 部分删除,同买家规则保留)。
- **原四期+ 全部**:多商户激活、OpenAPI 对外授权、XXL-Job 可视化调度、组合装/组装拆分、1688 线上采购对接、
  委外加工、物流商 API 对接(4PX/云途)、运费比价、BI 自定义驾驶舱、多维成本分摊(MSKU/ASIN/Listing/店铺/站点/负责人)、
  人员业绩核算、库存时序预测(Prophet/ARIMA)、库存质量维度(良品/残品分账)、库存自动同步到销售链接、
  买家邮件/评论管理、AI 评论分析(注:部门/操作审计/数据权限已随 #27 于 2026-09-12 落地,不在删除范围)。
- **跨境第二平台 adapter 单列项**(Shopee/Temu 择一)——改归类并入 #24 平台横向铺开(非删除)。

## 二、红线与坑(写代码时必读)

### SQL 兼容性红线(mapper XML)
- 开发库 = MySQL 9.7.2 LTS,自定义 SQL 一律 9.7.2 原生形态:
  - VALUES 行 upsert 统一 8.0.19+ 行别名 `AS new`(四处 XML);**行别名定义后 ODKU 内列引用必须全限定**(`new.` 前缀=本条插入值、表名前缀=冲突行现值,未限定一律 1052 歧义);弃用 VALUES(col)。
  - INSERT...SELECT 场景行别名不支持(1064):直传用源表别名引用,聚合值用派生表别名引用。
  - "每组取排序键最大一行"用 ROW_NUMBER() 窗口函数。
- **单测 mock Mapper 永远测不出 XML 语法错误,自定义 SQL 必须真库验证**(scripts/validate_*.py 家族);
  **教训**:SalesSnapshotJob 曾连日语法错误静默空跑(单销量表零行,补货/滞销规则拿空数据)——改 SQL/换引擎当天必须重跑验证脚本。

### MyBatis-Plus 3.5.9+ 的坑(已规避)
- Boot 4 必须用 `mybatis-plus-spring-boot4-starter` + 额外引 `mybatis-plus-extension`(分页插件)+ `mybatis-plus-jsqlparser`;
- **`IService`/`ServiceImpl` 已移除**:统一 Mapper 做通用 CRUD、Service 只装业务逻辑(plain @Service);
- MP 3.5.17 BaseMapper insert/updateById 有 Collection 重载,mockito any() 需类型化 any(Entity.class);
- LambdaQueryWrapper `.in()`/`.set()` 急切解析列元数据,纯单测环境炸——eq 逐条或 BaseMapper 内建方法规避(docs/07 §10);
- 若启动报 AgentScope/SAA 自动装配错误,临时注释 erp-ai 对应 starter。

### 遗留槽位提示
- 代码内 `TODO(编号)` 注释当前集中在:erp-ai(各 Controller/Graph 节点迭代槽位)、AftersaleOrderService(同步 upsert 剩余槽位)、AmazonClient(报表轮询异步化)——均为迭代预留非阻塞未完成,余量见上方对应分节。
- **TODO(#34) SSE 多实例广播**:NotificationSseRegistry 进程内 Map,单进程部署够用;扩多实例时改
  Redis pub/sub 广播(频道建议 `erp:notify:sse`,各实例订阅后转发本实例注册表),只换 broadcast
  传播介质,register/unregister/broadcast 口径不变;届时一并拍板踢旧策略与上限
  (SSE 实施拍板见 `docs/devlog/archive/2026-09-12-SSE实时推送落地.md`)。
- **TODO(#35) SP-API Fulfillment Inbound 客户端(fba-shipment V2)**:客户端已脱机落地(2026-09-12,见 P1 #35 条目),
  剩余 = 联调随 #3 真凭证(真报文 fixture 校准/partnered 拍板/领域编排接线),校准清单在
  `erp-platform-sdk` `AmazonClient.java` 尾部;领域数据面(fba_shipment 五表/状态机/OUT_SHIP 动账/
  diff 对账)已于 2026-09-12 落地 erp-fulfill(见 docs/plans/fba-shipment.md)。
