# TODO(#12) 售后退货入库:receive-return 复合事务激活 IN_RETURN 动账

- 日期: 2026-09-04
- 收尾提交: 32b4fea TODO(#8) codegen二工具:状态机守卫测试生成器StateMachineTestGenerator(spec驱动产XxxStateMachineTest守卫四类用例,#10/#11/#12守卫用例四连沉淀铁律#8),aftersale spec样板入库,试点生成7用例跑绿后删除

## 拍板
- 退货明细数据来源 = receive-return 人工录入实收(可≠平台申明,对齐采购入库实收模式):售后单唯一来源是平台同步(卡 #3 真凭证),
  平台申明路径无法端到端使用,实收路径不阻塞且贴合仓库验件现场。
- receive-return 升级复合事务动作(同 #10 confirm/#11 ship 先例),不拆独立入库动作:避免"已收退件未入库"中间态,
  refund 的 RETURN_RECEIVED 前置血缘因隐含"已入库"更硬。校验强度全套:仓存在/明细非空正数/归属(复用 findDeliveryView,
  仅 sku_id 已绑定行可退)/数量预校验(历史累计+本次≤订单行数量,跨售后单聚合)。

## 改动
- 表结构(add-table 三方):aftersale_order 加 warehouse_id(收退件回填,同步落库 NULL)+ 新表 aftersale_return_item
  (id/aftersale_id/order_item_id/sku_id/return_qty,codegen parts=entity,mapper);docs/03 §5、01_schema_init.sql、
  TODO.md ALTER 清单三处同步;已建库环境手工执行 aftersale_order ADD COLUMN 一条(售后表无存量数据,无归仓问题)。
- 契约消费:erp-aftersale pom 新引 erp-contract(零实现);ShopOrderApi.findDeliveryView 契约注释补 #12 消费方口径;
  AftersaleConsts 收口 BIZ_TYPE_AFTERSALE_ORDER;InventoryConsts.FLOW_TYPE_IN_RETURN 首次启用。
- 业务:AftersaleOrderMapper.receiveReturn(RETURNING→RETURN_RECEIVED 同 UPDATE 回填 warehouse_id,COALESCE result);
  AftersaleReturnItemMapper.listByOrderId(join 历史退货,内存聚合做预校验);Service 校验链→占位→逐行动账→明细落库
  @Transactional;Controller receive-return 入参换 AftersaleReturnReceiveRequest(@Valid);详情 withReturnItems 带明细。
- 测试 12→17:旧单 cas 用例删除,新增复合组 6(动账 captor 逐字段/校验链全分支/占位脱靶无副作用),`mvn test` 13 模块全绿。

## 坑
- erp-aftersale 首次依赖 erp-contract,`-pl erp-aftersale test` 直接编不过:本地仓库 erp-contract 快照落后(缺 #11 的
  ShopOrderApi)——带 `-am` 连上游一起构建即解;跨模块新引契约时先 install 或 -am。
- testgen spec(testgen-aftersale.txt)receiveReturn 行因动作复合化而失效(按旧 spec 再生成必编译不过):已删行并注释
  "复合动作出射程,同 ship/confirm";spec 是状态机拍板表,动作形态变了要同步改,否则下次 force 重跑会炸。

## 未尽
- 超退精确口径:上限按订单行数量放宽(不误拦),按"发货量"收紧留 TODO(#12) 在 Service javadoc,随 #11 发货数据完善后评估。
- 平台售后同步 upsert(uk_shop_platform_refund)随 #3/#4;换货补发出库走发货单域;退款金额财务勾稽随三期 settlement;
  未绑定行退件补录随 SKU 匹配完善(同 #11 补发机制)。
