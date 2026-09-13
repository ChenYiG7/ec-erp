# TODO(#19) 周期利润口径(三口径第二层/预估费用模型)

- 日期: 2026-09-11
- 计划书:`docs/plans/19-profit-caliber.md`(2026-09-10 拍板,本会话落地骨架;校差核心按铁律 1 留 #32 人工槽位)
- 收尾提交: 无(会话内未提交)

## 拍板

- **周期粒度=结算报告期**(计划书拍板点①):profit_period_report 一期一报告行,
  uk(shop_id,settlement_id) 幂等 upsert;不用自然月(报告期天然对账,避免跨期拆分)。
- **费率维度平台×费种起步**:marketplace/category_path 建列预留但写侧不开放(SaveRequest 删除),
  估费 V1 只取全站点/全类目行;费种白名单仅 COMMISSION——FBA 仓储类无明确费率不猜,
  新增可估费种必须有费率来源再扩白名单。
- **估算口径**:实际佣金(settlement_detail 归集)永远优先;缺实际且售价可折 CNY 时
  预估佣金 = −售价CNY×费率(报告符号为负),行级 commissionEstimated 标志,无费率仍 NULL;
  费率一次性全量载入按平台分组内存挑选,禁逐行查库 N+1。
- **platform_fee_rate 进逻辑删家族**(第 12 张人工域表):人工维护主数据,
  @TableLogic(delval=id)+ uk 含 deleted;LogicalDeleteAnnotationTest 反射钉死同步扩 12。
  uk 含 marketplace/category_path 两个 NULL 列(MySQL NULL 不去重),Service 补显式同维查重。
- **校差算法封死 TODO#32**(铁律 1):双侧聚合(结算分费种/订单同窗口复用第一层 summarize)
  + 汇率快照已备,差值口径/容差标记/三态状态机/upsert 整体留人工;calibrate 抛
  UnsupportedOperationException,算法落地前不产生任何周期行,禁半成品脏数据;
  PARSED 同事务钩子与 ProfitPeriodJob 默认关,均随 #32 接线。
- **财务写 admin 双闸**:费率 CRUD @PreAuthorize hasRole('admin') + 菜单 permKey
  (finance:fee-rate:add/edit/remove),周期报告系统写入只读,读侧登录即可。
- **结算拉取默认关**:SettlementPullJob erp.adapter.settlement-pull.enabled=false,
  卡 #3 SP-API 真凭证;无窗口(离散正本,window 退化时刻,口径同 SHIPMENT),
  未实现平台 UOE 静默跳过;店铺锁 + pull_log SETTLEMENT 类型 + 连续 3 次告警。

## 改动

- DDL:platform_fee_rate(费率表,逻辑删)/profit_period_report(周期报告,系统写只读);
  docs/03 §6 sql 清单 + 新增 §6.4、docs/02 §9/§14 状态、docs/07 §6.4 人工域 11→12 表、
  菜单 41/42 + 4101~4103 + admin 绑定;**顺手修复种子脚本既有语法错**(290 行后误写分号,
  致 233 起到 4103 整段按钮菜单无 INSERT 头,新库重跑必炸,分号改逗号)。
- 后端(erp-finance,八件套 codegen + 人工修整):
  - PlatformFeeRateService CRUD(白名单/区间/同维查重/source 固定 MANUAL)+ resolveFeeRate +
    listGlobalRates/pickFeeRate 批量估费;PlatformFeeRateSaveRequest 校验注解
    (0<rate<1,@NotBlank 等),删 source/marketplace/categoryPath 服务端管理列;
  - ProfitPeriodReportService 只读查询 + rebuildForReport/rebuildAllParsedReports 骨架,
    ProfitPeriodQueryMapper.xml sumSettlementFees(本期唯一新自定义 SQL),
    PeriodSettlementSide 值对象(费种归类/带符号折算/缺汇率全 NULL/null 与 0 语义分离);
  - ProfitQueryService 预估接线:OrderProfitRow 契约加 commissionEstimated(末尾加字段,
    唯一构造点同模块),费率索引批量载入,利润扣实际/预估佣金;
  - SettlementService 派生钩子里留 #32 同事务接线注释槽(insert/overwrite 两路径共用)。
- erp-common:PullConsts DATA_TYPE_SETTLEMENT(pull_log 注释同步)。
- erp-api:SettlementPullJob(默认关,九 Job 同款:开关→会话→adapter→锁→拉取落库→pull_log→告警)、
  ProfitPeriodJob(全局锁 finance:profit-period,默认关待 #32);LogicalDeleteAnnotationTest 12 实体。
- 测试:PlatformFeeRateServiceTest 13 例(白名单/查重/区间/404/回溯挑选含 effTo/未来版本)、
  ProfitPeriodReportServiceTest 5 例(读映射/报告不存在/FAILED 跳过/PARSED 聚合后封死)、
  PeriodSettlementSideTest 4 例(缺汇率/归类符号/未知费种不丢/null vs 0)、
  ProfitQueryServiceTest 加 3 例(估佣命中/未生效不估/实际优先);**全仓 mvn test 绿**。
- 真库:validate_profit_sql.py 扩 6→10 项(两表+菜单幂等重放、sumSettlementFees 分组与报告隔离、
  uk 派生幂等),10 项全绿,开发库已对齐两表与菜单。
- 前端:gen:page 两页(specs finance-fee-rate/finance-profit-period);费率页 CRUD +
  日期选择器 + 生效止"长期"展示;周期只读列表 + 人工补详情对照抽屉(双侧 5 行对照+校差说明);
  实时利润页佣金列加"预估"标签与斜体灰样式;api:sync 真快照(133→137 路径,本会话起后端实测),
  门禁四件绿(type:check/oxlint/stylelint/build)。

## 坑

- **种子脚本潜伏语法错**:sys_menu 按钮 INSERT 中 `(290...)` 误以分号收尾,后续 233 等行成
  无 INSERT 头裸元组;存量库靠其他迁移脚本掩盖,新环境整脚本重跑会中断。本次随新菜单一并修复。
- **MP 3.5.17 重载坑复现**:BaseMapper insert/updateById 有 Collection 重载,mockito `any()`
  歧义编译失败,须 `any(Entity.class)`(TODO.md 既有坑,又踩一次)。
- **spring-boot:run 工作目录**:.m2 旧契约 jar(-pl 不带 -am)与根 local.properties 找不到
  (CWD=erp-api,ERP_TOKEN_KEY/JWT 缺失启动炸);解法:先 install 全模块,
  `-Dspring-boot.run.workingDirectory` 指仓库根。
- **PowerShell 吃参数**:`-Dsurefire.xxx=false` 被拆词,加引号传;中文 -D 参数避免走命令行
  (codegen nameZh 用表 COMMENT 默认值,文件 UTF-8 写出不受控制台编码影响)。

## 未尽(已登记 TODO#32 + #19 余量)

- **#32 周期校差算法(人工)**:窗口归属口径(下单时间 vs postedAt 跨期系统性差)、
  容差 0.01 标记/diff_remark、OK/DIFF/RATE_MISSING 三态优先级、otherFee 费种归类、
  upsert ODKU;落地后接 SettlementService PARSED 钩子 + ProfitPeriodJob 翻默认开 +
  校差三态单测(指引详见 TODO.md #32 与 calibrate javadoc)。
- 交接边界:费率站点/类目维度启用时机、周期报表是否进报表中心(默认财务域自营)、
  DIFF 后人工复核/自动重拉流程。
- 其他环境库重跑两表 CREATE 段 + 菜单段(01_schema_init.sql TODO#32 注释,幂等);
  行级实际佣金跨境折算仍为 V1 原值口径,随 #32 周期侧汇率快照一并完善。

---

# TODO(#32) 周期校差算法落地(补篇)

- 日期: 2026-09-12
- 性质: #19 预留的人工铁律槽位,经用户拍板授权 AI 会话实现(授权记录见 TODO.md #32 条目)

## 拍板(2026-09-12,用户逐项确认)

- **①跨期窗口差**:结算明细按 posted_at 归属报告、订单侧按 order_time 落窗维持不变,
  跨期订单(上期下单本期结算/在途退款)系统性差**不修补窗口**,diff_remark 注明禁吞差。
- **②容差**:0.01 CNY(同 RefundReconciliationService.AMOUNT_TOLERANCE 防尾差,严格大于才标记);
  |收入差|/|佣金差| 任一超容差 diff_flag=1,diff_remark 写差异项+跨期口径+缺口计数,计数不静默归零。
- **③三态优先**:rateUsed==null → RATE_MISSING 优先(缺汇率时 CNY 列全 NULL,diff 本就不可算);
  否则 DIFF/OK。
- **④收入差口径(A 案)**:收入差 = 结算 **SALE 行合计 − 订单收入**(同口径对齐:平台认的订单侧
  收入流 vs 本地订单售价);TRANSFER 作打款事实存 settle_income 列**不进差值**;
  REFUND/ADVERTISING/OTHER 归 otherFee(带符号);SALE 合计随收入差进 diff_remark 供复核,零表结构变更。
  PeriodSettlementSide 拆出 settleSales 五列,SALE 移出 otherFee 归类。
- **⑤预估佣金占比**:不加列,佣金缺口计数(含估算命中行)进 diff_remark,更细占比再评估扩列+契约。

## 改动

- `PeriodSettlementSide`:SALE 单列 settleSales(五列),otherFee 排除 SALE,注释收口拍板口径。
- `ProfitPeriodReportService.calibrate` 放行(#32 拍板口径全量实现):差值(同号相减正=结算侧多,
  结算侧缺 SALE/COMMISSION 行时对应差不可比置 null 不硬算)/容差 diff_flag/diff_remark
  (差异段+缺口段拼装,≤500)/三态状态机;rebuildForReport 落 ODKU upsert 收尾;
  rebuildAllParsedReports 去类级事务改逐期 try-catch 隔离(单期唯一写=单语句 upsert 自原子,
  PARSED 同事务路径经 SettlementService 代理调用 REQUIRED 加入外层事务)。
- `ProfitPeriodReportMapper.upsertPeriod` + XML:VALUES 行别名 `AS new` ODKU(9.7.2 原生形态,
  ODKU 内列引用 new. 全限定),uk(shop_id,settlement_id) 幂等整行覆盖,created_at/updated_at 交库默认值。
- `SettlementService`:注入 ProfitPeriodReportService(同模块无环),PARSED 派生钩子更名
  deriveOnParsed(回款+周期利润双派生),新增/FAILED 覆盖两路径统一同事务触发 rebuildForReport。
- `ProfitPeriodJob` 默认翻开(enabled true,yml 同步):无 PARSED 报告空转零噪音。
- 实体/Response/01_schema_init.sql DDL 注释收口(TODO#32 悬空引用改拍板结论)。
- 测试:ProfitPeriodReportServiceTest 重写 5→8 例(读映射×2/报告不存在/FAILED 跳过/
  **平 OK/不平 DIFF+remark 断言/缺汇率 RATE_MISSING/容差边界 0.01 不超容差**);
  SettlementServiceTest 适配新依赖+PARSED 两路径触发与 FAILED/幂等跳过不触发断言。

## 验证

- `python scripts/validate_profit_sql.py` 扩 11→12 项:新增 upsertPeriod ODKU 真库形态
  (同 uk 二次执行整行覆盖断言),12 项全绿(开发库 MySQL 9.7.2 实测,**改 SQL 当天重跑红线**履行)。
- 全仓 mvn test 绿;无契约/端点变更,openapi 快照与前端零改动。
- **端到端真机验收(计划书 §5,新 jar 重启后端实测)**:哑元 PARSED 报告两份(A=USD 含 SALE 500/
  TRANSFER 400/COMMISSION −50+哑元报价 7.0,B=ZZZ 无报价币种)→ ProfitPeriodJob 默认开
  **启动即触发首轮兜底重算** → 查库断言:A=DIFF(diff_flag=1,diff_income=3500.0000 精确,
  diff_remark 含「SALE 3500.0000−订单 0」与跨期口径)、B=RATE_MISSING(rate_missing=1,CNY 列全 NULL);
  **首轮验收抓出 rate_used 落库 NULL 缺陷(见坑)修复后复验 GREEN**,哑元数据已清理
  (库中周期行清零、USD 哑元报价删除),常驻后端保持新 jar 运行。

## 遗留边界(登记,不阻塞)

- **行级实际佣金跨境折算仍为 V1 原值口径**(settlement_detail COMMISSION 归集报告原币未折 CNY,
  预估佣金已按 CNY):#32 拍板范围未含第一层管线改动,周期行 order_commission/diff_commission
  继承该口径(跨期系统性差之外或叠加汇率分量,diff_remark 已注明口径差异供人工判读);
  真实结算数据校准后评估是否行级折算(改第一层利润口径属独立立项)。

## 坑(补篇)

- **builder 公共段硬编码字段值吞快照**:baseBuilder 预置 rateUsed(null) 后正常路径漏回填,
  单测未断言该字段全绿放过,**真机端到端(哑元 PARSED 报告 → Job 启动即触发 → 查库)当场抓住**
  DIFF/OK 行 rate_used 落库 NULL 而校差值正常——教训:①公共 builder 段不放具体值只放身份列;
  ②凡「快照/来源可追溯」类字段单测必须显式断言;③算法类改动即使三态单测全绿也要走一轮
  哑元数据真机验证(与「改 SQL 当天重跑验证脚本」同级的兜底纪律)。
