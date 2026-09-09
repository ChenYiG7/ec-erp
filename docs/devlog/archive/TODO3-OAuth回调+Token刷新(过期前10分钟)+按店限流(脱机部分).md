# TODO(#3) OAuth回调+Token刷新(过期前10分钟)+按店限流(脱机部分)

- 日期: 2026-09-04
- 收尾提交: 见 git log TODO(#3)

## 拍板
- Token 刷新收口 `ShopService.getShopSession`(授权中心唯一装配出口,拉单消费方零感知,docs/04"读取时检查"),
  不在 erp-api 编排——否则刷新需要 appKey/appSecret 出 ShopSession,凭证面扩大(TODO#2 遗留项维持搁置);
  跨实例防双刷新用 **DB CAS**(`WHERE token_expire_at <=> 旧值` 条件更新即守卫)而非 LockService:
  LWA refresh_token 不轮换、双跑双方结果均有效,锁版 single-flight 留给二期轮换型平台(LockService javadoc 原口径)。
- state 用 AES-GCM 加密签发(`用途|shopId|到期ms`,复用 ERP_TOKEN_KEY,10 分钟 TTL)而非 Redis 存:
  erp-shop 无 Redis 依赖,签名即防伪;LWA 授权码单次有效兜住重放窗口。
- 会话装配失败(含刷新失败)从"仅 warn 跳过"升级为记 pull_log 走连续失败告警(两 Job 同款,改 1 个既有测试):
  不升级则 docs/04"刷新失败告警并停该店铺拉单"永远不触发。

## 改动
- erp-platform-sdk 新增 `gateway` 包:PlatformRateGuard(Redisson RRateLimiter,key=`erp:ratelimit:{platform}:{bucket}:{shopId}`,
  窗口状态持久化 Redis 防重启丢、多实例天然合并;`erp.rate.*` 可配,fail-open 降级可关,配额等待超时抛 429)
  + PlatformGateway 装饰器(数据面限流、授权面直通);AdapterRegistry 构造统一包装;redisson 进 sdk pom(API 消费)。
- erp-shop:OAuthStateService(state 签发/校验)、ShopService.buildAuthUrl 实装 + handleOAuthCallback
  (复用加密链路 applyOAuthToken,refreshToken 空不覆盖)+ getShopSession 内刷新(ShopMapper.casRefreshToken XML `<=>` NULL 安全等值);
  ShopController 新增 `/oauth/callback`(HTML 结果页,平台参数名翻译留在入口)。
- erp-api:SecurityConfig 放行回调路径;OrderPullJob/ProductPullJob 会话装配失败记 pull_log + 告警接线;
  application.yml 新增 `erp.rate.*` 段。
- 单测 +31:ShopServiceTest 11→26、OAuthStateServiceTest 3、PlatformRateGuardTest 7、PlatformGatewayTest 5(含 Registry 包装);
  全仓 265 个用例绿(scripts/mvn-quiet.sh)。

## 坑
- Redisson 4.x `RRateLimiter.tryAcquire(long, TimeUnit)` 与 `tryAcquire(long, Duration)` 并存,
  Mockito 桩 `anyLong(), anyLong()` 会匹配失败(long→TimeUnit 不兼容),须 `any(TimeUnit.class)`。
- MySQL `<=>` NULL 安全等值写进 MyBatis XML 需转义 `&lt;=&gt;`,否则 XML 解析炸——CAS 守卫覆盖"从未记过期的行"(双 NULL)靠它。
- AdapterRegistry 构造签名变更(加 ObjectProvider<PlatformRateGuard>)零破坏:全仓无测试直接构造它(全是 mock)。

## 未尽
- SP-API getOrders 实调(AWS SigV4 签名器 + NextToken 翻页)仍封死在 AmazonClient TODO(#3),待真凭证;
  SP-API 配额真值以响应头 x-amzn-RateLimit-Limit 为准,SigV4 落地时接"响应头自适应间隔"替换静态配置。
- LWA redirect_uri 语义(授权码换 Token 时是否必传/必须与登记一致)待真凭证联调验证,当前回传实际回调地址。
- 二期多实例:限流 RRateLimiter 已天然跨实例;refresh 若遇轮换型平台(refresh_token 一次一换)需按 LockService javadoc 接锁版 single-flight。
