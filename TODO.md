# TODO 清单(由人工逐项实现)

> 约定:代码里所有 `TODO(编号)` 注释都对应本清单;简单 CRUD 已生成并编译通过;
> 复杂逻辑/AI 一律占位,理解 docs 后由你亲手补齐。
>
> 文档边界:docs/ 全部随仓库发布(2026-09-09 起)。
>
> **职责边界(2026-09-09 划分,2026-09-12 重整)**:本清单只含**未完成待办**(含已落地功能的余量)。
> 已完成功能的全景状态(规划 + 落地合一)见 `docs/02-功能模块规划.md` 各分域状态列;
> 实施过程与拍板细节见 `docs/devlog/`(根目录近期日志 + archive/ 31 篇归档);
> 已实施计划书归档见 `docs/plans/archive/`。
>
> **编号约定**:#1~#34 中已完成条目已从本清单移除(全文在 git 历史;2026-09-10 后落地的
> #6 Report tools/#19/#25/#27/#28/#29/#30/#31/#33/SSE 推送的拍板细节见 docs/devlog/ 对应篇、
> 计划书见 docs/plans/archive/),编号**不复用、不重排**;代码内残留 `TODO(#3/#6/#10/#12/#17/#34)`
> 注释为迭代槽位或历史标注,其未完成余量已拆入下方对应分节。
> ⚠️ 建库注意:建表脚本 `docs/sql/01_schema_init.sql` 为唯一正本(CREATE IF NOT EXISTS 幂等,可重复执行);
> 2026-09 历次修订的旧库手工 ALTER 已全部在开发库执行验证(2026-09-12 全量收口),新库直接重跑脚本即可;
> **已建库对齐统一跑 `python scripts/replay_schema_migration.py`**(幂等重放正本 + 历史加列判存补齐,重跑安全);
> 存量清单存档于 git 历史(TODO.md 重整前版本)。

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
- [ ] **#35 SP-API Fulfillment Inbound 客户端(fba-shipment V2)**:createInboundShipmentPlan(平台发货计划生成)/
      putTransportContent(板箱信息回传)/getShipments(平台收货对账自动化,替代人工登记口径,
      FbaReconciliationJob 保留作兜底提醒);实现等 #3 真凭证,槽位注释与实现指引在
      `erp-platform-sdk` `AmazonClient.java` 尾部。领域数据面(fba_shipment 五表/状态机/OUT_SHIP 动账/
      diff 对账)已于 2026-09-12 落地 erp-fulfill。

### P2 —— 无外部依赖,数据就绪即可开工

