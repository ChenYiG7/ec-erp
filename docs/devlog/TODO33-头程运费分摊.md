# TODO(#33) 头程运费分摊

- 日期: 2026-09-11
- 收尾提交: 无(会话内交付,未提交)

## 拍板

- **路线 B 期间费用**(人工确认):运费分摊落 `first_leg_alloc` 独立表,作 SKU 维度期间费用行供利润第三层聚合;
  不进 `sku_cost_state` 移动加权成本账。方案 A(资本化 IN_PURCHASE 抬加权均价)影响面大,翻案必须先补成本账
  历史重放/快照对账评估,本期不做。
- **模块归属 erp-finance**:费用域收口,仓库/SKU 只作外键,跨域取数一律走契约(新增 WarehouseApi 仓型视图、
  GoodsQueryApi 批量 SKU 属性),erp-api 编排胶水实现。
- **本期 SHIPPED 不联动跨仓动账**:海外仓/FBA 是否 track 库存随 fba-shipment 联动拍板;头程单是费用/装箱单据,
  不背库存移动语义。
- **单据域物理删除**:计划书草案给主单写了 `deleted` 列,落正本时按 docs/07 §6.4 拍板改为与采购/发货/盘点/调拨
  五个单据同例(物理删除,仅 DRAFT/CANCELED;SHIPPED 起财务事实禁删),分摊行 append-only——审计价值在
  alloc 事实上,草稿单据不需要逻辑删。
- **箱毛重不进 V1 分摊算法**:计划书房略提"毛重优先箱重",但混装箱内多 SKU 没有规范的毛重拆分量纲;
  WEIGHT 一律 qty×product_sku.weight_g(跨境计费惯例),箱毛重/外箱尺寸只作装箱记录面。
- **AMOUNT 单价回退链**:最近采购价(PurchaseQueryApi 既有契约,非草稿采购最新一行)→ product_sku.cost_price
  → 视为缺基数。

## 改动

- DB(docs/03 §2/§6.5 + 01_schema_init.sql,开发库已真库执行):first_leg_shipment/box/box_item/alloc 四表;
  product_sku 加 length_mm/width_mm/height_mm(weight_g 此前已有);菜单 43 + 按钮 4301~4308。
- erp-finance 头程域:六态状态机 DRAFT→BOXED→SHIPPED→ALLOCATED→CLOSED(CANCELED 旁路仅 DRAFT/BOXED),
  五个 cas 条件更新;SHIPPED 录运费冻结汇率(CNY 短路 1/手填优先/空 resolveRate/无报价拦截禁猜);
  三策略分摊 + 全0降级(留 alloc_remark)+ 部分缺基数拦截(列明 SKU)+ 尾差并入最大基数行(并列最小 skuId,
  Σalloc 结构性=freight_cny);单号 FL+yyyyMMdd+4seq;分页双仓名 XML 投影 + SKU 费用汇总查询面。
- 契约只加方法不改旧义:WarehouseApi.findWarehouseViewById、GoodsQueryApi.findSkusByIds
  (ProductService.listSkusByIds 落点,selectByIds 避 .in() 急切解析坑);erp-goods SKU 模型同步尺寸列。
- 测试:FirstLegShipmentServiceTest 33 例(守卫链/三策略勾稽/降级/拦截/FX/尾差)
  + testgen spec `erp-finance/testgen-first-leg.txt` 生成 close/cancel 四类守卫 2 例
  (box/ship/allocate 复合事务按生成器边界落手写);全仓 mvn test 绿。
- 真库 `scripts/validate_first_leg_sql.py` 7 项:建表/加列/菜单幂等重放 + 分页 join + SKU 聚合
  + uk_shipment_box/uk_shipment_sku + casBox 命中/脱靶 + 已分摊不可重算/不可取消。
- 前端:api:sync 真快照(145 路径),gen:page(spec finance-first-leg.txt)+ 手写三组件
  (FirstLegShipmentForm 装箱两级编辑/FirstLegShipDialog 录运费/FirstLegShipmentDetail 抽屉),
  动作按钮按态裁剪;type:check/oxlint/stylelint/build 四件绿。
- 运行中 8088 端到端冒烟:真实 SELF→OVERSEAS 单(600g×2/750g×3,运费 100 CNY)全链路,
  分摊 34.7826/65.2174、Σ=100,DRAFT 发货拦截、ALLOCATED 重算拦截、CLOSED 删除拦截均符合预期。

## 坑

- **尾差行选择受 HashMap 遍历序影响**:初版用 stream.max 内联 tie-break 选"最大基数行",Long 小值键的
  HashMap 桶序打乱了并列顺序,单测抓到 0.0001 残差落到非最小 SKU;改为先按(基数降序,skuId 升序)
  显式排序再取首行,脱离 Map 遍历序。
- **100÷3 的期望值想当然**:测试一度把 100/3 的 4 位小数结果写成 33.33,实际 HALF_UP=33.3333、尾差 0.0001;
  代码没错,是断言位数错。
- **MP 3.5.17 BaseMapper 有 insert/updateById 的 Collection 重载**:mockito 裸 `any()` 二义编译不过,
  必须 `any(Entity.class)`(TODO.md 已载此坑,新域再踩一次)。
- **CNY 短路要在 Service 显式表达**:ship() 若直接调真实 resolveRate 没问题,但纯 Mockito 单测里 mock 返回
  null;改为 Service 层对 CNY 显式 rate=1(同采购付款先例),语义与汇率服务口径一致且可测。
- **环境**:本机无 bash/WSL,mvn-quiet.sh/devlog-new.sh 跑不了,用 PowerShell 直跑 mvn 与手工建 devlog;
  spring-boot:run 工作目录在 erp-api/ 导致 local.properties 未加载(JWT 密钥启动校验失败),
  改为根目录 `java -jar erp-api/target/*.jar` 启动。

## 未尽

- 其他环境存量库执行 `scripts/validate_first_leg_sql.py`(建表 + product_sku ALTER + 菜单幂等重放,无回填)。
- SKU 维度头程费用汇总端点已交付(`/sku-allocs`),接入第三层财务利润报表依赖周期口径 TODO#32 先收口。
- SHIPPED 之后的逆向/作废重开流程(目前设计即"重算=作废重开",但没有作废动作入口)随真实使用再拍板。
- product_sku 长宽高三列后端模型/契约已通,SKU 编辑页是否暴露尺寸录入随 FBA 装箱需求评估(weight_g 已可维护)。
- 箱唛打印 CLODOP、物流商 API 取价:四期,本期明确不做。
