# #24 平台 adapter 批量扩展 Playbook

| 元信息 | 值 |
|---|---|
| TODO 条目 | #24 其他电商平台 adapter 扩展:国内(淘宝/京东/拼多多/微信小店/快手/小红书)、跨境(eBay/Shopee/Lazada/TikTok/速卖通/Temu) |
| 优先级 | P2(**硬前置 = 各平台 ISV 资质/AppKey**;P1 的首个国内 adapter 与跨境第二平台 adapter 是本手册的首批验证样本) |
| 性质 | **接入手册(SOP)**,非逐平台实施计划——资质未解卡前不写 12 份平台计划;解卡后按本手册逐平台执行 |
| 目标一句话 | 从 amazon adapter 样板提炼"新平台零阻力接入"完整 checklist,使单平台接入成本稳定在 1-2 周且主系统零改动 |
| 明确不做 | 电子面单账户管理/CLODOP 打单(#17 独立项,国内平台接入后启动);推送/Webhook 进单的落地方案(本手册只登记设计拍板点);各平台 API 细节(未拿到开放平台文档前**不预设**,执行时预研补录) |

## 一、背景与现状(2026-09-10 代码事实)

- **SPI 已收口**:`PlatformClient` 十方法——`platform()` / `buildAuthUrl(redirectUri, state)` / `exchangeToken(authCode, redirectUri, appKey, appSecret)` / `refreshToken(default,可抛 Unsupported)` / `pullOrders(session, start, end)` / `pullProducts(...)` / `pullRefunds(...)` / `pullSettlements(default)` / `uploadTracking(session, PlatformShipment)` / **`fetchWaybill(session, platformOrderId)`(SPI 已定义,国内平台必实现,跨境返 null)**。
- **装配全自动**:`AdapterRegistry` 注入 `List<PlatformClient>` 按 PlatformType 建 EnumMap,统一 `PlatformGateway` 装饰(限流:数据面先 `rateGuard.acquire(platform, shopId, bucket)`,bucket 仅 pull/write,key=`erp:ratelimit:{platform}:{bucket}:{shopId}`,Redisson 平滑限流 fail-open 可配);消费方 `get(platform)` 返 Optional,**miss=静默跳过零噪音**。
- **样板包** `adapter/amazon/`:`AmazonClient`(@Component + `@ConditionalOnProperty("erp.adapter.amazon.enabled")` 默认不注册)/`AmazonAdapterConfig`(五 Bean:LWA/SigV4/STS/Orders/Reports/Finances,端点可配指假服务)/翻译器四件(Order/Listing/Refund/Settlement→Unified,零业务逻辑)/`AmazonMarketplace`(23 站币种)/测试 12 个(假服务)。
- **unified 模型四件**:UnifiedOrder/UnifiedProduct/UnifiedRefund/UnifiedSettlement(+Line)——新平台翻译目标,禁私造中间模型。
- **PlatformType 14 平台已备**(国内 7 + 跨境 7,带 domestic 标志);`PlatformShipment` 行级 @Builder;ShopSession 由 `ShopService.getShopSession`(过期前 10 分钟刷新)装配;OAuth 授权中心(state 加密签发 10min TTL、DB CAS 防双刷新)已投产。
- **主系统零改动是硬指标**:OrderPullJob/ProductPullJob 等按"adapter 未接入"静默跳过,新平台接入不碰任何 Job/Service。
- 项目 skill:`write-adapter`(新平台 adapter 流程已沉淀,**本手册是其执行清单化,流程以 skill 为准**)。

## 二、接入 Playbook(每平台走一遍)

### Phase 0 资质与预研(人工,非技术)

- [ ] ISV 资质/AppKey/AppSecret 申请(周期 1-4 周,与开发并行启动);
- [ ] 开放平台文档预研,登记「平台差异表」(见 §三,逐平台补录):授权模式(标准 OAuth/code?)、进单形态(**拉 vs 推**——国内多走推送,docs/04 L53 已提示,涉接收端点设计拍板)、结算单形态(文件 vs API)、电子面单(国内)、限流策略(按 app?按店?)、消息推送签名验签。
- [ ] 沙箱/测试店铺资源到位。

### Phase 1 骨架(半天)

- [ ] `adapter/<platform>/` 包骨架:`XxxClient`(implements PlatformClient,@Component + `@ConditionalOnProperty("erp.adapter.<platform>.enabled")` **默认不注册**)+ `XxxAdapterConfig`(HTTP 客户端/签名器 Bean,端点可配——假服务测试钩子)+ `application.yml` 配置样例(抄 amazon L169 段)。
- [ ] 不实现的方法**显式抛 UnsupportedOperationException 并注释**(禁返回 null 假成功)。

### Phase 2 授权(1-2 天)

- [ ] buildAuthUrl/exchangeToken/refreshToken 对接平台 OAuth;对接既有授权中心(state 签发/CAS/过期刷新全自动,零改动);
- [ ] Token 落 ShopService 唯一入口(AES-GCM 加密红线,凭证字段 @ToString.Exclude);
- [ ] 联调:授权→回调→session 装配→过期自动刷新。

### Phase 3 翻译器(每类 1-3 天,质量重心)

- [ ] 逐件实现 `XxxOrderTranslator/XxxListingTranslator/XxxRefundTranslator/XxxSettlementTranslator`(报文→Unified,**禁业务 if**,docs/07 §8 防腐铁律);
- [ ] 单测:**真实报文脱敏样本做翻译断言**(fixture 目录随 amazon 惯例);字段映射表写成常量,禁散落魔法值;
- [ ] 幂等口径核对:平台单号/唯一键语义 → uk `(shop_id, platform_order_id)` 等,upsert 语义不破坏。

### Phase 4 拉单冒烟(1-2 天)

- [ ] 真凭证(或沙箱)冒烟:pullOrders 窗口拉取→saveUnifiedOrder 落库→SKU 映射匹配链路全通;
- [ ] 限流真值校准(平台响应头/文档限频 → erp.rate 配置,#3 SP-API 先例);
- [ ] 游标纪律核对:pull_log window_end 左叠 5 分钟重拉 + 唯一键去重,**翻译器必须容忍重复报文**。

### Phase 5 平台特性扩展(按差异表裁剪)

- [ ] uploadTracking(回传;国内平台涉电子面单号来源——#17 未落地前用人工运单号);
- [ ] fetchWaybill(国内必做;跨境返 null);pullSettlements(国内结算链路形态随差异表);
- [ ] **推送/Webhook 进单(国内)**:设计拍板点——接收 Controller + 验签 + 去重后转 saveUnifiedOrder;与拉单并存时以谁为准(推荐:推送只做"触发即时拉"的信号,数据面仍走统一 pull,防双写口径分裂)。

### Phase 6 收尾

- [ ] 前端店铺管理页平台下拉确认含新平台(sys_dict shop_platform 14 平台种子已备,核对即可);
- [ ] `--force` 校准一轮翻译 fixture(真凭证样本到位后,docs/07 §8);
- [ ] write-adapter skill 自查 + self-review;涉拍板写 devlog;TODO/文档同步。

## 三、平台差异登记表(预研后逐行补录,禁止凭想象预填)

| 平台 | 授权模式 | 进单形态 | 结算形态 | 电子面单 | 已知坑/拍板 |
|---|---|---|---|---|---|
| 抖店(P1 候选) | (预研补) | (预研补) | (预研补) | 需要 | (预研补) |
| 淘宝/天猫(P1 候选) | 同上 | | | 需要 | |
| 拼多多 | | | | 需要 | |
| 京东 | | | | 需要 | |
| 微信小店/快手/小红书 | | | | 按平台 | |
| Shopee(P1 候选) | | | | 跨境面单 | |
| Temu(P1 候选) | | | | (半托管模式差异) | |
| eBay/Lazada/TikTok/速卖通 | | | | | |

## 四、验收标准(单平台口径)

- 授权→拉单→映射→发货回传全链真凭证跑通;主系统 diff 为零(Job/Service 零改动)。
- 翻译器 fixture 断言全绿;重复拉取幂等(重复报文不产生重复行)。
- adapter 默认关(未配置 enabled 不注册 Bean,拉单 Job 静默跳过);开启后限流真值生效。
- 平台差异表该行已补录;devlog(如有拍板)与 TODO 状态同步。

## 五、红线提醒(本项目最重纪律区)

- **adapter 内只做报文翻译,出现 `if (业务)` 即防腐失败**(铁律 3);
- 凭证:AES-GCM、ShopService 唯一写入、脱敏唯一出口、禁入契约(docs/07 §7);
- **qihang AGPL-3.0 禁读源码**;其他对标平台参考表结构须 devlog 记出处(docs/10 §6);
- 平台限流走 PlatformGateway(禁裸 HTTP 绕过限流装饰);失败语义禁吞(拉单失败记 pull_log);
- Unified 模型是唯一落库面,平台私有字段进 raw_json,禁加列稀释统一模型。

## 六、交接边界

1. 每平台开工前:资质到位 + 差异表该行预研补录 + 人工拍板进单形态(拉/推)。
2. 推送接收端点设计(Phase 5)涉 erp-api 面,属跨端契约改动——回仓库根会话走 add-domain 流程。
3. 电子面单链路整体(#17)随首个国内 adapter 落地后单独立项。
4. 本手册由 write-adapter skill 每次实践后回填迭代(同 skill 维护纪律)。