> 实施计划书(2026-09-10 拍板,自包含可直接投喂执行模型):未完成项对应 FBA Shipment→`docs/plans/fba-shipment.md`、
> #24→`docs/plans/24-adapter-playbook.md`、#26→`docs/plans/26-frontend-audit.md`;#19(含 #32 校差)→
> `docs/plans/archive/19-profit-caliber.md`(2026-09-12 #32 落地后归档);主体已落地、余量未清的
> order-review-split/warehouse-ops/payment-receipt/first-mile-freight 四份同留本目录。
> **已实施计划书归档至 `docs/plans/archive/`**(06-report-tools/25-oss-storage/27-rbac-enhance/28-deploy-docker/
> sse-notify/19-profit-caliber);索引与执行顺序见 `docs/plans/README.md`。
> 投喂方式:逐项现成执行提示词见 `docs/plans/execution-prompts.md`(已完成项的提示词仅存档不再投喂)。

- [x] **#19 周期利润口径**(2026-09-12 全量收口:主体 2026-09-11 落地两表 platform_fee_rate + profit_period_report、
      费率 CRUD、预估接线(行级 commissionEstimated,实际优先无费率不猜)、ProfitPeriodService 双侧聚合骨架、
      SettlementPullJob 骨架默认关、菜单 41/42;①②存量库两表/菜单重放与真库聚合 SQL 覆盖已于 2026-09-12 收口
      (replay 幂等重放 + validate_profit_sql.py 全绿,含 #27① shopIds IN 数据权限过滤验证);
      计划书第七节拍板点全部落定(周期粒度=报告期/费率维度=列预留不开放/报表归属=财务域自营/容差与 DIFF 随 #32),
      **#32 校差落地后计划书归档**,devlog 见 `docs/devlog/TODO19-周期利润口径.md`(#32 补篇同文件))。
- [x] **#32 周期利润校差算法**(2026-09-12 落地:登记为「人工实现,铁律 1」,经用户逐项拍板(①-⑤)并
      **明确授权 AI 会话实现**;拍板与实施记录见 `docs/devlog/TODO19-周期利润口径.md` 补篇):
      ①跨期系统性差(结算 posted_at/订单 order_time 落窗)不修补窗口,diff_remark 注明禁吞差;
      ②容差 0.01 CNY(同 RefundReconciliationService),任一超容差 diff_flag=1,缺口计数进 remark 不静默归零;
      ③三态 RATE_MISSING 优先(缺汇率 CNY 列全 NULL);
      ④收入差=结算 SALE 行合计−订单收入(A 案同口径对齐),TRANSFER 存列不进差值,REFUND/ADVERTISING/OTHER 归 otherFee;
      ⑤预估佣金不加列,计数进 remark。
      实现:`PeriodSettlementSide` 拆 settleSales 五列;`ProfitPeriodReportService.calibrate` 放行
      (差值/容差/remark 拼装/三态状态机)+ rebuildForReport 落 `upsertPeriod` ODKU upsert
      (VALUES 行别名 AS new,uk(shop_id,settlement_id) 幂等整行覆盖);rebuildAllParsedReports 逐期 try-catch 隔离;
      SettlementService PARSED 派生钩子(deriveOnParsed)新增/覆盖两路径同事务触发;ProfitPeriodJob 翻默认开。
      验证:单测三态(平 OK/不平 DIFF+remark 断言/缺汇率 RATE_MISSING)+容差边界 8 例绿;
      validate_profit_sql.py 扩 11→12 项(upsertPeriod ODKU 真库形态)同日全绿;全仓 mvn test 绿。
- [ ] **#29 订单域补课余量**(主体 2026-09-11 落地:订单审核(风控判定+审核状态机)/拆单增强(发货单按仓/按物流多单)/
      内销订单手工录单(MAN-* 合成单号);计划书 `docs/plans/order-review-split.md`,
      devlog 见 `docs/devlog/TODO29-订单域补课(审核拆单内销录单).md`):余量:
      拍板待定:全量待审 vs 风险命中才审(已按后者实现)、自动拆单建议算法(未做)。
      (原①②存量库六列 ALTER+idx_review+菜单 901/902 重放与真库 ODKU 不冲审核列/casReviewStatus 状态机验证
      已于 2026-09-12 收口:replay 幂等重放 + validate_mapper_sql.py 检查 1 扩容全绿)
- [ ] **#30 仓内作业余量**(盘点单(六态状态机+建单快照/确认时点双口径+差异 ADJUST 动账)/调拨单(确认即达两腿动账+
      biz_type 收口)2026-09-11 已落地,前端两套页面同日收口;计划书 `docs/plans/warehouse-ops.md`,
      devlog 见 `docs/devlog/TODO30-仓内作业(盘点单调拨单域).md`):余量:
      ①**调拨在途模式**(OUT 占用→到货 IN)未做——V1=确认即达(拍板点④),多仓地理分离时才硬;
      ②**盘点冻结**(停机动账)明确不做;③**库存质量维度**良品/残品分账(三期,随残品列评估);
      ④**仓库删除引用校验**已纳入 stocktake_order/transfer_order(2026-09-12 收口:四域合计,
      StocktakeOrderService/TransferOrderService.countByWarehouseId 经 WarehouseApi 汇总,单测绿);
      ⑤**库位/批次:评估结论不做**(触发点=进口效期合规/库龄精细化报表/多库位拣货瓶颈,任一成真实痛点再按 add-domain 立项);
      ⑥菜单种子 3808/3809 补登记与真库动账/三方差验证已于 2026-09-12 收口(replay 幂等重放 +
      validate_inventory_sql.py 5 项全绿);三方对账面随实际使用评估。
