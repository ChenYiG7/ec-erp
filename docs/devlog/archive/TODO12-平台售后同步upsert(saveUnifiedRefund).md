# TODO(#12) 平台售后同步 upsert(saveUnifiedRefund)

- 日期: 2026-09-05
- 收尾提交: 见 git log TODO(#12)

## 拍板
- 状态映射策略:平台状态只做**首插初始映射**(APPLYING→PENDING / WAIT_RECEIVE→RETURNING / FINISHED 按类型分流:退款类→REFUNDED、换货补发类→COMPLETED / REJECTED、CANCELLED 直落),人工状态机是处理主线;已存在单**仅平台终态回传(REJECTED/CANCELLED)条件推进未决态单**(PENDING/APPROVED/RETURNING)——CANCELLED 即 AftersaleConsts 预留的"平台同步撤单"出口,人工已决与终态单不被平台回传回退。upsert 的 ON DUPLICATE KEY UPDATE 里 status 走 CASE 条件表达式实现,非整体覆盖。
- order_id 翻译归属:saveUnifiedRefund 内部经 ShopOrderApi.findIdByPlatformOrderId 契约翻译(售后域自治,同 receiveReturn 用 findDeliveryView 先例),关联订单未入库(售后先于订单拉到)跳过返回 false 等下轮拉单窗口重拉,不抛异常断整批;未做批量预取是有意取舍——单条 uk 等值查 + 售后批量小 + 拉单窗口低频,接口内聚收益大于 N+1 微开销,Job 接线后实测有压力再批量化(同 #4 skuIdBySellerSku 模式)。
- 售后拉单 Job 本期不接:AmazonClient.pullRefunds 仍是占位,接了会产生 UnsupportedOperationException 失败噪音并误触连续失败告警;saveUnifiedRefund 先行落地,真凭证到位接 pullRefunds 即全通。

## 改动
- erp-contract:ShopOrderApi 扩 findIdByPlatformOrderId(shopId, platformOrderId) → 内部 shop_order.id,未入库返回 null。
- erp-order:ShopOrderService.findIdByPlatformOrderId(uk 等值反查 + LIMIT 1);erp-api ShopOrderApiImpl 转发。
- erp-aftersale:AftersaleOrderMapper.upsert(XML 首建,行别名语法同 ShopOrderMapper 先例;更新列仅快照字段+status CASE 条件推进)+ saveUnifiedRefund 唯一写入口(必填守卫/翻译/映射/upsert, aftersale_no=platformRefundId, reason 缺失用 description 兜底截 512)+ pom 新引 erp-platform-sdk(仅消费 UnifiedRefund,禁调平台 API,同 erp-order 规约 docs/07 §2.2)。
- 单测 +7:AftersaleOrderServiceTest.SyncRefund 组 6 用例(必填守卫全分支/订单未入库跳过/首插映射全字段 captor/FINISHED 四类型分流/其余状态映射/reason 兜底与截断)+ ShopOrderApiImplTest 1(契约翻译转发);全模块 mvn test 绿(aftersale 23、erp-api 32)。

## 坑
- UnifiedRefund 是 @Data+@Builder 无 toBuilder,单测变体构造先后试了 `.toBuilder()` 链和 `.setX()` 链,均编译失败(setter 返回 void 不可链);最终以 mutate helper(Consumer 改字段)落地,不为测试便利给共享 SDK 模型加 toBuilder = true。
- erp-aftersale 单模块 `mvn -pl test` 因 erp-platform-sdk 未 install 本地仓库而依赖解析失败,带 `-am` 跑依赖模块一并构建。

## 未尽
- 售后拉单 Job 接线(RefundPullJob 或并入 OrderPullJob)随 #3 SP-API 真凭证联调;pullRefunds(Finances API)实现 + 限流真值按 x-amzn-RateLimit-Limit 校准同批。
- 平台申明明细 items 不落库(aftersale_return_item 是人工实收凭证非平台申明);售后单 raw_json 列(翻译回溯重放)随 pullRefunds 接线时按 add-table 流程评估。
- 退款金额与财务勾稽(三期 settlement,TODO #12 原清单遗留)。
