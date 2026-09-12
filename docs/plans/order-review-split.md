# 订单域补课(审核/拆合单/内销录入)实施计划书

> 已于 2026-09-11 主体实施(#29),devlog 见 `docs/devlog/TODO29-订单域补课(审核拆单内销录单).md`;
> 余量(环境依赖 + 拍板待定)见 TODO.md 对应条目。

| 元信息 | 值 |
|---|---|
| TODO 条目 | 订单域补课(docs/02 原规划未编号):订单审核(风控备注/地址校验)、拆合单(按仓/按物流)、内销订单录入 |
| 优先级 | P2(docs/10 §4 G6:无硬阻塞纯功能开发) |
| 前置依赖 | 无 |
| 目标一句话 | 平台订单增加人工审核闸门(审核状态+风控备注+地址校验),发货单支持按仓/按物流拆分,补内销订单手工录入通路 |
| 明确不做 | 多单合并发货(拍板延后,见 2.3);平台地址校验 API 接入;订单编辑平台同步字段 |

## 一、背景与现状(2026-09-10 代码事实)

- `erp-order` 实体:`ShopOrder`(地址六列 receiver_name/phone/country/state/city/address/zip、buyer_note、currency、exchange_rate、order_amount 等已齐);状态枚举 `WAIT_PAY/WAIT_SHIP/SHIPPED/COMPLETED/CANCELLED/CLOSED`;履约渠道 `SELF_FULFILL/FBA/OVERSEAS_WAREHOUSE`。
- 写入口纪律:`ShopOrderService.saveUnifiedOrder`(upsert 幂等,唯一拉单写口)与 `casOrderStatus`(条件推进,"本系统操作"唯一出口);`ShopOrderController` **仅两个只读 GET 端点**——平台同步正本表对外禁写。
- **审核/拆单零代码**,唯一痕迹是 erp-order/pom.xml description 预告("订单审核、拆分/合并、内销订单录入、发货单生成")。
- 相邻:erp-fulfill 发货单 `delivery_order` 单 order_id 外键,**结构上已支持一单多次部分发货**(delivery_order_item 为进度事实源);docs/03:订单审核/拆合单/内销均为二期 ❌ P2。
- **核心纪律冲突(本计划要解的题)**:「系统写入表(平台拉单正本)对外只读」vs 内销订单需要人工录入。

## 二、方案设计

### 2.1 订单审核域

- **加列不动源语义**:`shop_order` 加审核列(order_source/review_status/review_reviewed_by/reviewed_at/risk_flag,见 §四);**关键红线:`saveUnifiedOrder` 的 upsert 列清单必须显式排除审核列**——平台重拉覆盖时审核状态不许被冲掉(现状 upsert 为显式列更新则安全,执行时核对实现,若存在整对象 updateById 必须改显式列)。
- 状态机:`review_status` 独立于 order_status:`0=无需审核(默认) / 1=待审核 / 2=已通过 / 3=已驳回`。审核闸门落点:**erp-fulfill 建发货单前置校验**——通过 `ShopOrderApi.findDeliveryView`(契约已有)扩展返回 review_status,建单时 review_status∈{1,3} 拦截(契约扩字段,零新契约)。
- 哪些订单进入待审核(拍板点①):推荐默认**全部 WAIT_SHIP 待审核**起步太重,改"风险规则命中才置 1,否则 0 直过"——V1 规则仅两条:地址不完整(六列任一空/zip 格式明显非法)+ buyer_note 命中风控关键词(sys_config 词表白名单,#18 先例);后续扩规则。
- 风控备注:纯字段 + 人工操作记录(审核端点带 remark);操作留痕依赖 #27 操作审计(先以 review_reviewed_by/at 兜底)。
- 端点(新增,走独立 Service 方法,不复用拉单写口):`POST /api/orders/{id}/review`(approve/reject+remark)、`GET /api/orders?reviewStatus=`(现有分页扩展过滤)。

### 2.2 拆单(按仓/按物流)

- 现状已支持"一订单多发货单"(部分发货),拆单缺口实际是:**发货单维度缺仓库/物流商字段**与"按仓拆"的行级归属。
- V1 口径:建发货单时可选 warehouse_id + 物流方式(.delivery_order 加列,草案见 §四),同一订单多次建单即实现"按仓/按物流拆";**不做系统自动拆单建议算法**(拍板点②:人工拆,V1 够用;自动拆单规则留 TODO)。
- delivery_order_item 行级仓归属跟随发货单级(不做行级混仓,拍板默认)。

### 2.3 合并发货(延后拍板)

- 多订单合一发货单需 delivery_order.order_id 可空 + 订单关联表,动发货单核心结构,影响发足判定(#11 已投产逻辑)。**默认延后**,docs/02 的"多单合并发货"待国内电商场景(拆单合并是高频)落地后随国内 adapter 一并设计。此处只登记结论。

### 2.4 内销订单录入

- 解法:`shop_order` 加 `order_source`(PLATFORM/MANUAL)+ MANUAL 单用**合成平台单号** `MAN-{shopId}-{yyyyMMdd}-{4位seq}` 占 uk `(shop_id, platform_order_id)`,平台拉单天然撞不上;platform 列取店铺真实平台,落 `MANUAL` source 标识区分。
- 手工录入走**新写入口** `ManualOrderService.create`(erp-order 内),录基础字段 + 行(SKU 从映射库选,sku_id 必绑——内销单无映射翻译环节);录完 status=WAIT_SHIP 并按规则进审核流(与 2.1 共用)。
- 同步保护:saveUnifiedOrder 按 uk 命中 MANUAL 单时(理论不可能,防御)**拒绝覆盖并告警**。
- 端点:`POST /api/orders/manual`(录单)、`PUT /api/orders/manual/{id}`(仅 WAIT_SHIP 可改)。前端:订单列表加"手工录单"入口(add-page 生成器出列表页 + 手写录单表单组件)。

## 三、实施步骤

1. add-table skill:shop_order 加列(order_source/review_*/risk_flag) + delivery_order 加列(warehouse_id/logistics_company/tracking_no 若缺,执行时核对现有列)——三方同步。
2. 契约扩容:`ShopOrderApi.findDeliveryView` 视图 record 加 reviewStatus(erp-contract 全 record,零 MP 类型);erp-api 实现同步。
3. 审核域:ShopOrderReviewService(状态机:0/1→2/3,条件更新即守卫)+ 风控词表 sys_config 键族(GROUP_ORDER_REVIEW,白名单热更 #18 同构)+ Controller 端点。
4. fulfill 建单闸门:DeliveryOrderService 建单校验扩展(review 拦截信息区分"待审核/已驳回")。
5. 内销录单:ManualOrderService + 合成单号生成器(复用 poNo 生成先例)+ Controller;防御告警(NotifyPushedEvent)。
6. 拆单增强:建发货单表单加仓/物流字段(前端)。
7. 测试:同步覆盖保护单测(saveUnifiedOrder 不冲审核列)、状态机守卫测试生成器 spec、真库回归(validate 脚本家族若有订单 SQL 同步)。
8. 前端:订单列表(审核状态列/过滤/审核动作)+ 手工录单页,门禁四件。

## 四、表结构草案(最终以 add-table skill 落正本)

```sql
ALTER TABLE shop_order
  ADD COLUMN order_source VARCHAR(16) NOT NULL DEFAULT 'PLATFORM' COMMENT 'PLATFORM/MANUAL(#订单域补课)',
  ADD COLUMN review_status TINYINT NOT NULL DEFAULT 0 COMMENT '0无需审核/1待审核/2已通过/3已驳回',
  ADD COLUMN review_remark VARCHAR(500) NULL COMMENT '审核/风控备注',
  ADD COLUMN reviewed_by BIGINT NULL, ADD COLUMN reviewed_at DATETIME NULL,
  ADD COLUMN risk_flag VARCHAR(255) NULL COMMENT '命中风控规则摘要(地址缺失/关键词等)';

ALTER TABLE delivery_order
  ADD COLUMN warehouse_id BIGINT NULL COMMENT '发货仓(#拆单,空=未指定)',
  ADD COLUMN logistics_company VARCHAR(64) NULL COMMENT '物流方式/承运商',
  ADD COLUMN tracking_no VARCHAR(64) NULL COMMENT '运单号(人工录入场景;平台回传仍走原链路)';
```

## 五、验收标准

- 同步保护:同订单重拉后 review_status/review_remark 不变(单测+真库);MANUAL 单不会被拉单覆盖。
- 审核闸门:待审核/已驳回订单建发货单被拦且文案区分;通过后可建;cas 守卫测试四类用例绿。
- 内销录单全链:录单→审核→建发货单→ship 推进状态全通;金额 string 进前端(docs/09 §6)。
- 风控词表热更后新订单即时生效(#18 事件失效缓存口径)。
- 前端门禁四件 + openapi 快照提交;mvn 编译绿。

## 六、红线提醒

- `casOrderStatus` 仍是订单状态推进唯一出口,审核状态走**独立列独立条件更新**,两状态机不合并。
- saveUnifiedOrder 显式列 upsert(禁整对象 updateById 覆盖);uk 幂等语义不动。
- 契约扩字段全 record;前端禁读 Java 源码,契约以 `pnpm api:sync` 后的 tools/openapi.json 为准。
- 风控词表禁入凭证类 sys_config 键;系统写入表对外只读纪律对 review 端点的边界:审核是业务动作端点(Service 收口),不是开放通用写。
- SQL:无新复杂 SQL,ALTER 走 add-table;若加查询过滤索引按 9.7.2 惯例。

## 七、交接边界

1. 待审核准入策略(推荐"风险规则命中才审",全量审太重)。
2. 自动拆单建议算法(默认不做,留 TODO)。
3. 合并发货延后的结论确认(随国内场景再启)。
4. delivery_order 现有列核对结果若与草案冲突(如已有 tracking_no),以 add-table 流程实际 DDL diff 为准。