- [x] **FBA Shipment(V1 内部数据面)**(计划书 `docs/plans/fba-shipment.md`,代码 2026-09-12 落地):
      FBA 发货单域落 erp-fulfill(拍板点②库存在预拍板内:SHIPPED 一步 OUT_SHIP,装箱不占库存;模块落 erp-fulfill)——
      状态机 DRAFT→BOXED→SHIPPED→RECEIVING→CLOSED(CANCELED 旁路仅 DRAFT/BOXED,cas 五守卫);
      五表 fba_shipment/fba_shipment_item(计划行,相对计划书草案新增——装箱勾稽 Σbox_item=计划量 的存储位,
      漂移记计划书头部)/fba_box/fba_box_item/fba_shipment_diff;SHIPPED 复合事务(勾稽不平拦截 +
      逐 SKU OUT_SHIP 经 change() 唯一入口,biz_type=FBA_SHIPMENT,成本随移动加权账结转,可用不足整单回滚);
      收货登记 SHIPPED/RECEIVING 两态可进(首登 cas 占位/重复登记先删后插幂等),diff 三态 SHORT/EXTRA/OK;
      发出 SKU 未全部登记即拦(未登记显式填 0=SHORT,防半量登记失真);删除仅 DRAFT/CANCELED 物理删;
      FbaReconciliationJob 默认关(`erp.fulfill.fba-reconciliation.enabled=false`)超期聚合告警;
      V2 SP-API Inbound 客户端 = TODO(#35) 槽位(AmazonClient 尾部,随 #3 真凭证)。
      后端 16 例单测(勾稽拦截/动账 captor 断言/diff 三态/幂等重登)+ testgen 守卫 2 例(close/cancel),
      全仓 mvn clean test 绿;前端 gen:page(spec=fulfill-fba-shipment.txt)+ 建单表单(计划行+装箱子表行编辑)/
      收货登记弹窗/详情抽屉定制,openapi.json 手改同步(同 #19/#29 口径),门禁四件绿。
      余量:①真库验证已于 2026-09-12 收口(scripts/validate_fba_sql.py 5 项全绿:五表+菜单 46/4601~4608
      幂等重放/cas 五守卫逐态命中与拦截/FOR UPDATE 行锁读/pageRows 店名仓名 LEFT JOIN 与过滤形态);
      ②店铺存在性后端校验未做(ShopQueryApi 无单店视图方法,前端店铺下拉兜底,需要时按「契约只加方法」扩容);
      ③店铺轴数据权限未注入 FBA 查询(单据带 shop_id,多用户真实使用后按 #27① 口径补);
      ④SHIPPED 逆向(作废重开)流程随实际使用拍板;⑤FBA 库存全量跟踪(FBA 仓建 inventory 行)延后,
      触发点=FBA 库存差异成为真实痛点。
- [ ] **#31 收付款/回款余量**(主体 2026-09-11 落地:统一资金流水 payment_record(+payment_alloc 分摊)——采购付款登记
      (强制 CNY/未审核不可付/超额拦截/部分挂账)、手工登记(多币种冻结汇率)、结算报告落 PARSED 同事务自动派生回款
      (uk_ref 幂等)、作废制、查询面三件、采购单已付/待付 ΣNORMAL 分摊聚合、供应商 settle_days 账期列;
      后端 23 例单测 + 前端门禁绿;计划书 `docs/plans/payment-receipt.md`,
      devlog 见 `docs/devlog/TODO31-收付款回款.md`):余量:
      ①开发库已于 2026-09-12 收口(replay 幂等重放 settle_days/菜单 40 段 + 存量 PARSED 派生缺口 0 +
      validate_payment_sql.py 4 项全绿);其他环境存量库统一跑 scripts/replay_schema_migration.py 幂等重放;
      ②拍板挂起:付款审批流(登记制起步)、账期到期提醒(预警引擎候选)、已付冗余列
      (量级上来再评估);③已知边界:未分摊预付/挂账款不进供应商应付视图,只在流水明细核对。
- [ ] **#33 头程运费分摊余量**(主体 2026-09-11 落地:四表 first_leg_shipment/box/box_item/alloc 落 erp-finance、
      状态机 DRAFT→BOXED→SHIPPED→ALLOCATED→CLOSED(CANCELED 旁路)、SHIPPED 录运费冻结汇率、
      QTY/WEIGHT/AMOUNT 三策略分摊+尾差并入最大基数行、uk+cas 双兜底不可重算覆盖;拍板:路线 B 期间费用落
      first_leg_alloc 独立表不触碰 sku_cost_state 移动加权账、模块归 erp-finance、SHIPPED 不联动跨仓动账;
      后端 33 例单测 + 前端门禁绿;计划书 `docs/plans/first-mile-freight.md`,
      devlog 见 `docs/devlog/TODO33-头程运费分摊.md`):余量:
      ①开发库已于 2026-09-12 收口(replay 幂等重放 + validate_first_leg_sql.py 7 项全绿);
      其他环境存量库统一跑 scripts/replay_schema_migration.py 幂等重放;
      ②SKU 维度头程费用汇总(/sku-allocs)接入第三层利润报表——前置 #32 已于 2026-09-12 落地,
      第三层(全费用分摊)本身未立项,随真实使用评估立项;
      ③箱唛打印 CLODOP、物流商 API 取价=四期明确不做;④SHIPPED 后逆向/作废重开流程随实际使用拍板;
      ⑤SKU 编辑页尺寸录入随 FBA 装箱需求评估。
- [ ] **#24 其他电商平台 adapter 扩展**:在 P1 首个国内 adapter + 跨境第二平台 adapter 落地后,批量接入剩余平台——
      国内(淘宝/京东/拼多多/微信小店/快手/小红书)、跨境(eBay/Shopee/Lazada/TikTok/速卖通/Temu)。
      前置 = 各平台 ISV 资质/AppKey;防腐层 SPI 已就绪,单平台接入复用 `PlatformClient` 契约 + `Unified*` 模型,
      adapter 内只做报文翻译(铁律 3);国内平台随 adapter 落地解锁电子面单取号/签收回传/国内结算链路。
- [x] **#26 前端页面走查第一轮**(2026-09-12:39 页全量走查,console error + 接口 500 + 渲染空态三维判定,4 Bug 全修):
      ①实时销售利润页白屏崩——`profitApi.page` 取 `page.records` 而后端契约 QueryPage 序列化为 `{list,total}`,
      SKU 翻译 `res.list.map` 抛 TypeError 表格数据全丢,修为取 `list` 并就地定义 QueryPageResult 类型;
      ②资金流水页 `.trim is not a function`——`netCny` 后端 BigDecimal 序列化为 number,前端按 string 调 `.trim()`,
      修为 `String()` 前缀判定 + interface 三金额字段改 number(展示层仍禁精度计算,docs/09 §6);
      ③供应商应付视图 500(后端)——`pageSupplierPayables` SQL 5 列 vs `SupplierPayableRow` 6 参构造器,
      MyBatis 构造映射按列序对齐直接炸,补 `unpaidAmount` 外层计算列;
      ④发货单/入库单/订单/售后四页每次进入必报 `row.id 缺失` error——el-table hidden-columns 预渲染列插槽时
      row 为空对象,expand 插槽加 `v-if="scope.row?.id != null"` 拦截(实测 v-if="scope.row" 拦不住空对象真值),
      组件守卫保留纯防御;真实展开明细功能复验正常。
      非问题澄清:调拨单 CanceledError=切页防竞态取消;部门/操作日志/平台回款空表=DB 无数据。
      门禁:oxlint 0 错 / vue-tsc 通过 / erp-finance 单测绿 / 真机复验全绿。
      余量:暗色主题/响应式样式、表单校验边界等深走查随实际使用继续(#26 常开)。
- [ ] **#26 前端页面优化与 Bug 修复(常开)**:在实际操作前端页面过程中发现 Bug 再逐项清理——
      不预先造 Bug 清单,而是**实际走查每个业务页面**(订单/库存/商品/采购/发货/售后/财务/报表/AI 对话/系统设置)发现问题后登记并修复。
      范围含:交互响应异常、数据加载竞态、表单校验遗漏、分页/筛选状态丢失、样式适配(暗色主题/响应式)、
      动态菜单权限边界、SSE 连接断线重连、加载态/空态/错误态覆盖。每轮走查产出 issue 列表,按影响面排序逐项修。
      门禁:oxlint 0 错 / oxfmt 归一 / vue-tsc 通过 / pnpm build 通过(docs/09)。
      二轮深走查(2026-09-12,非 ProTable 手写页 + 公共件,浏览器实测验证)9 处全修:
      ①AI 对话流式回复"冻结"——占位 AI 行以 raw 对象 push,流式 chunk 改原始引用不触发响应式,改经响应式数组
      取回 proxy 引用再追加;②AI 对话会话切换竞态(listMessages 序号守卫);③AI 对话页卸载 SSE 不断流
      (chatStream 透传 AbortSignal,onUnmounted 断流,abort 静默不弹错);④利润看板 summary/trend/skuRank
      三路并发竞态(序号守卫);⑤报表中心切 tab 不发请求——周报/SKU tab 复用日报数据且统计周期列空白
      (el-tabs 无 @tab-change),接 onTabChange 即拉;⑥报表中心日报/周报/SKU periodRows 竞态(序号守卫);
      ⑦商品分析 fetchTrend 竞态(序号守卫);⑧系统设置 loadGroup 组切换竞态(序号守卫);
      ⑨全局 errorHandler 误弹「未知错误 cancel」通知——ElMessageBox 取消 'cancel'/'close' 字符串 reject
      与业务错误 Result 对象经 Vue 异步事件链路路由到 errorHandler,改 `!(error instanceof Error)` 静默
      (拦截器已提示),真 JS 异常照旧 notification。
      判非:毛利率前端自算=文件头拍板设计(后端下发随 P3 拍板);config 数值校验=后端 INT/LONG/DECIMAL/BOOL
      已强校验;手写页"无 loading"=http 层默认全屏 loading;dept status 可清空=MP 缺省不更新语义。
      验证:浏览器实测周报独立取数/取消零通知/暗色 0 白块/config 切 tab 无串组/AI 真回复;
      门禁 oxlint 0 错 + oxfmt 归一 + vue-tsc + pnpm build 绿。
      三轮深走查(2026-09-12,暗色主题专项过全 11 域 40+ 页 + 公共件/布局硬编码色静态全量扫,浏览器实测):
      ①IconSelect(ProTable 图标选择器)硬编码边框 #dcdfe6/#4080ff 暗色下失真,改 var(--el-border-color)/
      var(--el-color-primary);②商品分析 goodsTrend 真库 500(残留多日,暗色走查顺带暴露)——
      selectSkuSalesTrend/selectSkuStockTrend 各只产 2 列 vs SkuTrendRow 三参 record,MyBatis 构造自动映射
      按列序对齐缺列直接炸(同本轮供应商应付 6 列先例),两条 SQL 各补 NULL 占位列凑齐 3 列;
      validate_report_sql.py 5→7 项扩容覆盖双趋势真库形态(教训再证:改 SQL 当天必须重跑对应验证脚本,
      单测 mock Mapper 测不出 XML 映射问题);③报表中心可视化调优(汇总卡 + SVG 趋势/Top15 条形/仓库构成
      堆叠条 + 简报结构化排版,纯 CSS/SVG 零依赖全 CSS 变量暗色适配,数据前端内存聚合零后端改动),
      顺带修 .table-box flex 压缩致图表底部裁剪(页面级滚动 + 卡片 flex-shrink:0)。
      判非:SKU匹配/FBA发货单/采购单"金额列被操作列遮挡"= 列总宽>容器的正常横向滚动(滚动后全列可达,
      固定列钉住无重叠);Menu/SubMenu、SearchMenu 的 #ffffff = 主色底白字明暗皆正确;底座水印字体色已
      isDark 适配;暗色专项全 11 域逐页(含新增/详情弹窗、抽屉、展开行交互)零白块零可读性问题。
      验证:浏览器实测商品分析出图无错弹;门禁 oxlint 0 错 + oxfmt 归一 + vue-tsc + stylelint + pnpm build 绿;
      后端 erp-report 单测绿 + validate_report_sql.py 7 项全绿 + 新 jar 真机复验。

### 已完成项收尾余量(环境依赖,统一登记)

> SSE 实时推送(2026-09-12)/#25 OSS 对象存储(2026-09-11)/#27 权限模块增强(2026-09-12)三条已完成条目
> 已从本清单移除(全文在 git 历史);实施拍板见 `docs/devlog/2026-09-12-SSE实时推送落地.md`、
> `docs/devlog/TODO25-OSS对象存储(RustFS).md`、`docs/devlog/TODO27-权限增强(数据权限店铺轴收口).md`,
> 计划书见 `docs/plans/archive/`,状态列见 docs/02。仅余环境依赖收尾:

- [x] **存量库重放**(2026-09-12 开发库全绿收口):#25 GROUP_OSS 种子段 + ai_kb_document 两列;
      #27 sys_user.dept_id + sys_dept/sys_user_shop/sys_oper_log 三表 + 菜单 44/45/205/4401~4403 与
      sys_role_menu 段;#29 shop_order 六列 ALTER + idx_review + 菜单 901/902;#30 菜单 3808/3809 与
      3801~3807/3901~3905 补齐;FBA 五表 + 菜单 46/4601~4608。工具 = `scripts/replay_schema_migration.py`
      (幂等重放正本 + 历史加列 information_schema 判存补齐,重跑安全);validate 家族 11 脚本全绿;
      #27 OrderProfitQuery XML shopIds IN 条件真库验证随 validate_profit_sql.py 第 11 项收口。
      (2026-09-12 晚 #26 修复后复跑:replay 3/3 + validate 家族 10 脚本全绿;validate_payment_sql.py
      对齐 pageSupplierPayables 补 unpaidAmount 后的 6 列构造 + 待付断言——印证"改 SQL 当天必须重跑
      对应验证脚本",#26 当天改 XML 未同步脚本,复跑才暴露)
- [x] **真 RustFS 端点冒烟(#25)**(2026-09-12 全绿):本机无 docker,起 rustfs 1.0.0-rc.6 Windows
      独立二进制(127.0.0.1:9000,rustfsadmin,常驻)+ boto3 建桶 erp-smoke;经后台 API 配置
      sys_config GROUP_OSS 五键 → SECRET 掩码回显 `******` 实测;四连:KB 文档上传走真
      OssService.upload 落桶(key=kb/{id}/rustfs-smoke.md,size 一致)→ GET /documents/{id}/file
      预签名 URL(30min)下载字节一致 → boto3 head/delete 404;冒烟数据已清理(KB doc/对象/配置键留,
      enabled=true 指向本机常驻 rustfs,停 RustFS 时业务按"未启用"口径静默降级)。
- [x] **SSE 真机双浏览器冒烟**(2026-09-12 收口):前端 vite 5173 + 后端真机,浏览器(登录 admin)
      与协议级 SSE 订阅客户端(等价第二浏览器)并行观察——①在线收帧 ≤2s:通知帧与 sys_notification
      created_at 同秒到达(python 客户端实测),心跳 30s 正常;②断线自动重连:页面 SSE 流存活 36.5min
      后断流,前端 0.5s 自动发起重连(Resource Timing 实锤,指数退避状态机工作,无雪崩);
      ③杀后端→重启:outage 期 console `ERR_ABORTED /subscribe` 证明重连循环活跃重试,后端恢复后
      新批次落库(扇出 1 人)且重连流恢复;④设计确认:onFrame 只刷未读数不弹 toast(与计划书一致,
      列表手动查询刷新)。观测限制:webview 在最后一环前挂死,「重启后浏览器面板收帧」未留最终截图,
      机制由 ②③ + DB 推送记录等价证实。回归:AlertJob/通知域 1566 行冒烟数据已清理(余 45 行原貌)。
- [x] **预警规则调度线程认证缺陷**(2026-09-12 修复+真机复测绿):`AuthContext` 增 `currentOrNull()`
      (无认证上下文返 null 不抛),`CurrentUserApiImpl.currentShopIds()` 无上下文返 null=不限
      (#27① admin 语义,HTTP 链路有 JwtAuthenticationFilter 拦截,无上下文只可能系统内部调用)——
      收口层一处修复同时覆盖 OrderQueryApiImpl.pageOrders / AftersaleQueryApiImpl.pageAftersales /
      AnomalyScanNode(异常工作流同跑调度线程,同踩此坑)。复测:新 jar + `--erp.alert.interval-ms=6000`,
      造 5 条 REFUNDED 售后单后一轮扫描 SHIP_TIMEOUT+REFUND_ABNORMAL 双双出帧落库(日志无 401,
      扇出 1 人;撤数据后下轮退款规则不再触发,正反验证);冒烟数据已清理,常驻后端已恢复默认周期。
- [x] **前端 `pnpm api:sync` 重抓契约快照**(2026-09-12):98 → 159 路径,新增全为 #19 报表/#29 资金/
      #30 利润/#25 知识库 OSS/#27 部门/SSE 订阅/#33 头程各手改端点,无移除(生成物零复核成本)。
- [ ] **#27 按需补**:详情按 ID 直取/写动作端点不在本期注入面(计划书 Query record 过滤口径)、perm_key 按钮级后端鉴权
      通道不做(展示层 v-auth + admin 方法注解已覆盖当前风险面)——多用户真实使用后按需补。

### P3 —— 拍板挂起(需真实使用数据/业务确认后拍板)

> 实施计划书(2026-09-10,触发条件式——条件不满足不开工):#6 HIGH 推通知/同买家规则/预警扩容→`docs/plans/p3-alert-monitor-expansion.md`、#6 三工作流定时接线→`docs/plans/p3-ai-workflow-scheduling.md`、#6 聊天页体验→`docs/plans/p3-chat-ux.md`、#6 四期 AI 深化→`docs/plans/p3-ai-deepening.md`、#11 代发/海外仓/回传异步化→`docs/plans/p3-fulfill-extensions.md`、#12 超退上限→`docs/plans/p3-aftersale-refund-cap.md`、#17 四小项→`docs/plans/p3-goods-ai-extensions.md`。
> **移动端(小程序/H5)与微信业绩推送:2026-09-10 拍板暂不考虑,不做规划**(重启时先补产品定位拍板)。

- [ ] #6:HIGH 级异常推通知(随实际告警量评估);「同买家批量下单」规则(契约无 buyer 字段,PII 不出契约,随 V2 契约扩容)。
- [ ] #6:采购/文案/选品三工作流定时接线(人工决策节奏,随实际使用拍板;补货/异常两工作流已接定时)。
- [ ] #6:前端聊天页 markdown 渲染/停止生成(引库需拍板);ai_suggestion payloadJson 结构化渲染(随前端评估)。
- [ ] #6:RAG 接入 agent 域(当前只接 chat)、意图识别/多语言客服、agent 更多角色(四期 AI 深化)。
- [ ] #11:FBA/OVERSEAS 发货单类型的供应商代发与海外仓发货流程(待业务确认后细化)。
- [ ] #11:发货回传异步化(当前 AFTER_COMMIT 同步执行,真凭证联调实测耗时后再评估;需独立 executor,禁复用 pullScheduler)。
- [ ] #12:超退上限按发货量收紧精确口径(当前按订单行数量放宽防误拦,随发货域数据完善后评估;Service javadoc 槽位)。
- [ ] #17:供应商比价(随多供应商数据积累评估)、文案平台风格适配 V2、listing 改写回填(随 adapter 上架类接口扩容)、选品权重自调优(四期评估)。
- [ ] #21 利润看板/实时利润页毛利率前端自算((profit/sales)×100 toFixed(1),文件头拍板的缺口口径)是否改后端下发——随 #19/#32 利润口径拍板一并议。
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
- [ ] 买家邮件/评论管理、AI 评论分析(部门/操作审计/数据权限已随 #27 于 2026-09-12 落地,移至已完成)。

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
- **TODO(#34) SSE 多实例广播**:NotificationSseRegistry 进程内 Map,单进程部署够用;扩多实例时改
  Redis pub/sub 广播(频道建议 `erp:notify:sse`,各实例订阅后转发本实例注册表),只换 broadcast
  传播介质,register/unregister/broadcast 口径不变;届时一并拍板踢旧策略与上限
  (SSE 实施拍板见 `docs/devlog/2026-09-12-SSE实时推送落地.md`)。
- **TODO(#35) SP-API Fulfillment Inbound 客户端(fba-shipment V2,2026-09-12 登记)**:createInboundShipmentPlan
  (平台发货计划生成)/putTransportContent(板箱信息回传)/getShipments(平台收货对账自动化,替代人工登记口径,
  FbaReconciliationJob 保留作兜底提醒)。实现等 #3 真凭证;槽位注释与实现指引在
  `erp-platform-sdk` `AmazonClient.java` 尾部;领域数据面(fba_shipment 五表/状态机/OUT_SHIP 动账/
  diff 对账)已于 2026-09-12 落地 erp-fulfill(见 docs/plans/fba-shipment.md)。
