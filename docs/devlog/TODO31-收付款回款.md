# TODO(#31) 收付款/回款(统一资金流水)

- 日期: 2026-09-11
- 计划书:`docs/plans/payment-receipt.md`(2026-09-10 预拍板,本会话实现)
- 收尾提交: 无(会话内未提交)

## 拍板

- **落点 erp-finance payment 域**(计划书拍板点②):payment_record/payment_alloc 两表 +
  PaymentRecordService 统一收口;对 erp-purchase 的引用只走 PurchaseQueryApi 契约
  (新增 findPurchasePayables 只读方法,erp-api 编排),不引模块依赖,同 #19 勾稽先例。
- **已付不冗余(拍板点①)**:采购单已付 = Σ payment_alloc 关联 NORMAL 流水,查询时 SQL 聚合;
  待付在 SQL 侧 DECIMAL 计算(前端金额 string 红线,禁 JS 浮点)。
- **采购付款强制 CNY**:采购总额是本位币,外币付款会混币勾稽;非 CNY 只允许结算回款(跟报告币种)
  与手工登记,落库即按 paidAt 回溯 resolveRate 冻结汇率,缺报价 amount_cny 留 NULL 进缺口计数。
- **作废制而非物理删**:NORMAL→VOIDED cas 条件更新留痕,分摊行不删(join NORMAL 自然失效),
  逻辑删除(deleted)与作废(status)双状态语义写进 docs/03 §6.3 与 DDL 注释。
- **派生在同事务**(红线):SettlementService 落 PARSED 且 transfer_amount>0 即调
  deriveSettlementReceipt,uk_ref(ref_type,ref_id,deleted) 幂等;重拉覆盖转 PARSED 刷新金额,
  人工作废(VOIDED)不复活;FAILED 暂存态不派生。
- **财务写 admin 双闸**:登记/作废 @PreAuthorize hasRole('admin') + 菜单 permKey 前端收口
  (finance:payment:purchase/manual/void),读侧登录即可,同汇率快照口径。
- **审批流不做**(交接边界④):登记制起步,银行/支付网关不接。

## 改动

- DDL:payment_record(统一流水)/payment_alloc(一付多单)+ supplier.settle_days 账期列;
  docs/03 §5/§6.3、01_schema_init.sql、菜单 40 + 按钮 4001~4003 + admin 绑定同步。
- 后端(erp-finance):PaymentRecordService 三写入口(采购付款登记/手工登记/结算派生)+
  作废 + 查询面(流水分页 join 往来方名、采购已付批量、供应商应付、平台回款、期间 CNY 汇总),
  PaymentQueryMapper.xml 五条自定义 SQL;守卫链:DRAFT 不可付/同供应商/按单已付+本次≤总额/
  Σ分摊≤流水额(允许挂账)/词表校验;流水号 PAY+yyyyMMdd+4位seq 撞号重试。
- 契约:PurchaseQueryApi +PurchasePayableView/findPurchasePayables;erp-purchase 加 listByIds;
  erp-api PurchaseQueryApiImpl 实现;SettlementService 同事务挂派生钩子。
- 供应商 settle_days 贯通 entity/SaveRequest/Response。
- 脚本:scripts/archive/payment_receipt_migration.py(建表+加列+菜单+存量 PARSED 报告
  NOT EXISTS 补派生,自检 derived=expected);scripts/validate_payment_sql.py **真库四项绿**
  (建表幂等重放/Σalloc NORMAL 过滤 VOIDED/供应商应付草稿排除/uk_ref 唯一键拦截)。
- 测试:PaymentRecordServiceTest 18 例(守卫链全负例+派生三态+缺汇率)+ SettlementServiceTest
  派生钩子 4 断言;全仓 mvn test 绿。
- 前端:gen:page 资金流水页(spec=finance-payment.txt)+ 采购付款/手工登记两定制对话框 +
  详情抽屉 + 供应商应付/平台回款两 tab + 期间汇总卡;采购单页加已付/待付列与"付款"入口
  (复用付款对话框预选 PO);供应商表单/列表加账期;api:sync 真快照(本会话起后端实测),
  门禁四件绿(type:check/oxlint/stylelint/build)。

## 坑

- **环境无 WSL/bash**:mvn-quiet.sh 跑不了,PowerShell 直跑 mvn 重定向日志同口径;
  gen:page 的 foreach 在 XML 提取验证脚本里 open/close 括号是标签属性不是体文本,
  validate 脚本需把 `<foreach>` 翻成 `(`、`</foreach>` 翻成 `)` 再注入哑元。
- **validate 脚本顺序**:payment 表首次不存在时预清扫 DELETE 会 1146,先幂等建表再清扫。
- **待付金额最初想前端算**:违反 docs/09 §6 金额 string 禁浮点,收口为后端 SQL
  total_amount-Σalloc,前端只直显。

## 未尽(已登记 TODO#31)

- 付款审批流(登记制起步,需业务确认)、账期到期提醒(预警引擎规则扩容候选)、
  已付冗余列(量级上来后评估事件刷新)。
- 存量其他环境库执行 payment_receipt_migration.py(本机开发库已跑并真库验证幂等)。
- 未分摊预付/挂账款不进供应商应付视图(无采购单归属),只在流水明细核对;V1 拍板。
- 结算 paid_at 取 UnifiedSettlement.depositDate(预计打款日),无值取落账时间;
  depositDate 未入库,历史补派生用报告 period_end 锚点。
