# TODO 清单(由人工逐项实现)

> 约定:代码里所有 `TODO(编号)` 注释都对应本清单。简单 CRUD 已生成并编译通过;
> 复杂逻辑/AI 一律占位,理解 docs 后由你亲手补齐。建议按编号顺序做。
>
> 文档边界:docs/ 仅 `07-开发规范守则.md`、`09-前端开发规范守则.md` 与 `docs/sql/` 随仓库发布,
> 其余(01~06 设计文档、08 精读清单、devlog)为作者私有,不随仓库发布。
>
> ⚠️ 建库注意:建表脚本 `docs/sql/01_schema_init.sql` 于 2026-09-02(shop 唯一键、绑定列可空)、
> 2026-09-03(11 表补 updated_at、inventory 补 created_at)、2026-09-03 二批(新增二期单据 6 表:
> supplier/purchase_order/purchase_order_item/purchase_inbound/delivery_order/aftersale_order)
> 与 2026-09-04(新增 sys_notification 站内通知)修订过。
> 此前已建库的重跑脚本不生效的仅限 ALTER 类修订;新增表为 CREATE IF NOT EXISTS 幂等,重跑脚本即可补建。
> 手工 ALTER 清单见 #7(**开发库已于 2026-09-03 执行并验证**)。文档(docs/01~06)已与代码、脚本三方对齐。

## #1 安全认证 + RBAC(二期第1周)✅ 2026-09-02 完成
- [x] JWT(JJWT 0.13.0)+ Security 7 过滤链 + BCrypt;`POST /api/auth/login` 签发,后续 `Authorization: Bearer <token>`;
      `/api/**` 全部需登录(放行 login、/error、OPTIONS、文档路径),SecurityConfig + JwtAuthenticationFilter 收口 erp-api,业务模块不引 Security starter
- [x] 密码三条专用路径(创建必填初始密码 / 管理员重置 / 本人改密校验旧密码),通用 update 禁改密码;任何返回路径清空 password;401/403 统一 Result JSON
- [x] sys_menu / sys_role_menu / sys_user_role 三表 + admin 种子(幂等脚本,重跑 01_schema_init.sql 即可;默认 admin/admin@123,首登改密)
- 鉴权粒度 = role_key(`@PreAuthorize` 收口管理类接口);perm_key 已随 /api/auth/me 返回,按钮级校验按需启用;其余坑与约定(JWT 载荷重登录、JJWT base64 怪癖等)见 docs/07 §7

## #2 凭证加密(二期第1周)✅ 2026-09-02 完成
- [x] `CryptoService`(erp-shop/security):AES-256-GCM(随机 12B IV,Base64(IV‖密文+tag));密钥仅环境变量 `ERP_TOKEN_KEY`(32B 标准 base64),
      **无默认值,缺失/非法启动即失败(红线)**;生成 `openssl rand -base64 32`(PowerShell 命令见 README)
- [x] 写侧唯一入口 `createShop`/`updateShop`:加密 + 掩码回传防护(MASK_SUFFIX 回传置 null 忽略)+ null/空串不覆盖;
      读侧唯一出口 `pageShops`/`getShopById` 脱敏(密文前 6 位+`***`)+ `getShopSession` 解密装配;实体 @ToString.Exclude;解密失败转"请重新授权"且消息不含凭证
- [x] 单测 19 个(erp-shop:CryptoServiceTest 9 + ShopServiceTest 10);加密透传语义与换钥轮换坑见 docs/07 §7
- 遗留:~~OAuth 回调 + Token 刷新~~(✅ 2026-09-04 随 #3 落地);ShopSession 缺 appKey/appSecret(国内签名平台需评估扩 SDK 模型);
  更新接口无法清空凭证(空串=不覆盖);`PUT /api/shops/{id}` 大而全更新与 docs/07 §6.3 冲突待拆;token 列 VARCHAR(2048) 接首平台时实测

## #3 第一个平台 Adapter(2026-09-04 选型定为 Amazon SP-API)
> 选型背景(2026-09-04):淘宝开放平台沙箱已停维护、订单类 API 有资质门槛,真凭证联调随账号就绪推进;
> Amazon 有官方 SP-API Sandbox,但拿 LWA 凭证需 Professional 卖家账号,注册审核周期约 1 个月——
> 账号就绪前脱机开发不阻塞,真凭证到位后即插即用。
- [x] 2026-09-04 脱机部分落地(erp-platform-sdk/adapter/amazon,详见 docs/04):
  - `AmazonClient implements PlatformClient`:LWA buildAuthUrl / exchangeToken / refreshToken(SPI 新增 default refreshToken);
    **默认不注册 Bean**(`erp.adapter.amazon.enabled=false`),真凭证到位后置 true 并补 app-id;
    未启用时拉单调度按"adapter 未接入"自动跳过,不产生 pull_log 失败噪音
  - `LwaTokenClient`(RestClient):授权码换 Token + 刷新,端点可配;异常消息只带 HTTP 状态码禁带凭证(docs/07 §7);
    单测用 JDK 自带 HttpServer 起本地假服务,不出网(AIR)
  - `AmazonOrderTranslator`:getOrders/getOrderItems 报文 → UnifiedOrder——状态机映射(Pending→WAIT_PAY 等,
    PartiallyShipped 按已发货推进 raw_json 兜底)/ISO8601 时间/Amount 十进制串直读(已是元)/AddressLine 合并/raw_json 必存;
    **官方模型样例报文做翻译单测**(脱敏,禁 mock 报文自嗨,docs/07 §8);未知状态/缺单号抛异常禁静默
  - Unified* 演进(只加字段不改语义,docs/07 §8):UnifiedOrder.Item.platformOrderItemId(#3)、
    UnifiedProduct.Sku.currency(#5);shop_order_item.platform_order_item_id 落库接线(#4)
- [x] 2026-09-05 AWS SigV4 签名器落地(adapter/amazon/SpApiSigner,纯 JDK 无状态,时间戳入参=固定时钟单测):
      规范请求五段式 + 四轮 HMAC;期望签名由 botocore SigV4Auth(Amazon 官方实现)冻结时钟交叉验证——
      官方向量套件(aws-sig-v4-test-suite)离线拉不到,脚本 `scripts/gen_sigv4_expectations.py` 五用例替代
      (最简 GET/常规查询/%3A 编码查询/POST body/STS 临时凭证;首版漏把 X-Amz-Date 并入规范头,被官方向量当场抓获,
      交叉验证的价值实证)+ StsTokenClient(AssumeRole 换临时凭证,查询串直接用签名器回传的 canonicalQueryString
      禁二次拼参;XXE 防护;异常只带 HTTP 状态码);单测 10 个(SpApiSignerTest 7 + StsTokenClientTest 3)
- [x] 2026-09-05 getOrders 拉单接线落地(脱机部分,adapter/amazon/SpApiOrdersClient):
      LastUpdatedAfter/Before **更新时间窗** + NextToken 翻页(MaxCount=100/页,翻页防御上限 50 页防令牌异常自旋)+
      逐单 getOrderItems 挂明细(明细同带令牌翻页);SpApiSigner 签名(service=execute-api),
      查询串直接用签名器回传 canonicalQueryString 禁二次拼参(StsTokenClient 同款纪律);
      AWS 密钥走环境变量/local.properties(键=环境变量名),配置 role-arn 时经 StsTokenClient 换临时凭证并缓存、
      **过期前 10 分钟刷新**(与 LWA 刷新节奏同口径,docs/04);
      AmazonClient.pullOrders 占位消除(会话缺 LWA token/未配 AWS 密钥/未配 marketplace-ids 均友好报错,
      `erp.adapter.amazon.enabled` 默认仍 false);
      假服务单测 9 个(SpApiOrdersClientTest 7:查询串/签名头/翻页/明细挂载/安全令牌头/错误只透状态码/防御上限;
      AmazonClientTest 适配新构造 +2 守卫);已知边界:getOrderItems 页内循环未单独走限流,随实调真值评估
- [ ] SP-API 真凭证联调(剩余部分):Seller Central 应用授权 + IAM 权限/role-arn 上线 + getOrders 冒烟;
      SP-API 限流真值按响应头 x-amzn-RateLimit-Limit 校准;~~pullProducts/pullRefunds 占位~~
      (✅ 2026-09-06 联调预备骨架落地,见下条)/ ~~uploadTracking 占位~~(✅ 2026-09-06 脱机落地,
      ✅ 2026-09-08 #11 编排接线落地,见 #11 条目)(docs/07 §12)
- [x] 2026-09-06 #3 联调预备骨架落地(选型拍板进 docs/04「Amazon 拉取/回写面选型拍板」节,代码 adapter/amazon):
      ①pullProducts 选型 **Reports GET_MERCHANT_LISTINGS_ALL_DATA**(Listings Items API 无枚举能力,
      报表全量快照 + saveUnifiedProduct upsert 幂等,PRODUCT 游标退化为频率控制)——SpApiReportsClient
      三步异步链(createReport/轮询 getReport 至 DONE 防御上限/getReportDocument→S3 预签名下载不走 SigV4
      →GZIP 解压)+ AmazonListingTranslator TSV 按列名解析(行按 asin1 分组,币种已按站点静态表推导收口,
      见下方站点映射条目);②pullRefunds 选型 **Finances listFinancialEvents**
      (PostedAfter/Before 记账窗 + NextToken)——SpApiFinancesClient + AmazonRefundTranslator
      (事件无原生退款 ID,组合幂等键 OrderId|Sku|PostedDate 拍板;FINISHED+REFUND_ONLY → #12 分流
      REFUNDED 终态回传契合"仅平台终态回传条件推进"设计);③uploadTracking 占位消除**规划**拍板:
      MFN = POST /orders/v0/orders/{orderId}/shipment,#11 ship 本地推进后回传、失败记 pull_log 不回滚本地
      (✅ 实现已无凭证脱机落地,见下方回传条目);单测 +17(SpApiReportsClientTest 6 + SpApiFinancesClientTest 4 + 两翻译器 7,
      假服务/官方模板推导 fixture,真凭证样本到位后 --force 校准一轮,docs/07 §8);
      ⚠️ 已知边界:报表轮询同步阻塞拉单线程(平台侧生成 15~60 分钟),接真凭证实测时长必要时异步任务化;
      ⚠️ 售后拉单 Job(AftersaleRefundPullJob)仍不接线,随真凭证(接早了对假报文产生 pull_log 失败噪音,#12 口径)
- [x] 2026-09-06 站点↔币种映射收口(**无凭证落地**,TODO(#3) 槽位消除):listing 报表无币色列,
      Sku.currency 改由适配器按 marketplaceId 静态表推导——`AmazonMarketplace` 封闭枚举 23 站点全收录
      (官方 SP-API「Store Identifiers」文档逐项核对,抓出瑞典 A2NODRKZP88ZB9/沙特 A17E79C6D8DWNP 两个易错 ID;
      真凭证到位后抽样核对即可),未配置/未收录站点拉单即报错拒静默(禁猜币种落脏账),
      且先于报表创建推导(不空耗平台侧 15~60 分钟报表生成);
      旧口径"币种置空由落库侧按店铺站点推导"作废——平台知识归防腐层,落库侧 saveUnifiedProduct 零改动;
      单测 AmazonMarketplaceTest(官方值抽查/穷举完整性 23 站守卫/未收录即拒)+
      翻译器透传断言 + AmazonClient 未收录先拒守卫,共 +6 用例
- [x] 2026-09-06 uploadTracking 脱机落地(**无凭证实现**,用户拍板提前,TODO(#3) 回传占位消除):
      MFN 确认发货 = POST /orders/v0/orders/{orderId}/shipment(`SpApiOrdersClient.confirmShipment`,
      行级发运 platformOrderItemId+quantity + 包裹详情 trackingNumber/carrierCode·carrierName 兜底/
      shipDate 必填;Jackson 树模型构建请求体防注入(运单号/承运商名为外部值禁手工拼接);
      成功 = 2xx 无响应体,异常只透状态码 docs/07 §7;入参校验链先于网络调用——平台单号/运单号/
      发货时间/承运商 code·name 至少其一/行级明细非空正数,禁半配置出请求);
      **SPI 签名收口**:`uploadTracking` 四散参 → `PlatformShipment` 命令(record+@Builder,同
      InventoryChangeCommand 拍板防相邻同类型错位)——原形态缺行级 quantity 与 shipTime,
      撑不起 confirmShipment 必填要素,趁单 adapter 窗口改签名(零存量实现迁移成本);
      PlatformGateway 回写桶限流透传同步适配;AmazonClient 守卫(缺 LWA token/未配 AWS 密钥)同拉单口径;
      单测 +7(SpApiOrdersClientTest 7→13:POST 形态与签名/请求体逐字段/承运商兜底省空字段/
      安全令牌头/错误透状态码/校验守卫/配置守卫;AmazonClientTest 占位测试改双守卫);
      ⚠️ **#11 ship 编排接线** —— ✅ 2026-09-08 已落地(erp-api ShipmentSyncService 事件驱动,
      事务提交后回传、失败记 pull_log 不回滚本地;明细行翻译经 ShopOrderApi 契约扩容字段,见 #11 条目)
- [x] 2026-09-04 OAuth 回调 + Token 刷新落地(脱机部分,erp-shop 授权中心):
      `GET /{id}/auth-url` 实装(state 加密签发)→ `GET /api/shops/oauth/callback`(SecurityConfig 放行,浏览器直跳无 JWT)
      → OAuthStateService 校验(state = AES-GCM 加密 `用途|shopId|到期ms`,复用 ERP_TOKEN_KEY,10 分钟 TTL,
      免 Redis 依赖;伪造即解密失败拒,LWA 授权码单次有效兜重放)→ exchangeToken → 复用加密链路入库
      (`applyOAuthToken`:accessToken 必写,refreshToken 空不覆盖——LWA 刷新不轮换)→ 记 tokenExpireAt + 回填 sellerId;
      Token 刷新按定案节奏实现(**过期前 10 分钟**,docs/04):收口 `getShopSession` 读取时检查,
      adapter 未接入静默跳过(对齐 #4 不产生失败噪音);跨实例防双刷新 = **DB CAS**
      (`ShopMapper.casRefreshToken`,`WHERE token_expire_at <=> 旧值` NULL 安全等值,条件更新即守卫,
      脱靶弃本次结果回库重读;LWA refresh_token 不轮换双跑无害,专用锁留二期轮换型平台,LockService javadoc 同口径);
      刷新失败抛业务异常 → Job 记 pull_log 走连续失败告警(docs/04"刷新失败告警并停该店铺拉单",
      会话装配失败从仅 warn 升级为记 pull_log,两 Job 同款);
      ShopServiceTest 11→26、OAuthStateServiceTest 3(单测共 +18)
- [x] 2026-09-04 限流落地(脱机部分,docs/04 PlatformGateway 横切层,参考 wimoor t_amz_api_timelimit):
      erp-platform-sdk 新增 `gateway` 包——`PlatformRateGuard`(Redisson **RRateLimiter** 令牌桶,
      key=`erp:ratelimit:{platform}:{bucket}:{shopId}`,窗口状态持久化 Redis 防重启丢、多实例天然合并;
      间隔配置 `erp.rate.{platform}.{bucket}-interval-ms`,缺省回落 default-interval-ms=2000;
      Redis 故障按 `erp.rate.fail-open` 降级放行——限流是保护性横切不绑架拉单;配额等待超时 fail-closed 抛 429 由拉单重试)
      + `PlatformGateway` 装饰器(数据面拉取/回写先取许可,授权面 LWA 端点直通不限流),
      `AdapterRegistry` 构造统一包装,各 adapter 对限流零感知(横切不散落);无 Redisson Bean 形态整体直通;
      sdk pom 新引 redisson(API 消费,客户端 Bean 仍由 erp-api RedissonConfig 手工装配);
      SP-API 真值以响应头 x-amzn-RateLimit-Limit 为准,随 SigV4 实调校准;
      单测 PlatformRateGuardTest 7 + PlatformGatewayTest 5(包装/直通/授权不限流)
- [x] 2026-09-04 店铺删除引用校验落地(同 #5 接口模块方案,复用 erp-contract):
      引用面 = listing 与 pull_log(erp-shop 域内直查 ShopProductService/PullLogService.countByShopIds)+
      订单与售后(跨域,新契约 ShopReferenceApi.countOrderRefs/countAftersaleRefs——售后单同样持有 shop_id,
      比原清单多了售后域);实现 ShopReferenceApiImpl 收口 erp-api,取数走 ShopOrderService/AftersaleOrderService;
      `ShopService.deleteShop` 四处引用任一存在即禁删(提示可改状态停用);ShopServiceTest +4 用例

## #4 拉单 Worker(二期第3-4周)
- [x] 2026-09-03 前置就位:pull_log 域骨架 + 游标读取 `PullLogService.findLastSuccessWindowEnd(shopId, dataType)`(无成功记录返回 null;写入侧 TODO(#4) 槽位在 PullLogService 类注释);
      shop_order / shop_order_item 域骨架已生成,对外只读查询(`/api/orders`,店铺/平台/状态过滤),无人工写接口——
      剩余工作 = OrderPullJob 调度 + `saveUnifiedOrder` 落库(ShopOrderService 类注释 TODO(#4) 有实现指引)
- [x] 2026-09-04 `saveUnifiedOrder` 落库落地(ShopOrderService):主表 `upsert` ON DUPLICATE KEY UPDATE
      (uk_shop_platform_order,docs/07 §5 禁先查后插,XML 见 erp-order mapper/ShopOrderMapper.xml;行别名 `AS new` 9.7.2 原生形态,见"SQL 兼容性红线")
      → 按 uk 反查 id → 明细先删后插(状态回传可能改行,同事务);缺省值:履约渠道 SELF_FULFILL、汇率 1(跨境缺汇率记 warn)、金额归零;
      明细 sku_id 由 erp-api 编排批量翻译(`ShopProductSkuService.mapSellerSkuToSkuId`,XML JOIN shop_product 限定店铺),未绑定 NULL 订单照常入库;
      `getById` 随单带明细(ShopOrderItemResponse,shop_product_id 内部列不对外);单测 8 个(翻译/金额/必填校验/反查兜底)
- [x] 2026-09-04 `OrderPullJob`(erp-api/job):@Scheduled fixedDelay `erp.pull.order-interval-ms`(默认 15 分钟);
      窗口 = 上次成功 window_end 左叠 5 分钟(PullConsts.WINDOW_OVERLAP_MINUTES),首次回溯 `erp.pull.first-pull-days`(默认 90,docs/04);
      防重入(2026-09-04 #13 锁选型,同日二次定版):进程内单线程调度器(SchedulingConfig,pull-sched- 前缀)
      + 店铺级 Redisson 锁 LockService.tryAcquire(一期即定型,免二期灰度迁移窗口的进程内锁静默失效;
      Redis 故障按 erp.lock.fail-open 降级);
      原手写 Redis setnx 有"超时误删他人锁(value 固定常量+无条件 delete)/无续期双跑"两坑,已退场;
      **二期多实例**:吞吐不足再议 worker 分片(分片管吞吐、锁管互斥兜底);Token 刷新 single-flight 同走 LockService
      入口 MDC traceId / finally 清(#9);seller_sku 一次批量取映射禁 N+1;pull_log 无论成败必记(含 duration_ms/pull_way,error_msg 截断 2000);
      单店失败隔离(不推游标,下轮左叠窗口自动重试);任务级单测 7 个(固定时钟测窗口/锁/隔离)
- [x] adapter 未接入期间各店自动跳过(registry miss,debug 日志;随 #3 默认不注册 Bean 一并落地)——真拉单待 #3 SP-API 实调;
      一期跑在 erp-api 进程内,量大迁 erp-worker(:8089)
- [x] 2026-09-04 连续失败 3 次告警落地(#14 站内通知):`PullLogService.shouldAlertContinuousFailure` 无状态判定
      (最近 3 次全失败且恰达阈值→true,连续段只告警一次,成功即重新计数),OrderPullJob catch 内接线,
      通知写失败只记日志不阻断拉单
- [x] 2026-09-06 前端拉单日志页落地(#16 add-page 逐域铺开,pull_log 观测面只读):
      列表 + shopId/dataType(订单·商品·售后)/success 三过滤,success 结果标签、errorMsg 失败原因列;
      菜单种子:系统管理下 id=21(perm shop:pulllog:list),回写 01_schema_init.sql(已建库直接跑新增段,重登录生效);
      ~~店铺名称列翻译随店铺 options 共享数据源另议~~(✅ 2026-09-06 收口:api/apis/shop/options.ts,
      订单/拉单日志/平台商品/售后单/发货单五页 shopId 列 enum 翻译停用店铺不参与;~~SKU 名称列翻译契约缺口挂账见 #7 专条~~
      (✅ 2026-09-06 收口,见 #7 专条))
- 模块边界演进(2026-09-04):erp-order 新增 erp-platform-sdk 依赖,**仅消费 UnifiedOrder 落库模型,禁止调平台 API**;
  后续 erp-goods(UnifiedProduct)/erp-aftersale(UnifiedRefund)落库同规约(docs/07 §2.2 已同步)

## #5 SKU 匹配(系统心脏,二期第4周)
- [x] 2026-09-03 前置就位:shop_product / shop_product_sku 域骨架已生成(listing 域对外只读,
      `/api/shop-products`、`/api/shop-product-skus`,待匹配列表 = match_status=0 过滤);
      人工绑定接口已实现 `PUT /api/shop-product-skus/{id}/bind`(回填 sku_id + match_status=2,重复绑定幂等,6 个单测)
- [x] 2026-09-04 店铺商品同步落地:`ShopProductService.saveUnifiedProduct`(唯一写入口)——主表 upsert
      (uk_shop_platform_product,XML upsert;行别名 `AS new` 9.7.2 原生形态,见"SQL 兼容性红线")+ uk 反查 id + 级联 upsert SKU 行(uk_shop_seller_sku);
      绑定字段(product_id/sku_id/match_status)永不被同步覆盖(XML 更新列不含绑定列,快照列 COALESCE);
      无 seller_sku 的平台 SKU 行不进映射表;单 SKU 商品回填 platform_sku_id,多 SKU 落 NULL;
      UnifiedProduct.Sku 演进新增 currency 字段(只加不改,docs/07 §8)
- [x] 2026-09-04 自动匹配落地(三段式,跨域编排收口 erp-api `ProductPullJob`,PRODUCT 独立游标默认 1 小时):
      ①listing 同步落库 → ②goods 侧精确匹配查询(`ProductService.mapSkuCodesToIds`,seller_sku==sku_code,in 分批 ≤1000)
      → ③`ShopProductSkuService.autoMatch` 回填 sku_id + match_status=1(**仅 sku_id 仍 NULL 的行,人工绑定 2 不被覆盖**);
      匹配不中保持 NULL + match_status=0 进待匹配列表,下轮同步/人工绑定续接
- [x] 订单行落库翻译(已在 #4 完成):saveUnifiedOrder 按 mapSellerSkuToSkuId 回填 sku_id,未绑定 NULL 订单照常入库
- [x] 2026-09-04 删除 SKU/SPU 前引用校验落地(方案拍板:接口模块,**新增 erp-contract 契约模块**):
      引用面 = shop_product_sku 绑定 / inventory / shop_order_item / purchase_order_item(采购明细同样引用 sku_id,
      比原清单多了采购域)/ SPU 另查 shop_product.product_id listing 引用;契约 = GoodsReferenceApi(引用计数)+
      GoodsSkuApi(存在性),erp-goods/erp-shop 只依赖接口,实现 GoodsReferenceApiImpl/GoodsSkuApiImpl 收口 erp-api,
      取数走各归属域 Service 新增 count 方法(ShopProductSkuService.countBoundBySkuIds /
      ShopProductService.countListingByProductIds / InventoryService.countBySkuIds /
      ShopOrderService.countItemRefsBySkuIds / PurchaseOrderService.countItemRefsBySkuIds /
      ProductService.existsSku);docs/07 §2.2 已更新;goods 首个单测 6 个(删除拦截/放行/existsSku)
- [x] bind 接口 sku_id 存在性校验(2026-09-04 同上收口):ShopProductSkuService.bind 经 GoodsSkuApi 校验,
      查无此 SKU 禁绑定(此前信任入参);ShopProductSkuServiceTest 同步(+1 用例改 2 处打桩)
- [x] 连续失败 3 次告警推通知渠道(2026-09-04 已落地,#14 站内通知,同 #4,ProductPullJob catch 内接线)
- [x] 2026-09-06 前端 SKU匹配页落地(#16 add-page 生成器逐域铺开,系统心脏人工闭环):待匹配处理台
      (init-param 固定 matchStatus=0)+ 人工绑定(prompt 录内部SKU ID,存在性校验在后端,重复绑定幂等);
      菜单种子:商品中心下 id=16 + 按钮 1601,回写 01_schema_init.sql;~~SKU 搜索下拉随 goods 域页面完善~~
      (✅ 2026-09-06 随 #16 SkuSelector 收口,bind 升级搜索选择器弹窗)
- [x] sku_code 全局唯一校验(2026-09-04 收口,TODO(#5) 槽位消除):createSku/updateSku/createProduct 前置查重
      给友好报错(updateSku 排除自身;createProduct 先拒请求内重复再逐码查库,eq 逐码而非 in 聚合——
      in 急切解析列元数据纯 Mockito 单测不可直测,SPU 下 SKU 个位数开销可忽略),并发窗口 uk_sku 兜底
      捕 DuplicateKeyException 转业务异常(同 #10 po_no 模式);ProductServiceTest +8 用例共 14 个
- [x] 2026-09-06 前端商品域基础数据两页(#16 铺开续):品牌管理 gen:page 生成(spec=goods-brand.txt,菜单 id=24;
      契约三特性——save body=Brand 本体、无 detail 端点、GET 仅 PageQuery 无业务过滤——编辑回填走行数据,
      页无搜索表单,生成器均天然兼容);分类管理手写树形页(菜单 id=23,gen:page 不适用:无分页/详情端点 +
      树形布局双踩边界)——树表格 + 新增子级/编辑/删除,编辑态禁改父级 + 前端挡子节点删除
      (TODO(#7) 后端成环校验/引用拦截补齐前的前端兜底);categoryApi 扩 CRUD,
      category.ts 过时"届时走 gen:page"注释修正

## #6 AI(三期开工 2026-09-06:地基已落地,graph/agent/前端页留待后续)
- [x] 版本定版(2026-09-06 核实 Maven Central):Spring AI 2.0.1(GA)/ AgentScope 2.0.2(GA)/ SAA 2.0.0-M1.1
      (2.0 线仅此里程碑,1.1.2.3 GA 对齐 Boot3 不可降级);升级只动根 pom 三属性
- [x] ai_* 三表落库(2026-09-06 定稿进 01_schema_init.sql,docs/03 §7 定稿;CREATE IF NOT EXISTS 幂等,已建库重跑即补建)
- [x] erp-contract 只读查询契约四件(OrderQueryApi/InventoryQueryApi/GoodsQueryApi/AftersaleQueryApi:
      过滤 record + 行视图 record + QueryPage,全 record 不引 MP 类型;实现收口 erp-api 委托各域 Service.page;
      erp-ai 取数唯一正道,铁律 2)
- [x] `tools/` 只读 @Tool 首批四类(OrderTools/InventoryTools/GoodsTools/AftersaleTools,取数走契约;
      写操作必须人工确认,铁律 7)
- [x] 2026-09-08 `tools/` 扩容三类(**Shop/Purchase/Delivery 随查询契约落地**,余量仅剩 Report——报表域未建,
      ACOS 无数据不开):
      ①契约三件(erp-contract,与查询契约五件同构:过滤 record + 行视图 record + QueryPage,全 record 不引 MP 类型):
      `ShopQueryApi`(pageShops/getShop——**凭证字段不进契约**:ShopView 只收 id/platform/shopName/sellerId/status/
      tokenExpireAt,appKey/accessToken 源头上不映射,模型零消费场景即编译期不可见,安全红线 docs/07 §7)/
      `PurchaseQueryApi`(pagePurchaseOrders/getPurchaseOrderDetail,带 PO 明细)/
      `DeliveryQueryApi`(pageDeliveries/getDeliveryDetail,带发货明细;waybillUrl 无消费场景不出契约);
      实现收口 erp-api(三 Impl 委托各域 Service,entity→record 显式逐字段映射经域 Response 中转,禁反射拷贝);
      ②\`erp-ai/tools/` 新增 `ShopTools`/`PurchaseTools`/`DeliveryTools`(一类一文件,qihang 11 类 checklist 对齐,
      只读铁律 7 + @Lazy 断环 + 分页 int 入参空值回退 0 走契约 filter 归一,同 2026-09-07 拆箱 NPE 修复口径);
      ③`ErpChatService`/`AgentService` 工具面四类 → **七类**(`ToolCallbacks.from` 本地转换,
      复刻 2026-09-07 勘误:禁注入 List<ToolCallback>,容器无该 Bean 恒空会静默丢工具);
      ④`PurchaseOrderQuery`/`PurchaseOrderService` 补过滤三条件(供应商/收货仓/状态,AI 过滤必需,
      原分页无过滤条件全量扫);
      单测 +9(erp-api 三 Impl 各 2 + erp-ai ToolsPagingDefaultsTest 三类分页默认归一 +3);
      全 reactor 18 模块 BUILD SUCCESS(erp-ai 105+ / erp-api 106+)
- [x] `ErpChatService.chat/chatStream`:ChatClient + system prompt 集中 ErpAiProperties(yml `erp.ai.system-prompt` 可覆盖);
      无 AI_API_KEY 调用友好报错、启动不炸
- [x] `ErpChatController`:POST /api/ai/chat/sessions/{id}/chat(SSE 流式)+ chat-sync 同步 + 会话新建/列表/历史
      (归属服务端强制,仅本人可见,越权统一"会话不存在");登录即可
- [x] 会话持久化:ai_chat_session(首条消息截断作 title,默认"新会话"由 renameIfDefault 回填)/ ai_chat_message
      (USER/AI 双行;同步带 usage,流式 token 取不到置 NULL;~~TOOL 中间行 Spring AI 不透出~~
      ✅ 2026-09-06 已补:**AuditingToolCallback 装饰器**包装 ToolCallback,invoke 前落 TOOL 行
      (工具名+入参 JSON 截断,erp.ai.tool-audit-max-length 可配;先落库再委托,工具执行失败也留痕,
      异常原样上抛不吞),同步/流式双通道统一生效,tokens 不适用置 NULL);userId 走 CurrentUserApi
      (AuditingToolCallbackTest 5 用例)
- [x] AI 建议闭环:ai_suggestion 三态 cas 守卫(0待确认→1已采纳/2已忽略,条件更新即守卫,同 UPDATE 回填确认人/时间)
      + POST /{id}/adopt、/{id}/ignore 动作端点 + AI 产出内部 save 唯一入口(必填校验);
      testgen-ai.txt 产 AiSuggestionStateMachineTest(守卫四类)
- [x] `graph/`:SAA Graph Core 补货建议工作流 ✅ 2026-09-06 落地(四节点链 START→collect→calculate→
      条件边(无可补项直达 END)→summarize→persist→END,图构造器装配 compile 一次持有可重复 invoke;
      **拍板偏离 TODO 原文"取数LLM"**:程序取数+程序计算、LLM 只写报告——公式确定性强/零 token
      (铁律 8 判断归 AI、执行归程序)。collect 分页扫 inventory(可用≤阈值)按 skuId 跨仓合并(可用/在途求和);
      calculate 纯公式:日均销量 = 动销窗口真实销量合计/窗口天数——✅ 2026-09-07 动销重估收口
      (旧固定 assumedDailySales 估计口径已弃,详见下方「销量数据面」条目);summarize 单次 LLM 调用产逐 SKU 摘要,
      **三重降级**(apiKey 空/调用失败/解析失败,含 ``` 围栏容错)统一落模板串 degraded=true 照跑照落库——
      模型故障不阻断建议产出;persist 经 AiSuggestionService.save 唯一入口落 ai_suggestion(type=REPLENISH,
      风险分级:可用≤0 即缺货 HIGH 否则 MID;payloadJson 存 三数量,不碰业务单据);
      触发 = POST /api/ai/replenishment/run(登录即可);定时接线 ✅ 2026-09-07 落地
      (ReplenishJob 每日 02:00 低峰,见下方「两工作流定时接线」条目);
      单测 12 个(Collect 扫描护栏+跨仓合并/Calculate 公式/Summarize 三重降级+解析/Persist 落库字段/Workflow 条件边);
      ⚠️ **victools 仲裁钉版**(根 pom,T1 当场炸出):spring-ai 2.0.1 JsonSchemaGenerator 静态引
      jsonschema-module-jackson 的 JacksonSchemaModule(仅 5.0.0 有,4.38.0 已更名),agentscope 2.0.2
      直依赖 4.38.0 参与仲裁 nearest-wins 拉低版本 → 启动即 NoClassDefFound;钉 5.0.0 保 spring-ai,
      agentscope 四期启用时若不兼容 5.0.0 再评估)
- [x] `agent/`(四期)✅ 2026-09-07 V1 落地(AgentScope 2.0.2 ReActAgent 双角色):
      **SUPPORT 客服**(tools/ 四类全量只读)/ **OPS 运营**(库存商品盘面,白名单 queryInventory/searchProducts/findSkuByCode),
      工具 = tools/ 只读 @Tool 四类经 `SpringAiAgentToolBridge` 桥接进 AgentScope Toolkit
      (AgentTool 接口:name/description/parameters(JSON schema)+callAsync;逻辑单一来源仍在 tools/,Agent 侧零复制);
      模型 = AgentScope 内建 OpenAIChatModel(官方 openai-java 协议),连接复用 spring.ai.openai.* 占位符
      (与 chat 单一来源,环境变量/local.properties 通吃);**Agent 懒装配**(无 key 启动不炸,首次调用拦截——
      构造期不建客户端);sys prompt 收口 ErpAiProperties.Agent(erp.ai.agent.* 可覆盖,maxIters=10 防死循环);
      端点(登录即可,角色大小写无关 fromPath);starter 单例自动装配不启用
      (agentscope.agent.enabled 不设,自建 Bean 零冲突);
      真调 DashScope qwen-plus ✅ 双角色 ReAct 工具闭环(客服查库存给结论/运营出库存口径盘面)
      ⚠️ **2026-09-07 勘误:上述 V1 真调"工具闭环"不成立**——验证只看了回复文本,模型在无工具可调时
      会幻觉式引用不存在的工具名(get_sku_inventory)自圆其说;V1 实际从未真调过工具(见 V1.5 勘误)。
      **V1.5 会话式多轮 ✅ 2026-09-07**(compile + erp-ai 94 测全绿):
      会话复用 ai_chat_session/ai_chat_message,**ai_chat_session 加列 source[CHAT对话/AGENT智能体] 隔离两域列表**
      (脚本/docs/03 §7/实体三方已齐;⚠️ **已建库手工 ALTER**:
      `ALTER TABLE ai_chat_session ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'CHAT' COMMENT '会话来源:CHAT智能对话/AGENT智能体(四期 agent/),前端列表按来源隔离' AFTER title;`);
      端点五件:POST+GET /api/ai/agents/{role}/sessions(新建/我的分页,source=AGENT 强制)+
      GET .../sessions/{sessionId}/messages(历史正序,归属校验同 chat)+
      POST .../sessions/{sessionId}/chat(**SSE,streamEvents 逐 TextBlockDeltaEvent 吐 delta**,帧形态同 chatStream)+
      POST .../sessions/{sessionId}/chat-sync(聚合降级);旧单轮 POST /{role}/chat 移除(契约 +4 路径,快照待刷新);
      每轮 = 归属校验→标题回填→USER 行落库→**历史重放**(USER/AI 文本行转 Msg 升序含本轮;**TOOL 行不重放**
      ——工具细节不影响连续性)→每请求新建 ReActAgent(Toolkit 桥**会话绑定版**,工具调用前落 TOOL 审计行,
      同 AuditingToolCallback 口径、写失败只记日志不阻断)→流式聚合完成落 AI 行;
      错误转可见帧同 chatStream 口径,失败轮不落 AI 行;模型改懒建单例(synchronized 双检,测试注入口保留);
      **⚠️ 联调炸出根因级 bug 并修复(2026-09-07)**:AgentService 原注入 `List<ToolCallback>`,
      **容器内无任何 ToolCallback Bean → 注入恒为空列表 → Toolkit 空 → 请求不带 tools 字段
      → 模型幻觉工具调用**(V1 与 V1.5 初版同病);修复 = 生产构造器改注入 tools/ 四类
      (ErpChatService 同款)本地 `ToolCallbacks.from` 转换,显式回调列表构造器留测试桩;
      真调复验(全新会话,OPS):queryInventory 真执行(boundedElastic 线程 6 次 inventory SELECT)
      + TOOL 审计行(queryInventory, {"skuId":1})+ 真实分仓数据(149+300=449,此前幻觉答 0)
      + 第二轮多轮记忆正确复用 449 未重查;chat-sync 同验;
      **联调方法论坑**:①工具闭环验证只认审计行/DB SQL 证据,回复文本不可信(模型会照历史幻觉有样学样,
      被污染会话里即使有真工具也不调,务必用全新会话验证);②Windows Git Bash curl 中文 body 发 GBK 必炸
      伪装"系统繁忙"500(V1 devlog 已记过,本次复犯),用 UTF-8 文件 `--data-binary @file`;
      **四项遗留 2026-09-07 拍板收口**(compile 全绿 + erp-ai 96 测全绿):
      ①跨源会话强约束:AiChatSessionService.getOwned 加 source 参数(chat 域强制 CHAT/agent 域强制 AGENT,
      归属+来源不符统一"会话不存在"不泄露存在性),ErpChatService/ErpChatController/AgentService 四调用点全量收口;
      ②role 不落会话(拍板确认现状:会话不绑 role,同会话可跨角色续聊,注释已声明);
      ③历史重放截断:erp.ai.agent.history-max-messages(默认 40 行,只重放最近 N 行 USER/AI 文本行,
      本轮提问恒在,≤0 按 1;TOOL 行仍不参与);
      ⑤SSE async dispatch Access Denied 修复:SecurityConfig `dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()`
      (过滤器默认只挂 REQUEST、JwtAuthenticationFilter 在异步派发不重跑所致,chat/agent 两域同款噪音一并修);
      单测 9(服务 6:历史重放+落库时序/流式 delta/失败帧不落 AI 行/无 key 拦截/空消息/角色白名单
      + 桥接 3:透传/委托/工具失败转结果)+ 新增 2(跨源拒绝/重放尾部截断);
      **④agent 前端页 ✅ 2026-09-07**(手写页,同构母本 ai/chat,gen:page 不适用;门禁四件全绿):
      views/ai/agent/index.vue(SUPPORT/OPS radio 切换,role 不落会话跨角色续聊,文案词表 ROLE_HINTS 收口)
      + api/interface/ai/agent.ts(AgentRole;会话/消息类型单一来源复用 ./chat)+ api/apis/ai/agent.ts(分页差异单点收口);
      **组件上提跨页公共**:views/ai/chat/components → src/components/chat(MessageList 补 emptyText/ChatInput 补
      placeholder 槽位,chat 页同步改 import);**SSE fetch 手解沉淀 src/utils/sse postSse**(chat.ts 改委托,
      鉴权/错误/帧解析单点,同铁律 8 同路径第二现沉淀);菜单 id 28('AI智能体',/ai/agent,perm ai:agent:list,
      种子已回写 01_schema_init.sql;⚠️ 存量库需手工 INSERT IGNORE);
      **chat 模板沉淀(2026-09-07 拍板落地,铁律 8)**:gen:page 增 pageType=chat——头段 pageType/chatBase({role} 占位,
      五端点快照核账守卫)/typesFrom(契约类型复用源,模板不自产会话类型)/menuSort,role= 行拍板角色与文案
      (name/empty/placeholder 必填);产三件(interface 再导出+角色词表 / apis 五函数 SSE 收口 postSse / 会话页,
      角色切换仅角色模式;无 Form 无按钮行,菜单 SQL 复用);agent 页三件已由 spec=ai-agent.txt 接管
      (--force 重生成,门禁四件全绿),冒烟 smoke-chat-spec.txt 双模式回归过;
      余量:更多角色
- [x] 库存预警规则引擎 ✅ 2026-09-06 落地(alert/ + erp-api AlertJob,V1 三规则:
      低库存(可用≤阈值聚一条,明细 topN)/ 发货超时(WAIT_SHIP 且下单超 N 小时)/
      退款异常(窗口内按店铺聚合 REFUNDED 单数达阈值);滞销/积压两规则 ✅ 2026-09-07 随销量数据面
      补齐(V1 三规则→五规则,详见下方「销量数据面」条目)。取数只走只读查询契约(铁律 2/7);分页扫全量,
      scanPageSize/scanMaxRows 护栏钳制防大表拖死;单规则失败隔离只记日志不殃及本轮其余规则;
      出口仅产 AlertEvent,推送/静默去重收口 **erp-api AlertJob**(erp-ai 不依赖 erp-system,
      模式同 OrderPullJob:每小时 fixedDelay 开关→抢锁(LockService alert:scan 效率锁,漏扫一轮无损失)→
      评估→静默期去重→#14 扇出);静默期按 notifyType 全局判(erp.alert.quiet-hours 默认 24h),
      **sys_notification 自身即"上次告警时间"存储免建去重表**(SysNotificationService.existsRecent 新增,
      告警量级走 idx_created 范围条件足够);阈值/静默期/护栏全走 erp.alert.* 配置(yml 已布默认值,
      enabled 默认 true 纯本地扫描无外呼,可置 false 灰度);时间统一注入 Clock(docs/07 §10);
      单测 15 个(AlertEngine 8 规则命中/空轮/护栏/单规则隔离 + AlertJob 6 开关/锁/静默/隔离 + existsRecent 1))
- [x] `graph/` 订单异常检测工作流 ✅ 2026-09-07 落地(docs/02 §13 两段式:规则引擎先筛 → LLM 只评可疑样本控成本,
      三节点链 scan → 条件边(无可疑单直达 END 零 LLM 成本)→ score → persist;照 ReplenishWorkflow 母本)。
      规则半边纯程序零 token:V1 四规则(2026-09-06 拍板)UNPAID_TIMEOUT(WAIT_PAY 超时未付,LOW)/
      BIG_AMOUNT(已支付且 orderAmount×exchangeRate ≥ 10000 本位币,MID,汇率缺省按 1 #4 落库口径)/
      ZERO_AMOUNT(已支付且 orderAmount ≤ 0,HIGH)/ HIGH_DISCOUNT(已支付且 discountAmount ≥ orderAmount×0.5,MID),
      规则→基线风险映射收口 AnomalyRule 枚举,同单多规则命中合并一行取 max;不含发货超时逐单版
      (AlertEngine 已有同款聚合告警避免双出口);**金额类规则一律 paidTime 判空守卫**——Amazon Pending 单
      落库金额归零,无此守卫整批误报;扫描只扫 WAIT_PAY/WAIT_SHIP 两态(待处理可干预,终态不扫防重复命中),
      分页走 OrderQueryApi 只读契约(铁律 2/7),scanPageSize/scanMaxRows 单态钳制,单态扫描失败隔离只记日志;
      时间统一注入 Clock(docs/07 §10)。LLM 半边批量单次调用 JSON 数组按 orderId 对齐,
      输入无 PII(OrderView 收件人/地址/buyer_note 不出契约);护栏 llmMaxItems=20 超限按基线风险降序截断,
      未送评单直接规则定级**不算 degraded**;三重降级(apiKey 空/调用失败/解析失败,含 ``` 围栏容错)与
      逐单漏回/词表外 riskLevel → 规则定级+模板 summary+degraded=true 照跑照落库;
      prompt 集中 ErpAiProperties.Anomaly.scorePrompt(docs/07 §9)。落库经 AiSuggestionService.save 唯一入口
      (type=ANOMALY/refType=SHOP_ORDER/refId=orderId/payloadJson={hitRules,ruleRisk,金额汇率,时间,llmScored});
      重复 run 产生新一批 = 已接受语义(同补货),去重语义 ✅ 2026-09-07 拍板(同键存在待确认建议即跳过,
      见下方「两工作流定时接线」条目);
      触发 = POST /api/ai/anomaly/run(登录即可),返回 {scannedCount,suspiciousCount,persistedCount,llmScoredCount,degraded};
      定时接线 ✅ 2026-09-07 随 ReplenishJob/AnomalyJob 落地(见下方条目);HIGH 推通知随实际告警量评估
      → TODO(#6);「同买家批量下单」
      契约无 buyer 字段(PII 不出契约)随 V2 契约扩容再上 → TODO(#6);
      单测 19 个(Scan 9 规则命中边界/paidTime 守卫/汇率缺省/合并取 max/护栏/单态隔离 +
      Score 6 三重降级/围栏对齐/词表外回落/截断不算降级 + Persist 2 + Workflow 2);
      mvn -DskipTests compile ✅ + -pl erp-ai -am test 68 全绿(新 19 + 存量 49 不回退))
- [x] 两工作流定时接线 + 去重语义 ✅ 2026-09-07 拍板落地(补货 ReplenishJob cron 默认 02:00 / 异常 AnomalyJob
      cron 默认 02:30 错峰,erp.ai.{replenish,anomaly}.cron 占位符可配(enabled 默认 true,无 key 自动降级不炸);
      **去重语义拍板:同键存在待确认(status=0)建议即跳过**——异常按 refId/补货按 skuId,收口 scan 段
      LLM 评分前省 token,旧建议被采纳/忽略后若单据仍命中允许再产出,确认闭环自然运转;
      查询 eq 全量待确认行+内存交集规避 .in() 急切解析坑(docs/07 §10),无可疑单不查库;
      调度编排在 erp-api job(模式同 AlertJob:MDC traceId→开关→LockService 抢锁 replenish:run/anomaly:run→
      跑工作流→日志摘要;效率锁语义+去重兜底,双跑无害);cron 不进 ErpAiProperties 重复建键(@Scheduled 占位符
      直读 Environment);单测 +12(AiSuggestionService 去重读侧 2 + Scan/Collect 去重各 1 + 两 Job 各 4),
      erp-ai 74 / erp-api 65 全绿)
- [x] 销量数据面 ✅ 2026-09-07 落地(表 order_sales_daily,docs/03 §7.1 定稿:支付日×SKU 合计购买数量,
      已支付态 WAIT_SHIP/SHIPPED/COMPLETED 口径,未绑定 SKU 不统计;V1 只落数量维,金额维随选品/ACOS 面再加列;
      归属 erp-order,erp-api SalesSnapshotJob 每日 01:00 窗口重算近 30 天(erp.sales.rebuild-days 可配,
      单语句原子 upsert uk_sku_date 幂等,覆盖状态回传/取消单修正;30 天外不回刷为 V1 已知边界;
      锁 sales:snapshot 效率锁);读侧 = 查询契约第五件 SalesQueryApi(erp-api Impl 委托 OrderSalesDailyService,
      sumQtyBySku 近 N 天合计,未记录 sku 调用方按 0 兜底)。**两个 TODO(#6) 槽位同日收口**:
      ①补货动销公式重估——calculate 弃固定 assumedDailySales,日均销量 = 窗口销量合计/窗口天数
      (分数速率向上取整,不在中间截断),建议量 = max(min, ceil(覆盖×日均)−可用−在途),
      **零动销死 SKU 与库存充足者剔除不再硬补**(旧公式会给零动销 SKU 每日补 minSuggestQty);
      ②预警引擎 V1 三规则→五规则:滞销(有库存但窗口内零动销,SLOW_MOVING)/
      积压(可用/日均 ≥ overstock-days 默认 90,OVERSTOCK)聚一条 topN 明细;
      notify_type 词表同步 AlertEvent ↔ 01_schema_init.sql COMMENT(已建库 COMMENT 变更可选手工 ALTER,
      仅注释无功能影响);库存快照面见下条(2026-09-08 落地,与销量面相互独立);
      ⚠️ **2026-09-08 真库炸出修复**:upsertWindow 曾因 SQL 写法与开发库引擎不符直接语法错误,
      SalesSnapshotJob 自 2026-09-07 起连日空跑、order_sales_daily 零行(单测 mock Mapper 测不出 XML,
      补货动销/滞销积压规则一直拿空销量数据)——现行派生表别名引用即 9.7.2 规范形态(单语句原子语义不变),
      真库回填 30 天窗口(17 行)并经与直查 SQL 行数/合计核对,详见"SQL 兼容性红线"节
      单测 +15(erp-order OrderSalesDailyService 3 / erp-api SalesSnapshotJob 4 + SalesQueryApiImpl 1 /
      erp-ai Calculate 重写 7 + AlertEngine +3),全 reactor 18 模块 BUILD SUCCESS(erp-ai 80 / erp-api 70)
- [x] 库存日快照数据面 ✅ 2026-09-08 落地(表 inventory_snapshot_daily,docs/03 §7.2 定稿:
      快照日×SKU×仓 存量四量(在库/占用/在途/可用),uk_sku_wh_date 幂等 upsert 同日重跑覆盖;
      **只增不可回溯**——快照取"当下存量",历史日期无法重算(要回溯需 inventory_flow 逐日反推,V2 再评估),
      故无窗口重算,与销量面"可重算窗口"是两种语义;零库存行也入快照(缺货持续天数靠它算))。
      写侧 = erp-api InventorySnapshotJob 每日 01:30 cron(erp.inventory-snapshot.enabled/cron yml 可配,默认开;
      01:00 销量之后、02:00 补货之前错峰;灰度置 false 整体停,关停期间日期数据不补;
      未接 #18 面板只走 yml,同 sales 口径;LockService inventory:snapshot 效率锁,双跑无害同库幂等;
      @Scheduled 线程自行 MDC traceId finally 清,同 AlertJob 模式)调 InventorySnapshotDailyService.snapshot
      (单语句 INSERT...SELECT 直传全量四量,ODKU 源表别名引用 9.7.2 规范形态,见"SQL 兼容性红线");
      读侧 = 只读契约第六件 InventorySnapshotQueryApi(erp-api Impl 委托 erp-inventory
      InventorySnapshotDailyService.listSeries;单 SKU 时间序,warehouseId 空=跨仓聚合 SUM 四量仓库列置 0,
      limit 空/非正回落 365、超界钳 365 防长区间拉爆;非法入参空集合不触库;全 record 不引 MP 类型)。
      单测 +11(erp-inventory Service 5:快照委托/非法入参防触库/limit 钳制三态/warehouseId 空透传 +
      erp-api Impl 2:entity→record 逐字段映射/空透传 + erp-api Job 4:开关/锁被占/Clock 当日/异常不穿透);
      真库验证 7 项全绿(scripts/validate_mapper_sql.py:建表/upsert 两遍幂等+行数合计核对/
      listSeries 单仓+跨仓形态;9.7.2 真库验证全绿);前端报表页暂不接(数据面先行,AI/报表域取数用)
- [x] 补货算法 V2((s,S) 策略+安全库存)✅ 2026-09-08 落地(#6 算法升级,面试深度样本定位——
      V1.5 均值公式升级为经典连续复查 (s,S) 策略,公式单一来源仍收口 ReplenishCalculator,采购工作流自动受益):
      相比 V1.5 两点本质升级——①**显式建模采购提前期**(lead-time-days,默认 7):补货点
      ROP=ceil(μ×LT)+SS,库存位置(可用+在途)≤ROP 才触发——均值公式只看"覆盖天数够不够",
      表达不了"提前期内会不会断货";②**需求波动进入公式**:安全库存 SS=ceil(z×σ×√LT),
      σ=窗口逐日销量样本标准差(零销日补 0),z 按服务水平档位 {0.90,0.95,0.98,0.99} 最近邻映射
      (运营只配服务水平,不暴露 z-score 概念);目标库存 S=ceil(μ×(LT+覆盖天数))+SS(覆盖天数语义不变:
      提前期之外的额外覆盖),建议量=S−库存位置,<最小建议量提到下限;低均值高波动(闪购/漏单爆款)
      场景 V1.5 会漏,V2 安全垫捕捉;零动销剔除语义保持(μ=0⇒σ=0⇒ROP=0 不触发,2026-09-07 拍板延续);
      数据面 = SalesQueryApi 契约只加方法 listDailyQtyBySku(逐日序列 Map<skuId,Map<date,qty>>,
      合计口径丢失波动信息;只回有记录 skuId,零销日调用方补零),实现链 OrderSalesDailyService
      +OrderSalesDailyMapper XML listQtySince(sku_id IN + stat_date range 走 uk_sku_date 前缀索引,
      空入参/非法窗口不触库,java.sql.Date/LocalDate 双形态防御);配置 2 键
      erp.ai.replenish.lead-time-days/service-level(ConfigConsts+AI_KEYS+01_schema_init.sql 种子+
      前端补货小节两键+幂等迁移 scripts/replenish_v2_migration.py,开发库 ALL GREEN;凭证红线不涉,
      service-level 0~1 越界回落代码默认);算法明细(μ/σ/SS/ROP/S+旧三字段键向后兼容)回填
      ReplenishItem 新增 calcJson 字段(calculate 回填,persist 透传 payloadJson 供人工判读/
      详情抽屉),空串回落旧三字段形态;单测 新增 7/适配 10+(ReplenishCalculatorTest 重写 8:
      稳定零 SS/波动安全垫/库存位置高于补货点剔除/零动销/下限兜底/单日窗口 σ 退化/z 最近邻映射/
      空入参不触库 + OrderSalesDailyService 3 + Impl 委托 1 + persist calcJson 透传 1,
      节点/工作流冒烟签名与期望值适配——均匀动销手算 25→39,采购预估金额 312.50→487.50),
      erp-ai/erp-api/erp-order 全绿;真库验证 scripts/validate_replenish_v2_sql.py 5 项 ALL GREEN
      (序列行集/零销日排除/同 SKU 日序/与 sumQtySince 合计交叉核对/真库业务行交叉核对)
- [x] 前端聊天页/AI 建议页(✅ 2026-09-06 T4 收口:AI对话页手写(会话列表+SSE 流式+首条自动建会话,
      每轮结束服务端历史回读兜底;markdown 渲染/停止生成留余量,引库需拍板)+ AI建议页 gen:page
      (readonly + adopt/ignore 动作,payloadJson 详情抽屉,动作按钮不带 v-auth 同通知中心口径);
      菜单种子 25/26/27 已回写 01_schema_init.sql,已建库整段重跑 INSERT IGNORE 即可,变更后重登生效)
- [x] SSE 帧格式真模型联调校准 ✅ 2026-09-07 全程收口(真调 DashScope qwen-plus:
      base-url/model 接线修复——yml 此前硬编码 DeepSeek 地址且只读 AI_API_KEY,用户真 key/地址在
      Windows 环境变量 OPENAI_BASE_URL/OPENAI_API_KEY 一直没被读;yml 改占位符
      OPENAI_BASE_URL/OPENAI_API_KEY:${AI_API_KEY}/AI_MODEL,缺省回落 DeepSeek;
      local.properties 补 AI_MODEL=qwen-plus。**帧形态**:Spring SSE `data:` 帧前导无空格、
      无 [DONE],前端解析零改动兼容;chunk 逐词增量 ✅、只读工具闭环 ✅(模型自发调 queryInventory,
      TOOL 审计行+AI 行落库)、错误帧兜底 ✅。联调炸出并修掉三 bug:①yml 空 mapping 登记块启动即炸
      (Boot 4 ConverterNotFound,登记块须留真键);②流式错误穿透伪装 401 未登录(转可见错误帧+失败轮不落 AI 行);
      ③四类 tools 分页参数基础类型 int 拆箱 NPE(模型不传可选参数时)——统一 Integer+空值回退 0,
      归一收口 Filter record,回归测试 4 个)
- [x] 采购建议工作流 ✅ 2026-09-08 落地(#17 落位表「智能采购建议」三期候选 V1,照 ReplenishWorkflow 母本,
      落位表拍板:建议层叠加 #10 采购域之上,只产建议进 ai_suggestion,不碰状态机与单据):
      collect(低库存扫描+补货量计算)→ aggregate(按"最新采购供应商"聚合+去重)→
      条件边(无供应商组直达 END 零 LLM 成本)→ summarize(LLM 单次调用逐供应商摘要,
      三重降级 + llm-max-items 超限按预估金额降序截断走模板)→ persist(type=PURCHASE/refType=SUPPLIER/
      refId=supplierId/skuId=NULL,组内含缺货 SKU(可用≤0)→HIGH 否则 MID)→ END。
      **共享组件抽取(逻辑单一来源,铁律 8)**:`LowStockScanner`(分页扫 InventoryQueryApi+跨仓合并)+
      `ReplenishCalculator`(建议量公式)自补货两节点抽取,补货/采购两工作流组合复用——低库存阈值/覆盖天数/
      动销窗口/最小建议量四参数同源 replenish 键(#18 热更),"缺什么缺多少"两工作流口径强一致;
      供应商映射 = `PurchaseQueryApi.findLatestSupplierBySkuIds`(契约只加方法,实现收口 erp-api 委托
      PurchaseOrderService;PurchaseOrderItemMapper XML 窗口函数 ROW_NUMBER 取每 SKU 最近一笔非 DRAFT
      采购行的 供应商/最新单价/单号/时间——草稿未定案不算历史;联查禁 Wrapper .in() 急切解析坑);
      无采购历史 SKU 不纳入建议(无法定位供应商无行动价值)计数上报 noSupplierCount;
      预估金额 = Σ(最新单价×建议量,BigDecimal 精确累加,单价缺失按 0 禁猜价);
      去重 = 同供应商存在待确认(status=0)PURCHASE 建议即跳过该组(同 2026-09-07 拍板语义,
      findPendingRefIds 复用);V1 仅手动触发 POST /api/ai/purchase/run(登录即可),**不接定时**——
      采购是人类决策节奏,定时随实际使用节奏拍板 → TODO(#17);
      配置 2 键(erp.ai.purchase.llm-max-items/summary-prompt 入 sys_config AI 组白名单+系统设置页
      CONFIG_ITEMS/PROMPT_KEYS 同步登记;scan 护栏走 yml purchase 段留真键防空 mapping 启动炸);
      词表扩容 AiConsts TYPE_PURCHASE/REF_TYPE_SUPPLIER + DDL COMMENT 同步(已建库可选手工 ALTER 仅注释);
      前端:AI 建议页类型 enum 加"采购"(success tag);单测 +28(Purchase 五测试类 15:
      Collect 透传/Aggregate 5/Summarize 5/Persist 1/Workflow 3 + LowStockScannerTest 4 +
      ReplenishCalculatorTest 6(自节点测试迁移)+ PurchaseQueryApiImplTest 2 + PurchaseOrderServiceTest 1,
      存量 ReplenishCollect/Calculate/Workflow 测试改薄委托适配);
      全 reactor 20 模块 mvn test 绿(erp-ai 125 / erp-api 108 / erp-purchase 51);vue-tsc/oxlint 绿
- [x] 文案生成工作流 ✅ 2026-09-08 落地(#17 落位表「产品描述生成」三期候选 V1,照 Purchase/Anomaly
      工作流母本,落位表拍板:listing 文案生成产出进 ai_suggestion,人工采纳后复制使用):
      collect(分页扫商品库**启用**商品——GoodsQueryApi.ProductFilter 扩 status 过滤 + ProductView/SkuView
      加 attrsJson 只加字段不改语义;去重 = 同商品存在待确认 COPYWRITING 建议即跳过(2026-09-07 拍板语义);
      材料装配逐商品隔离,品牌/类目/SKU 查询失败只跳过该商品;SKU 行截前 20 条防超变体拉爆 prompt,
      硬护栏属实现细节不入配置)→ 条件边(无待生成商品直达 END 零 LLM 成本)→
      generate(LLM 批量单次调用照 AnomalyScoreNode,JSON 数组按 productId 对齐回填
      标题/五点描述/商品描述/关键词;材料不含成本价/条码/HS 等内部字段——价格不进文案 prompt 防抄成本当售价;
      llmMaxItems(sys_config erp.ai.copy.llm-max-items 默认 10)超限按 productId 升序截断,
      截断商品下轮再生成不算降级)→ persist(type=COPYWRITING(词表既留槽位)/refType=GOODS_PRODUCT/
      refId=productId/summary=建议标题/risk 恒 LOW,payloadJson=文案四件)→ END。
      ⚠️ **降级语义与补货/异常/采购三工作流刻意不同(拍板)**:那三处建议本体是程序算的,LLM 只写报告,
      故模板兜底照落库;文案本体即 LLM 产出无模板可兜——无 key/调用失败/解析失败/逐商品漏回或必填缺失
      → 该商品跳不产出,degraded=true **零垃圾建议落库**。prompt 集中 ErpAiProperties.Copy + sys_config
      erp.ai.copy.prompt(#18 热更);触发 = POST /api/ai/copywriting/run(登录即可),V1 不接定时
      (文案采纳是人工编辑节奏,定时待拍板);V1 不自动回填平台 listing(改写随 adapter 扩容);
      附带修复:sys_config 种子补上 2026-09-08 采购两键(erp.ai.purchase.llm-max-items/summary-prompt,
      此前前端 CONFIG_ITEMS 已登记而 01_schema_init.sql 种子行遗漏,开发库已补);
      单测 +17(Collect 4:扫描装配/去重跳过/材料失败隔离/SKU 截断 + Generate 8:无 key/调用失败/
      解析失败/围栏对齐/可选字段缺省/漏回跳过/截断不算降级/空短路 + Persist 2 + Workflow 3 +
      GoodsQueryApiImpl 扩容 3),全 reactor 20 模块 mvn test 绿(erp-ai 142);vue-tsc/oxlint 绿
- [x] AI 客服 RAG V1 ✅ 2026-09-08 落地(#6 落位表「AI 客服」知识库半边,意图识别/多语言随四期):
      **向量库选型拍板(2026-09-08,结"三期开工拍板"悬案)** = Spring AI `SimpleVectorStore`
      (spring-ai-vector-store 构件,内存余弦 + JSON 文件持久化,零新基建零运维);VectorStore 接口编程,
      后续换 pgvector/Redis 只换实现类;**向量不入库**——ai_kb_chunk 存 chunk 文本作为重建正本,
      索引文件丢失/换 embedding 模型时按正本重嵌入重建(启动自动判定 + 手动端点双路径)。
      表 ai_kb_document/ai_kb_chunk(正本元数据 + 分块文本,docs/sql 定稿;开发库已建);
      切块 TokenTextSplitter builder(chunk-size 800 yml 可调,2.0.1 无参构造已废弃);
      embedding 走 openai starter 自动装配 EmbeddingModel(与 chat 同 base-url/api-key 单源,
      spring.ai.openai.embedding.options.model=${AI_EMBEDDING_MODEL:text-embedding-v3},DeepSeek 无
      /embeddings 端点须切通义等兼容服务);接入面 = 上传 .txt/.md(≤1MB)+ 粘贴文本,写侧 admin 双闸
      (@PreAuthorize hasRole('admin'),同 #18 口径——知识库是全局语料),读侧登录即可;
      **降级语义(拍板)**:无 key 接入直接拒绝;向量化调用失败 → 正本与分块留存 status=FAILED
      (文本是资产向量只是派生索引,修复后重建转 READY),不抛不回滚;检索空/未命中/异常 → prompt 原样
      零侵入,RAG 失败绝不阻断 chat。检索注入 = chat 双通道调用前向量检索 top-k(min-score 过滤,
      两键入 sys_config erp.ai.kb.retrieval-top-k/min-score #18 热更)→ 固定格式上下文拼在问题后
      进 user message(不用 Advisor 魔法显式可测;USER 审计行仍存原始问题);V1 只接 chat 不接 agent;
      删除次序拍板 = 先删向量(失败即中止)后删正本(事务),防"正本已删向量残留"脏检索;
      前端 AI 助手组新页「AI知识库」(menu id=30,手写页:上传/粘贴/分块预览抽屉/重建索引,
      系统设置页补 KB 小节 tab 并顺手补齐采购/文案 tab);配置 2 键入 sys_config AI 组 + 种子;
      单测 +26(KbVectorIndex 5 文件往返/重建换引用/坏文件空起步 + Ingest 7 + Document 7 + Search 6
      + ErpChat 注入 3),erp-ai 170 全绿、全 reactor 20 模块绿;vue-tsc/oxlint 绿;开发库已建表落种子。
      ⚠️ 实测坑(2.0.1):Mockito mock 接口 default 方法整体拦截不执行真实实现——embed(String) 不打桩
      返回 null 检索恒空;LambdaQueryWrapper.select(SFunction) 急切解析 MP 元数据纯单测炸(生产代码
      避用,同 #14 set 坑先例);TokenTextSplitter 无参构造已废弃统一 builder
- ℹ️ 版本提示(2026-09-06 回写):Spring AI 2.0.1 与 AgentScope 2.0.2 已 GA;SAA 仍为里程碑,
  graph/ 开工前再核实有无 GA;升级照旧只动根 pom 三属性

## #7 其他(随二期推进)
- [x] 时间戳两列补齐(2026-09-03 规约落地,docs/07 §6.1;脚本已改;实体侧 Brand/ProductSku/ProductCategory/SysDict/SysRole 已同步补 updatedAt;
      **开发库 2026-09-03 已执行以下 12 条 ALTER,验证 11/11 通过**;若还有其他旧库,再手工执行):
      (清单存档,新库直接重跑 01_schema_init.sql 即可)
  ```sql
  ALTER TABLE sys_role         ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;
  ALTER TABLE sys_user_role    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;
  ALTER TABLE sys_role_menu    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;
  ALTER TABLE sys_dict         ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;
  ALTER TABLE pull_log         ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;
  ALTER TABLE brand            ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;
  ALTER TABLE product_category ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;
  ALTER TABLE product_sku      ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;
  ALTER TABLE shop_order_item  ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;
  ALTER TABLE warehouse        ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;
  ALTER TABLE inventory_flow   ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;
  ALTER TABLE inventory        ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER qty_available;
  ```
- [x] 库存统一入口 `InventoryService.change(flow)`(2026-09-03):@Transactional 同事务更新 inventory + 写 inventory_flow,
      before/after 记 qty_available 轨迹,行不存在自动建行(其余数量列 0 起步),after<0 拒绝;
      ~~按 flow_type 差异化维护 qty_locked/qty_transit(发货占用/取消释放)、TRANSFER 跨仓上层组合~~
      (✅ 2026-09-06 差异化收口,**FlowOps 列语义矩阵**(InventoryService 内枚举,枚举名即字面量):
      新增 IN_TRANSIT 采购在途(审核占 +q/关闭释放 -q,仅动 qty_transit)+ LOCK_SHIP 发货占用
      (建单 +q:占用+q/可用-q;取消·删除·改单释放 -q)两 flow_type(词表三方同步 InventoryConsts ↔
      01_schema_init.sql ↔ docs/03);IN_PURCHASE 改**核销在途**(在途-q/在库+q/可用+q,守卫=在途充足,未审核即入库在此拦截);
      OUT_SHIP 改**占用转出库**(在库/占用双降,可用不变——建单时已占,守卫防未占用先发货);
      IN_RETURN/ADJUST/TRANSFER_OUT/TRANSFER_IN 保持通用形(on_hand/available 同步±,可用守卫);
      每类型一条原子 UPDATE(守卫全下 SQL),首建分支按类型收窄(通用形正数与 IN_TRANSIT 正数允许建行,
      占用/出库/核销需存量行);未知 flow_type 封闭枚举拒绝;InventoryService.transfer() 收口跨仓组合
      (两腿同事务,biz=INVENTORY_TRANSFER,暂无调拨单域直调预留);单测翻新 29 个
      (并发分支以 ADJUST 代表通用形 + 类型矩阵逐类断言);
      ⚠️ **开发库数据回补**(改造前已审核/已在途的单据无占位记录,见 #10/#11 条目内回补 SQL);
      before/after 记 qty_available 轨迹,IN_TRANSIT/OUT_SHIP 不动可用(前后相等属正常))
- [x] change() 并发安全原子化(2026-09-04 #13 锁选型同日落地,docs/07 §1① 正确性锁落 DB):
      存量行 check-then-act(selectOne→算术→updateById)重写为一条原子 UPDATE `updateAvailableDelta`
      (`SET qty_on_hand = qty_on_hand + ?, qty_available = qty_available + ? WHERE sku_id = ? AND warehouse_id = ? AND qty_available + ? >= 0`,
      余额条件进 WHERE,行锁至提交,affected=0 回查区分行不存在/余额不足);UPDATE 命中后同事务回读取 after 记流水;
      首建并发**弃 INSERT IGNORE 改捕 DuplicateKeyException 回退原子 UPDATE 重试一轮**(IGNORE 会把非重复键错误一并吞成 warning,
      与 TODO 原指引的偏差,已拍板);两轮仍冲突按业务冲突上抛;单测 11 个(新增并发首建撞 uk/回查窗口重试/持续冲突三分支)
- [x] pull_log 观测列 `duration_ms` / `pull_way`(脚本已加;开发库已生效,2026-09-03 验证)
- [x] 2026-09-06 前端库存两页落地(#16 add-page 生成器逐域铺开,均只读):库存查询 + 库存流水
      (flow_type 六值枚举与 DDL COMMENT/InventoryConsts 对齐);菜单种子:新建库存管理目录 id=17 +
      两页 18/19,回写 01_schema_init.sql;仓库列翻译已随仓库管理页收口,~~SKU 名称列翻译挂账见 #7 专条(契约缺口)~~
      (✅ 2026-09-06 随批量端点收口)
- [x] 2026-09-06 前端仓库管理页落地(#16 生成器逐域铺开,全 CRUD):库存管理目录第三页,
      菜单种子 id=20 + 按钮段 2001~2003,回写 01_schema_init.sql(全新 id,已建库直接跑新增段,重登录生效);
      同款裸 ID 一并收口:售后收退件/采购建单表单仓库手填改下拉(供应商手填同款改下拉)+ 库存两页/采购单/
      入库单/发货单列表仓库列 ID 翻译仓库名(共享数据源 api/apis/warehouse/options.ts + ProTable enum 函数形态);
      ~~SKU 明细行手填保留(SKU 搜索选择器随 goods 域页面完善)~~(✅ 2026-09-06 随 #16 SkuSelector 收口,
      采购建单明细行换搜索选择器)
- [x] 前端 SKU 名称列翻译收口(2026-09-06,后端契约补齐 + 前端一处收口):
      后端新增 `GET /api/goods/skus/batch?ids=`(ProductSkuController,出参 SkuOptionResponse{id,skuCode,productName}——
      product_sku 无名称列,SPU 名称两步组装收口 ProductService.listSkuOptions,双表组装属 Service 同 getProductDetail 口径;
      selectByIds 走 BaseMapper 内建规避 .in() 急切解析坑(docs/07 §10)且纯单测可直测;不过滤 status,禁用 SKU 历史单据仍可翻译);
      前端 `api/apis/goods/options.ts` 一处收口(模块级缓存跨页累积 + 按页去重 ids 批量取 + 串行队列防并发重复请求,
      查无 ID 记空串哨兵回落裸 ID 不重复请求);调用点 11 处裸 ID 清零:库存两页 + SKU 匹配页(ProTable #skuId 插槽 +
      request-api 包装预取)/ 订单·入库·发货·售后明细展开行 / 发货建单·编辑 / 收退件 / 建入库单表单明细行,
      label = "skuCode · SPU名称"(SkuSelector 同款);
      SkuSelector 仍走"商品keyword→SPU内SKU"两段式(批量端点按 id 翻译,不覆盖关键词搜索;全局搜索端点另议);
      单测 ProductServiceTest 14→18;api:sync 快照 71→72 路径
- [x] 逻辑删除 ✅ 2026-09-08 收口(拍板与规约定版见 docs/07 §6.4):**范围=人工域 11 表**
      (sys_user/sys_role/sys_menu/sys_dict/brand/product/product_category/product_sku/shop/warehouse/supplier,
      实体加 `@TableLogic(value="0", delval="id")` + 表加 `deleted BIGINT`;**删时置主键 id 非 0/1**,
      6 个业务唯一键重建含 deleted(uk_username/uk_role_key/uk_spu/uk_sku/uk_platform_seller/uk_name)→
      删后同键可重建且可重复删);**排除保持物理删除**:sys_config(upsert 正本)/单据三主表+明细
      (状态机守卫+改单先删后插,逻辑删堆积垃圾行)/平台同步正本/流水快照成本账/AI 域(删除=真删含向量编排)/settlement;
      XML 自定义 SQL 不自动生效——拍板 join 不滤已删(历史单据显示已删供应商名,PurchaseOrderItemMapper 唯一相关点);
      迁移 scripts/logical_delete_migration.py 幂等落开发库 ALL GREEN(复跑幂等验证过);
      codegen 同步(entity 模板注 @TableLogic,Response/SaveRequest 剔 deleted);
      单测 LogicalDeleteAnnotationTest 反射钉死注解口径(delval 漂移会静默破坏删后重建)
- [x] 商品分类管理校验补齐(2026-09-04 收口,TODO(#7) 分类槽位消除):create/update 父分类存在性校验(非根防孤儿)+
      成环校验(沿父链上走,链上出现自身即拒=自己/自己子孙禁挂;visited 集合兼防存量脏数据环死循环)+
      删除前子分类/商品引用(category_id)拦截;ProductCategoryServiceTest 12 个
- [x] WarehouseService 删除引用校验(2026-09-04 收口,同 #10 遗留"仓库删除引用校验"条目):
      契约扩容 WarehouseApi.countWarehouseRefs(库存 inventory + 采购单 purchase_order 两域合计,单 id eq 查可直测),
      实现收口 erp-api WarehouseApiImpl(新增 InventoryService/PurchaseOrderService 依赖);
      InventoryService.countByWarehouseId / PurchaseOrderService.countByWarehouseId 两域计数方法;
      `WarehouseService.delete` 任一引用即禁删(引导改状态停用);erp-warehouse pom 新引 erp-contract(零接口实现,铁律 2);
      名称唯一性不做——warehouse 无业务唯一键列,同 supplier 需业务确认后改表
- [x] 参数校验收口 ✅ 2026-09-07:spring-boot-starter-validation 已引入(2026-09-03,各业务模块+erp-api);GlobalExceptionHandler 已兜 BindException→400
      (MethodArgumentNotValidException 为其子类,Spring 7.0.7 实测继承关系不变,@RequestBody @Valid 校验失败同口出);
      Controller `@Valid` 全量收口:存货三个直连实体域(Brand/SysDict 纯配置域,实体加约束注解,**分组校验**
      ——Create 嵌套接口承载 @NotBlank 仅 create 端点 @Validated({Default, Create}) 生效,update 仅 @Valid(Default 组 @Size 对 null 放行,保住部分更新语义));
      LoginRequest/PasswordChange/PasswordReset 加 @NotBlank;角色菜单/用户角色绑定入参加 @NotNull
      (允许空列表=全量重绑清空语义,null 视为非法载荷);AftersaleHandleRequest 维持 Service 校验收口
      (reject 必填其余选填,统一注解会误伤 agree/refund/complete 选填语义);
      单测 +12(BrandValidationTest 6 分组行为/SystemRequestValidationTest 6 入参约束,纯 Validator 直测不起 Spring);
      后续新域 DTO 约束注解随 #8 规约落地时逐域启用
- [x] ~~前端工程(Vue3 + Element Plus)未创建~~ → 已立项 **#16**(2026-09-05,Geeker Admin v2 底座);接口此前已可被 Apifox/Postman 联调

## #8 API 模型收口:entity 不再直接收发 HTTP(2026-09-03 定版,docs/07 §1)
- [x] 规约定版(docs/07 §1 领域模型):出参 `XxxVO`(敏感字段不建字段=编译期封死)/ 分页入参 `XxxQuery`(继承 PageQuery)/
      写侧 `XxxSaveRequest`(id 由路径携带,创建/更新共用)+ 显式 from/toEntity 映射(禁反射拷贝,§12 反模式表已收录);
      dict/brand 纯配置域豁免;GlobalExceptionHandler 补 BindException→400(校验失败不再落 500)
- [x] erp-codegen 升级八件套(Entity/Mapper/Query/VO/SaveRequest/Service/Controller/单测),shop+inventory 双表冒烟通过(临时模块已删)
- [x] shop 域试点:ShopVO(appSecret/refreshToken 无出参字段)/ShopQuery/ShopSaveRequest(剔 id/createdAt/updatedAt/merchantId,
      platform 加 @NotBlank);掩码三态防护保留,null/blank=不改语义不变;ShopServiceTest 11 个全绿
- [x] 2026-09-03 CQRS 分包重构:平铺 model/ 与 system 的 dto/vo 统一为 request/command(写)+ request/query(读)+ response(出参),
      XxxVO 更名 XxxResponse(LoginVO→LoginResponse、ShopVO→ShopResponse);CodeGenerator 八件套/README、docs/07 §1、CLAUDE.md
      同步改版,新生成域直接产新结构
- [x] 2026-09-03 存量域翻新收官:erp-inventory / erp-warehouse / erp-system user/role/menu / erp-goods product(sku)/category 全部改走
      API 模型(request/command + request/query + response);菜单树出参 SysMenuResponse 递归转换,LoginResponse.menus 不再嵌 entity;
      brand/dict 按规约豁免(entity 直连);role/page 无过滤条件直接用 PageQuery 不建 Query 类
- [x] ~~前端工程未创建~~ → 已立项 **#16**(2026-09-05);契约切换零成本窗口期剩余部分随 #16 api:sync 快照机制收口
- [x] 2026-09-05 单测还债收口:erp-system 新增 5 测试类 45 用例——SysUserServiceTest 15(三条专用密码通道/用户名唯一/
      updateUser 物理隔绝 password + SysRoleServiceTest 5(删角色仍绑用户即拒+菜单绑定清理)+ SysMenuServiceTest 14
      (组树父不在集合按根容错/绑定先删后插 InOrder/更新禁自环/删菜单子级拦截)+ AuthServiceTest 6(用户不存在与密码错误
      同一文案防探测/禁用 403/JWT 载荷装配//me 无 token)+ JwtTokenServiceTest 5(签发解析往返/防篡改/悬挂 Base64 严格
      预校验/弱密钥启动拦截);PasswordEncoder 用真实 BCrypt 低代价轮次非打桩(docs/07 §10 AIR);
      "其余每个 Service ≥1 个"全模块盘点达成(goods 翻新域已有 14+12,brand/dict 纯配置直连豁免);
      ⚠️ JJWT `signWith(Key)` 按密钥长度自选最强算法(密钥越长可能升 HS384/HS512,签名段字符数随之变 43/64/86),测试禁硬编码段长;
      docs/07 §10 补 MP Wrapper 急切解析坑记载(#11 声称已载实际缺失,本次补齐);erp-system 单测 8→53,全模块全量绿
- [ ] 单元测试:erp-shop 20 个(CryptoServiceTest 9 + ShopServiceTest 11)✅ 已超(31+);其余每个 Service ≥1 个 ✅ 2026-09-05 收口(见上条)

## #9 链路追踪 traceId(2026-09-03 完成)
- [x] TraceIdFilter(erp-api/config,@Order 最高先于 Security 过滤链):每请求生成 16 位 hex traceId 写 MDC,
      并以 X-Trace-Id 响应头回传;上游可带 X-Trace-Id 请求头复用同一 traceId(网关/重试链路);
      finally 强制清 MDC(Tomcat 线程复用防串号);application.yml console pattern 带 [%X{traceId}]
- [x] @Scheduled 拉单线程不经 HTTP 过滤器(✅ 已随 #4 OrderPullJob 落地:任务入口自行 MDC.put traceId、finally 强清;
      拉单主日志仍以 pull_log 落表为准,traceId 是辅)

## #10 采购域(二期)✅ 2026-09-04 激活(状态机 + 入库核销 + 删除校验)
- [x] 2026-09-03 前置就位:supplier / purchase_order / purchase_order_item / purchase_inbound 四域骨架已生成
      (supplier 人工配置域全 CRUD;purchase_order_item 子表仅 entity+mapper);
      表结构与状态机枚举为草案(docs/03 §5 定稿进 01_schema_init.sql),业务确认后可调
- [x] 2026-09-04 表结构补充(add-table 流程):新增 **purchase_inbound_item** 入库明细子表
      (id/inbound_id/po_item_id/sku_id/inbound_qty,拍板 2026-09-04:入库单带明细落库,支持多次部分收货与凭证留痕;
      docs/03 §5 已登记;CREATE IF NOT EXISTS 幂等,已建库重跑 01_schema_init.sql 即补建,无 ALTER);
      erp-codegen `parts=entity,mapper` 生成子表两件
- [x] 2026-09-04 契约扩容(erp-contract,消费方新增 erp-purchase):
      ①`InventoryChangeApi.change(InventoryChangeCommand)` = 业务域动库存唯一通道(铁律 2 禁横向依赖 erp-inventory),
      实现 InventoryChangeApiImpl 收口 erp-api,翻译成 InventoryFlow 委托 InventoryService.change 唯一入口;
      调用方自持事务时 change 以 REQUIRED 加入同一事务(核销与流水同事务由调用方 @Transactional 保证);
      `InventoryConsts`(flow_type 词表)随契约走,#11 OUT_SHIP/#12 IN_RETURN 同用;
      ②`WarehouseApi.existsWarehouse`(实现 WarehouseApiImpl)防错误仓库ID经 change() 自动建行产出幻影库存;
      ⚠️ 偏差声明:erp-contract 引入 lombok(provided,仅编译期注解处理器)供契约命令模型 @Builder
      防相邻同类型参数错位(docs/07 §1 ⑤,skuId/warehouseId 双 Long 相邻),pom 注释已标
- [x] 2026-09-04 采购单状态机(PurchaseOrderService,TODO(#10) 槽位收口):
      save = 服务端置 DRAFT + totalAmount 服务端按 Σ(数量×单价,无价按 0)计算(入参剔除 status/totalAmount 防不一致脏数据)
      + 校验(供应商存在/仓库存在 WarehouseApi/SKU 存在 GoodsSkuApi、同行重复 SKU 拒、明细非空)+ po_no 撞 uk 捕
      DuplicateKeyException 友好报错 + 明细落库;audit = DRAFT→AUDITED、close = AUDITED|PARTIAL_RECEIVED|RECEIVED→CLOSED
      (均 casStatus/条件更新,WHERE 即状态机守卫,禁先查后改 docs/07 §6.3);update 仅 DRAFT(明细整体替换重算);
      详情带明细(withItems wither)、分页不带;审核/关闭接口 @PreAuthorize hasRole('admin')(erp-purchase pom 新引
      spring-security-core 仅注解,过滤链仍收口 erp-api)
- [x] 2026-09-04 入库单核销(PurchaseInboundService):save = PO 可收校验(requireReceivable:AUDITED/PARTIAL_RECEIVED)
      + 明细行校验(po_item 归属/剩余量预校验/重复行拒,sku_id 服务端按采购明细回填)+ 服务端置 PENDING +
      入库仓锁采购单收货仓(入参剔除 status/warehouseId);**confirm @Transactional** 三步同事务:
      ①PENDING→RECEIVED 条件更新占位(并发双确认/重复确认仅一个成功,失败随事务回滚)→
      ②逐行经 InventoryChangeApi 写库存流水(flow_type=IN_PURCHASE,biz=PURCHASE_INBOUND/入库单ID)→
      ③PurchaseOrderService.receiveQuantities = arrived_qty 原子累加(防超收条件 `arrived+Δ<=quantity` 进 WHERE,
      docs/07 §1 ①)+ 回读明细算推进(PARTIAL_RECEIVED/RECEIVED)+ advanceOnReceive 条件更新(与 close 并发互斥);
      任一步失败整体回滚,库存/流水/核销/状态四者强一致;cancel 仅 PENDING;update 仅 PENDING 且不许换绑采购单
- [x] 2026-09-04 删除校验:采购单仅 DRAFT 且无入库记录可删(明细同事务删,其余状态引导走关闭);入库单 RECEIVED 禁删
      (库存已动账,删单致账实无法追溯);供应商存在采购单禁删(引导改状态禁用)
- 单测 45 个(erp-purchase:PurchaseOrderServiceTest 21 状态机/金额汇总/删除校验/核销回写 + PurchaseInboundServiceTest 18
  建单校验/confirm 守卫与链路/取消删除 + SupplierServiceTest 6);erp-api 新增 InventoryChangeApiImplTest/WarehouseApiImplTest;
  ⚠️ MP 3.5.17 BaseMapper.insert/updateById 有 Collection 重载,mockito any() 需类型化 any(Entity.class)
- [x] ~~遗留:supplier 名称唯一性~~ ✅ 2026-09-07 拍板收口(表加 uk_name + Service 友好查重:
      save/update 同名上抛"供应商名称已存在",update 查重排除自身,部分更新 name=null 跳过校验;
      并发窗口漏网由 uk 兜底;docs/03 supplier 段同步 UNIQUE 标注;单测 +3);
      **已建库环境需手工执行**(先清重再加键,否则存量重名会让 ALTER 失败):
      ```sql
      SELECT name, COUNT(*) c FROM supplier GROUP BY name HAVING c > 1;  -- 有结果先人工合并
      ALTER TABLE supplier ADD UNIQUE KEY uk_name (name);
      ```
      / ~~仓库删除引用校验~~(✅ 2026-09-04 已随 #7 收口,
      WarehouseApi 扩 countWarehouseRefs)/ ~~单据 createdBy 接 SecurityContext~~(✅ 2026-09-06 收口:契约新增
      CurrentUserApi 实现收口 erp-api 走 AuthContext,采购单/入库单 save 服务端回填,SaveRequest 剔除 createdBy 入参;
      "确认人"无落库列,随需求演进另立项)/ ~~采购在途 qty_transit 维护随 #7 change() 按 flow_type 差异化~~
      (✅ 2026-09-06 收口:audit 升级复合事务动作——cas DRAFT→AUDITED 占位 + 逐行 IN_TRANSIT 正数占在途
      (biz=PURCHASE_ORDER);close 升级复合——cas→CLOSED + 逐行释放未到货在途(quantity-arrived>0 的行负数,
      已收齐行跳过);confirm 的 IN_PURCHASE 由 change() 新矩阵承接核销在途(守卫=在途充足);
      ⚠️ **开发库回补 SQL**(改造前已审核未收齐的采购单无在途占位,不回补则入库核销/关闭释放在途报"在途库存不足",
      执行前备份,幂等性靠 ODKU 累加保证):
      ```sql
      INSERT INTO inventory (sku_id, warehouse_id, qty_on_hand, qty_locked, qty_transit, qty_available)
      SELECT i.sku_id, po.warehouse_id, 0, 0, SUM(i.quantity - i.arrived_qty), 0
      FROM purchase_order_item i
      JOIN purchase_order po ON po.id = i.po_id
      WHERE po.status IN ('AUDITED','PARTIAL_RECEIVED')
      GROUP BY i.sku_id, po.warehouse_id
      HAVING SUM(i.quantity - i.arrived_qty) > 0
      ON DUPLICATE KEY UPDATE qty_transit = qty_transit + VALUES(qty_transit);
      ```
- [x] 2026-09-05 前端三页落地(#16 生成器逐域铺开,add-page 流程):supplier 全 CRUD 热身 / 采购单 列表+建单表单
      (明细行编辑人工槽,单价金额 string 红线)+ audit/close(按钮 permKey purchase:order:audit/close,后端
      @PreAuthorize hasRole('admin') 双闸,按钮按状态机裁剪显示)/ 入库单 列表+confirm/cancel(仅 PENDING)+
      收货明细展开行(懒加载详情 items;**建入库单表单不在本期射程**,后端接口已备,页 spec 注明人工扩展);
      菜单种子:采购管理目录 id=10 + 三页 11/12/13 + 按钮段 1101~1103/1201~1205/1301~1302(menuId*100+n 新段),
      回写 01_schema_init.sql(全新 id,已建库直接跑新增段即可,重登录生效);嵌套明细 purchasePrice 生成器不标
      money,人工按 docs/09 §6 收 string(重生成 --force 前注意 diff)
- [x] 2026-09-06 建入库单表单落地(#10 建单槽位收口,后端接口已备):新建入库单弹窗(选 AUDITED/PARTIAL_RECEIVED
      采购单[Query 无状态过滤参数,取前 100 单客户端裁剪]→ 拉明细剩余行[已收满行不列,预填全收可改]逐行录收货量)
      → 新增 PENDING;入库仓 = 采购单收货仓服务端定(前端只读展示),超收预校验在后端;
      按钮 1303 purchase:inbound:add,回写 01_schema_init.sql(已建库直接跑新增段)

## #11 发货域(二期)✅ 2026-09-04 激活(拍板:3列+子表 / 全部发足才 SHIPPED / 未绑定行不参与)
- [x] 2026-09-03 前置就位:delivery_order 域骨架已生成(人工建单全 CRUD,运单号唯一、可多条 NULL)
- [x] 2026-09-04 表结构补充(add-table 流程,docs/03 §5 已登记):
      ①delivery_order 加 3 列 warehouse_id(出库仓,扣库存必需)/ship_by_time(承诺发货时限,docs/03 L152 预设)/
      created_by(对齐 purchase_inbound,接 SecurityContext 随前端工程);
      ②新建 **delivery_order_item** 子表(delivery_id/order_item_id/sku_id 冗余/ship_qty)——发货进度事实源
      (**不可**存 shop_order_item:拉单 replaceItems 先删后插会冲掉);仅 sku_id 已绑定的订单行参与发货与发足判定。
      ⚠️ 已建库环境需手工执行以下 ALTER(新库重跑 01_schema_init.sql 即可):
      ```sql
      ALTER TABLE delivery_order ADD COLUMN warehouse_id BIGINT NOT NULL DEFAULT 0 COMMENT '出库仓ID(warehouse.id,发货指定仓,ship 时从该仓扣库存;#11 激活加列 2026-09-04)' AFTER shop_id;
      ALTER TABLE delivery_order ADD COLUMN ship_by_time DATETIME NULL COMMENT '承诺发货时限(平台侧快照,国内平台考核;#11 激活加列 2026-09-04)' AFTER status;
      ALTER TABLE delivery_order ADD COLUMN created_by BIGINT NULL COMMENT '创建人(sys_user.id,接 SecurityContext 随前端工程;#11 激活加列 2026-09-04)' AFTER shipped_at;
      ALTER TABLE delivery_order ADD KEY idx_warehouse (warehouse_id);
      ```
      (delivery_order_item 为 CREATE IF NOT EXISTS 幂等,重跑脚本即建;存量 warehouse_id=0 的行需人工归仓)
      erp-codegen `parts=entity,mapper` 生成子表两件
- [x] 2026-09-04 契约扩容(erp-contract 新增 ShopOrderApi,消费方 erp-fulfill):
      ①`findDeliveryView(orderId)` = 订单发货视图(shopId/orderStatus/fulfillmentChannel/items),
      items 仅含 sku_id 已绑定的订单明细行(未绑定行不参与发货与发足判定,2026-09-04 拍板);
      ②`casOrderStatus(orderId, from, to)` = 订单状态条件推进(WHERE order_status=from 即守卫,XML 在 ShopOrderMapper),
      "本系统操作"推进订单状态的唯一出口(docs/04:状态只允许拉单同步与本系统操作两条路径);
      实现 ShopOrderApiImpl 收口 erp-api,取数走 ShopOrderService.getById/casOrderStatus;
      OrderDeliveryView record+@Builder(嵌套 Item,防相邻同类型参数错位,同 InventoryChangeCommand)
- [x] 2026-09-04 发货单状态机(DeliveryOrderService,TODO(#11) 槽位收口,同 #10 先例):
      PENDING→ship→SHIPPED→deliver→DELIVERED;cancel 仅 PENDING;update/delete 仅 PENDING
      (SHIPPED/DELIVERED 库存已动账禁删;update 不许换绑订单,明细整体替换);
      save = 订单可发校验(存在/WAIT_SHIP/SELF_FULFILL——FBA/海外仓平台履约不产生系统发货单,docs/03)
      + 出库仓存在(WarehouseApi,防幻影库存同 #10)+ 明细行校验(归属/sku_id 已绑定/同行重复拒/剩余量预校验:
      Σ 非 CANCELLED 发货明细(PENDING 在途+已发)≤ order_item.quantity,逐单 eq 聚合可单测)
      + 服务端置 PENDING/shop_id 按订单回填/sku_id 服务端回填 + 单号/运单号撞 uk 捕 DuplicateKeyException 友好报错;
      DeliveryConsts(状态词表/BIZ_TYPE_DELIVERY_ORDER/订单侧字面量)收口本模块(同 PurchaseConsts)
- [x] 2026-09-04 确认发货 ship @Transactional(同 #10 confirm 三步+发足推进):
      ①PENDING→SHIPPED 条件更新占位(并发双确认/重复确认仅一个成功,失败随事务回滚)→
      ②逐行经 InventoryChangeApi 写库存流水(flow_type=OUT_SHIP 数量为负,biz=DELIVERY_ORDER/发货单ID)→
      ③回写 shipped_at → ④发足判定:按 order_item_id 聚合该订单全部非 CANCELLED 发货明细,
      全部已绑定订单行发足才 casOrderStatus(WAIT_SHIP→SHIPPED),部分发货保持 WAIT_SHIP 不报错
      (未命中不推进属正常:部分发货/订单已被拉单推进);任一步失败整体回滚,
      库存/流水/发货单状态/订单状态强一致;Controller 新增 POST /{id}/ship|cancel|deliver(运营接口不限 admin)
- [x] 2026-09-04 未绑定 SKU 行策略(拍板):不参与发货与发足判定,不进发货单;补绑后补发机制待 SKU 匹配完善后评估
- 单测 23 个(DeliveryOrderServiceTest 全翻新:建单校验链 8 分支/ship 守卫与出库动账/发足推进 vs 部分发货/
  cancel·deliver·delete·update 守卫);⚠️ MP .in() 急切解析坑再次验证:占用聚合逐单 eq 查规避(docs/07 §10 已有记载)
- [x] 2026-09-06 前端发货单页落地(#16 add-page 生成器逐域铺开):列表 + ship/deliver/cancel 动作
      (按钮按状态机裁剪,发货弹窗注明扣库存不可回退)+ 发货明细展开行(懒加载详情 items);
      菜单种子:订单中心下 id=14 + 按钮段 1401~1403,回写 01_schema_init.sql(全新 id,已建库直接跑新增段,重登录生效);
      建发货单表单(选 WAIT_SHIP+SELF_FULFILL 订单 → 仅 sku_id 已绑定行 → 逐行 ship_qty)不在本期射程,spec 注明人工扩展
- [x] 2026-09-06 建发货单表单落地(#11 建单槽位收口,后端接口已备):新建发货单弹窗(选 WAIT_SHIP 订单
      [orderStatus 服务端过滤 + 客户端裁 SELF_FULFILL,前 100 单]→ 订单明细仅 sku_id 已绑定行逐行预填全量可改)
      → 新增 PENDING;出库仓必选(动账发生在 ship,建单不动账——~~旧口径~~,同日被下方占用模型条目升级取代);
      type 固定 SELF_FULFILL;跨发货单累计超发预校验留后端;物流公司/运单号选填可后补(update 仅 PENDING);
      按钮 1404 fulfill:delivery:add,回写 01_schema_init.sql(已建库直接跑新增段)
- [x] 2026-09-06 发货单占用模型落地(#7 change() 差异化同日收口,**拍板升级:建单即占库存**,
      取代 2026-09-04"动账发生在 ship,建单不动账"旧口径——qty_locked 列语义"占用(已分配未发货)"本就预设
      建单分配,且旧模型 PENDING 在途单不持有库存、缺货要到 ship 才暴露):
      save 落单后逐行 LOCK_SHIP 占用(可用不足建单即拦,取消"建单成功、发货才缺货"的滞后暴露);
      cancel 升级复合(cas→CANCELLED + 释放占用);delete(PENDING)先 cas 占位防 ship 竞态再释放
      (CANCELLED 单占用已在取消时释放,删除不重复释放);update 升级行锁读(selectByIdForUpdate,FOR UPDATE
      串行化与 ship 的 check-then-act 竞态,防"改单释放了已发货单据的占用"错账)+ 释放旧占用(旧仓旧明细)
      → 替换 → 重占新占用(新仓新明细);ship 的 OUT_SHIP 语义转占用核销(在库/占用双降,可用不变);
      附带收益:并发建单超发窗口被占用守卫闭合(两单同抢同行库存,后到者占用失败回滚);
      前端建单表单文案同步("建单即占用;确认发货核销出库;取消/删除自动释放");
      单测翻新 27 个(建单占用/取消释放/删除释放与 CANCELLED 不重复释放/改单释放重占/行锁拒已发货);
      ⚠️ **开发库回补 SQL**(改造前创建、仍 PENDING 的发货单无占用记录,不回补则 ship 报"出库占用不足",
      可用不足的行即历史超发,人工核账后处理):
      ```sql
      -- 行已存在:累加占用
      UPDATE inventory inv JOIN (
          SELECT d.warehouse_id, i.sku_id, SUM(i.ship_qty) lock_qty
          FROM delivery_order d JOIN delivery_order_item i ON i.delivery_id = d.id
          WHERE d.status = 'PENDING'
          GROUP BY d.warehouse_id, i.sku_id
      ) t ON inv.sku_id = t.sku_id AND inv.warehouse_id = t.warehouse_id
      SET inv.qty_locked = inv.qty_locked + t.lock_qty, inv.qty_available = inv.qty_available - t.lock_qty
      WHERE inv.qty_available >= t.lock_qty;
      -- 行不存在:首建(报错改用 SELECT 先核对其库存行缺失场景)
      INSERT INTO inventory (sku_id, warehouse_id, qty_on_hand, qty_locked, qty_transit, qty_available)
      SELECT t.sku_id, t.warehouse_id, 0, t.lock_qty, 0, -t.lock_qty
      FROM (
          SELECT d.warehouse_id, i.sku_id, SUM(i.ship_qty) lock_qty
          FROM delivery_order d JOIN delivery_order_item i ON i.delivery_id = d.id
          WHERE d.status = 'PENDING'
          GROUP BY d.warehouse_id, i.sku_id
      ) t
      ON DUPLICATE KEY UPDATE qty_locked = qty_locked + VALUES(qty_locked),
                              qty_available = qty_available - VALUES(qty_available);
      ```
      (第二条与第一条同跑会双重累加——二选一:行齐全跑第一条,含缺行场景只跑第二条 ODKU 版)
- [x] 2026-09-06 前端订单明细展开行 + 发货单编辑表单落地(#16 人工槽收尾):
      ①订单页 expand 懒加载详情 items(OrderItems,明细行自带 productName/platformSku 平台侧快照,
      ~~内部SKU 列翻译仍挂 #7 专条~~ ✅ 2026-09-06 随批量端点收口);②发货单页编辑弹窗(DeliveryEditForm,仅 PENDING;射程=后补/修正
      物流公司与运单号,仓库/明细只读——改仓改量走"取消后重建",前端不放开占用重算入口;
      后端 update 全量 SaveRequest 明细整体替换,释放旧占→替换→重占由后端事务收口);
      按钮 1405 fulfill:delivery:edit,回写 01_schema_init.sql(已建库直接跑新增段);
      ⚠️ 顺手修 1404 漏配:sys_role_menu 缺 (1,1404) 授权行,admin 看不到"新建发货单"按钮,已补
- [x] 2026-09-08 发货回传平台编排接线(**电子面单/运单号回传平台遗留项收口**,adapter 侧 2026-09-06 已脱机落地):
      编排收口 erp-api `ShipmentSyncService`(事件驱动,**事务提交后才回传**):
      ①erp-common 新增 `DeliveryShippedEvent`(erp-fulfill 在 ship 事务内发布,erp-api 以
      `@TransactionalEventListener(AFTER_COMMIT)` 消费——事件只进 erp-common,发布方与监听方互不依赖,
      同 #18 SystemConfigChangedEvent 先例;erp-fulfill 无 ShopSession/AdapterRegistry,回传只可能落 erp-api);
      ②跳过 vs 失败分清楚(噪音纪律同拉单 Job):**跳过**=开关关闭(`erp.shipment.sync-enabled` 默认 true)/
      adapter 未接入/非卖家自履约(FBA·海外仓平台自履约)/运单号未填(要素未齐)/单据非已发货状态——不记 pull_log;
      **失败**=会话装配失败/订单不存在/缺平台订单号/**缺任一发货行 platformOrderItemId(禁静默丢行,
      半回传比不回传更难对账)**/平台调用抛错——记 pull_log(`DATA_TYPE_SHIPMENT`+`PULL_WAY_EVENT` 新常量,
      窗口退化为本次时刻,pulled_count 恒 1;复用 pull_log 而非另建表:排障入口与连续失败告警同一套,禁提前抽象)
      + 连续 3 次失败推站内告警(同 #14 扇出);
      ③命令装配:发货明细行 order_item_id → platform_order_item_id 翻译走 ShopOrderApi 契约
      (**契约扩容 2026-09-08:OrderDeliveryView 加 platformOrderId、Item 加 platformOrderItemId,只加字段不改语义**),
      carrierCode 无平台映射表故置空、carrierName 取 logisticsCompany 由 adapter 兜底(编外承运商),
      shipTime = shippedAt(Asia/Shanghai → Instant);
      ④**失败不回滚本地发货**(docs/04 拍板):AFTER_COMMIT 相位 + 监听器吞异常,ship 返回不受回传影响;
      单测 +16(erp-api ShipmentSyncServiceTest 14:五跳过分支/命令逐字段/五失败分支/告警阈值/事件入口吞异常/开关关闭;
      erp-fulfill DeliveryOrderServiceTest +2:ship 发事件、ship 被拒不发);全 reactor 18 模块 BUILD SUCCESS;
      ⚠️ **已知边界**:回传在 ship 请求线程内同步执行(AFTER_COMMIT 后、Controller 返回前),平台慢/超时会拖长
      "确认发货"响应——V1 接受(失败只记 pull_log 不影响正确性),真凭证联调实测耗时后再评估异步化
      (需独立 executor,禁复用 pullScheduler 单线程池)
- [ ] 遗留:~~签收回传平台物流轨迹~~(✅ 2026-09-08 拍板**恒不做**:Amazon SP-API 无卖家侧轨迹/签收回传
      API——承运商扫描由平台自拉,卖家义务止于运单号回传(uploadTracking 已落地,见上条);签收回传/轨迹
      回传是国内平台结算侧概念,随首个国内 adapter 与电子面单取号 fetchWaybill 一并开题,不独立挂账)/
      ~~电子面单/运单号回传平台~~(✅ 2026-09-08 编排接线落地,见上条;电子面单取号 fetchWaybill 跨境平台恒不支持,
      国内平台随首个国内 adapter 落地)/
      ~~createdBy 接 SecurityContext~~(✅ 2026-09-06 已随 #10 同款收口:CurrentUserApi,发货单 save 服务端回填)/
      ~~并发建单超发窗口~~(✅ 2026-09-06 随占用模型闭合:并发建单在占用动账处被 inventory 行锁 + 可用守卫串行化,
      后到者占用失败整体回滚)/ 发货单类型 FBA/OVERSEAS 的供应商代发与海外仓发货流程待业务确认后细化

## #12 售后域(二期)
- [x] 2026-09-03 前置就位:aftersale_order 域骨架已生成(readOnly=true 系统写入表,对外只读查询,写入口留 TODO)
- [x] 2026-09-05 平台售后同步 upsert 落地(脱机部分,TODO(#12) 槽位收口,同 #4 saveUnifiedOrder 套路):
      `AftersaleOrderService.saveUnifiedRefund` 唯一写入口——幂等靠 uk_shop_platform_refund upsert 冲突即更新
      (XML upsert,AftersaleOrderMapper.xml 首建;行别名 `AS new` 9.7.2 原生形态,见"SQL 兼容性红线");aftersale_no=platformRefundId(uk_aftersale_no 防御兜底);
      **order_id 经契约跨域翻译**:ShopOrderApi 扩 `findIdByPlatformOrderId`(实现收口 erp-api,取数走 ShopOrderService),
      关联订单未入库(售后先于订单拉到)跳过返回 false 等下轮窗口重拉,不抛异常断整批;
      **状态映射拍板**:平台状态只做首插初始映射(APPLYING→PENDING/WAIT_RECEIVE→RETURNING/FINISHED 按类型分流
      退款类→REFUNDED、换货补发类→COMPLETED/REJECTED、CANCELLED 直落),人工状态机是处理主线——
      已存在单仅平台终态回传(REJECTED/CANCELLED,CANCELLED 即预留的平台撤单出口)条件推进未决态单(PENDING/APPROVED/RETURNING),
      其余只刷金额/币种/原因快照字段,人工已决与终态不被平台回传回退;
      类型枚举封闭穷举映射(编译器保证不漏分支);reason 缺失用 description 兜底、截 512 对齐列宽;
      erp-aftersale pom 新引 erp-platform-sdk(仅消费 UnifiedRefund 落库模型,禁调平台 API,同 erp-order #4 规约);
      平台申明明细 items 不落库(aftersale_return_item 是人工实收凭证非平台申明;raw_json 列随 #3 接线 pullRefunds 时评估);
      售后拉单 Job 接线随 #3 真凭证(pullRefunds 现为占位,不接避免 UnsupportedOperationException 噪音);
      单测 +6(saveUnifiedRefund 6:必填守卫/订单未入库跳过/首插映射全字段/FINISHED 四类型分流/其余状态映射/reason 兜底截断)
      + ShopOrderApiImplTest 1(契约翻译转发),全模块 mvn test 绿
- [x] 2026-09-04 售后状态机与人工处理入口(拍板:草案 7 态扩 8 态,新增 RETURN_RECEIVED已收退件;退款/收退件两步独立可追溯):
      agree(PENDING→APPROVED 仅退款/补发 | RETURNING 退货退款/换货,按 type 分流,未知 type 拒)/
      reject(PENDING→REJECTED,result 必填)/ receive-return(RETURNING→RETURN_RECEIVED)/
      refund(APPROVED|RETURN_RECEIVED→REFUNDED,前置白名单即类型血缘,退货类强制已收退件)/
      complete(REFUNDED→COMPLETED 终态收尾);五动作均 `POST /api/aftersale/orders/{id}/...`,
      条件更新即守卫(AftersaleOrderMapper.casStatus/refundOrder,WHERE 即前置态,affected=0 即拒),
      同一 UPDATE 原子回填 result;单步流转不加事务(#10 audit 先例);处理接口不限 admin(对齐 #11 发货先例);
      Query 补 shopId/status/type/orderId 过滤(idx_status/idx_order 预置);CANCELLED 预留平台同步撤单,无人工入口;
      词表收口 AftersaleConsts(与 SQL COMMENT/条件更新 SQL 三方同步);单测 12 个(分流/守卫/未知类型/result 必填)
- [x] 2026-09-04 退货入库落地(动账经 InventoryChangeApi→InventoryService.change 唯一入口,拍板:退货明细=receive-return
      人工录入实收(可≠平台申明,对齐采购入库实收模式)/ 升级 receive-return 复合事务动作 / 全套校验):
      表结构 = ①aftersale_order 加 warehouse_id ②新建 aftersale_return_item(id/aftersale_id/order_item_id/sku_id/return_qty,
      实收明细即动账凭证,erp-codegen parts=entity,mapper 两件);子表 CREATE IF NOT EXISTS 幂等,重跑脚本即建;
      **已建库环境需手工执行**:
      ```sql
      ALTER TABLE aftersale_order ADD COLUMN warehouse_id BIGINT NULL COMMENT '退货入库仓ID(warehouse.id,收退件时必填回填;#12 激活加列 2026-09-04)' AFTER order_id;
      ```
      (售后单来源=平台同步尚未跑,存量表无数据,无归仓问题);
      实现 = receiveReturn @Transactional(#10 confirm/#11 ship 先例):①校验链(仓存在 WarehouseApi 防幻影库存/
      明细非空+数量正数/归属复用 ShopOrderApi.findDeliveryView 仅 sku_id 已绑定行可退,sku_id 服务端按订单行回填不入参/
      数量预校验:该订单行历史退货累计+本次≤订单行数量,跨售后单聚合 AftersaleReturnItemMapper.listByOrderId join)→
      ②占位 AftersaleOrderMapper.receiveReturn(RETURNING→RETURN_RECEIVED 同 UPDATE 回填 warehouse_id,COALESCE result,
      并发双收退件仅一个成功)→ ③逐行 InventoryChangeApi.change(IN_RETURN 正数,biz=AFTERSALE_ORDER/售后单ID)→
      ④aftersale_return_item insert;失败整体回滚,库存/流水/状态/明细强一致;
      详情 getById withReturnItems 带退货明细,分页不带;Controller receive-return 入参换 AftersaleReturnReceiveRequest(@Valid);
      erp-aftersale pom 新引 erp-contract;超退上限按订单行数量放宽不误拦(按发货量收紧精确口径留 TODO(#12) 在 Service javadoc,
      随发货域数据完善后评估);testgen spec receiveReturn 行删除(复合动作出射程,同 ship/confirm);
      单测 17 个(状态机 11 + 收退件复合 6:动账 captor 逐字段/校验链全分支/占位脱靶无副作用)
- [x] 2026-09-06 前端售后单页落地(#16 add-page 生成器逐域铺开):列表 + 五动作按状态机裁剪
      (agree/reject(PENDING)·receive-return(RETURNING)·refund(APPROVED|RETURN_RECEIVED)·complete(REFUNDED);
      reject 走 prompt 录拒绝原因 result 必填)+ 收退件复合表单(仓库ID + 拉订单明细枚举仅 sku_id 已绑定行逐行录实收,
      超退预校验留在后端)+ 退货明细展开行(懒加载详情 returnItems);
      菜单种子:订单中心下 id=15 + 按钮段 1501~1505,回写 01_schema_init.sql;~~仓库改下拉随 warehouse 页建设另议~~
      (✅ 2026-09-06 已随仓库管理页收口,收退件表单下拉选仓)
- [x] 退款金额与财务勾稽(2026-09-08 随 #19④ 退款勾稽收口,#12 槽位关闭)
- ⚠️ 已建库手工 ALTER(#12 状态机注释定版,仅注释无数据变更):
      `ALTER TABLE aftersale_order MODIFY COLUMN status VARCHAR(32) NOT NULL COMMENT 'PENDING待处理/APPROVED已同意/RETURNING待收退件/RETURN_RECEIVED已收退件/REFUNDED已退款/COMPLETED已完成/REJECTED已拒绝/CANCELLED已取消(#12 状态机 2026-09-04 定版)';`

## #13 模型可变性分级:record+@Builder 落地(2026-09-04,docs/07 §1)
> 背景:评审"业务层坚决不用 set/Entity 按 JPA 保留 setter/DTO 能省则省"提案后定版——方向采纳、结论修正:
> 本项目 ORM 是 MyBatis-Plus 非 JPA(MP 对 getter/setter 依赖更重);"DTO 能不用就不用"与 §1 API 模型收口冲突不采纳;
> 落地为五档分级(Entity 例外/Query 继承例外/Response record/SaveRequest record/装配 builder 化),setter 白名单封口。
- [x] 规约定版:docs/07 §1 新增「模型可变性分级」+ §12 反模式表补两行(长串 set 装配 / record 敏感 toString)
- [x] erp-codegen 模板改版:Response/SaveRequest 产 `record + @Builder`(from 用 builder 命名传参防相邻同类型字段错位),
      测试模板改 builder 构造;Query/Entity 模板加豁免注释;README 同步;purchase_order_item 双件冒烟 + 编译验证通过
- [x] 存量翻新(批量脚本 + 特例手工,32 文件):20 个 Response + 12 个 SaveRequest → record+@Builder;
      `ShopOrderResponse.withItems`/`LoginResponse.withToken` wither 承接"构造后补字段";
      `SysMenuResponse.from` 条件装配收进 builder 链;CategoryNode(分类树节点)record 化;
      **敏感 toString 手写脱敏**:ShopSaveRequest(凭证三字段)、SysUserSaveRequest(password)——存量
      LoginRequest/PasswordChangeRequest/PasswordResetRequest 已自带,核实无需改
- [x] 装配点 Builder 化:UnifiedOrder/UnifiedProduct/UnifiedRefund(含嵌套 Item/Sku)与 PullLog 加 @Builder
      (@Data+双构造保无参构造,MP/存量测试零破坏);AmazonOrderTranslator、PullLogService、AuthService.assemble 改 builder 链;
      ShopOrderService.getById 改 withItems;InventoryService 读改写/ShopService 加密等白名单 set 不动;
      InventoryFlow 补 @Builder(调用方构造入参,同 PullLog 同款);
      Inventory 补 @Builder + change() 建行/读改写拆两分支(2026-09-04 追加):新行 builder 一次成型
      (qtyOnHand=首笔、qtyAvailable=after,与原"全 0 建行后共路径累加"逐值等价,建行/读改写两分支各有测试值断言),
      存量行保留 setter 读改写——@Builder 只落纯构造位,不碰读改写生命周期
- [x] Entity 默认 @Builder 收口(2026-09-04 二次追加):从"装配点 ≥3 才补"改为**全量 24 个 entity 默认
      @Data+@Builder+双构造**(消除"要不要加"的决策点,注解零成本;加是默认的,用是场景判断——builder 只许纯构造位,
      读改写/写前回填仍 setter 白名单);codegen ENTITY_TEMPLATE 同步产四注解、SAVE_REQUEST 的 toEntity 产 builder 链
      (存量 toEntity set 链合法,随域翻新演进);docs/07 §1 ①⑤ 更新
- [x] 测试同步 + 全量绿:各 ServiceTest 的 `new SaveRequest()+set` 改 builder,Response 断言改 record 访问器;
      `mvn test` 13 模块全部 SUCCESS
- [x] 2026-09-04 锁选型二次定版(全系统复审拍板):拉单防重入 ReentrantLock → **Redisson 一期即定型**——
      Redis 本就在运行栈(starter-data-redis 已接),且二期多实例/灰度期进程内锁会**静默失效**(不报错即防不住,
      重复拉单撞平台限流风控),迁移窗口恰是系统最脆弱时刻;落地 = 根 pom 登记 redisson 4.7.0(核心包,不用 starter——
      Boot 4 线 autoconfig 版本耦合)+ `RedissonConfig` 手工装配(@Lazy 首次抢锁才连,本地无 Redis 照常启动,
      连接超时收紧防降级阻塞)+ `LockService` 收口(tryAcquire 非阻塞/看门狗续期/`erp.lock.fail-open` 降级可配:
      一期 true 无锁照跑靠 uk 幂等兜底,二期多实例置 false 宁停拉不重复拉);OrderPullJob/ProductPullJob 平移
      (key = pull:order|product:{shopId},命名空间 erp:lock:);两 Job 测试改 mock LockService,新增 LockServiceTest 6 用例;
      docs/07 §1 ③/§5 同步改版;锁只装互斥(防重入/single-flight),数据正确性仍走 DB 原子 UPDATE(§1 ①,库存侧随 #7 落地)

## #14 通知渠道(2026-09-04 拍板:站内先行)
> 决策:站内通知先做;重要事项的邮件/短信/IM 推送(微信/飞书/钉钉/企业微信)为后续渠道,随前端工程与实际告警量再接。
- [x] `sys_notification` 表(系统写入表:告警由任务扇出写入,对外只读 + 本人已读状态;
      已读状态列用 `read_status` 命名规避 is_ 前缀列,与 match_status/success 同风格)——
      新库重跑 01_schema_init.sql 即建,已建库环境无 ALTER
- [x] SysNotificationService(整域收口):写侧唯一入口 `pushAllUsers`(系统告警扇出全部启用用户,
      @Transactional,content 截断 1000,类型/业务类型常量收口本类);对外接口 = 我的通知分页/详情(归属校验)/
      未读数/单条已读/全部已读;SysUserService 增 `listEnabledUserIds`;单测 8 个(erp-system 首个测试类,
      pom 补 starter-test);⚠️ 坑:LambdaUpdateWrapper.set 急切解析列名,纯单测环境无 MP 元数据会炸,
      已读更新走实体 null-skip + eq 条件(注释已标)
- [x] 拉单告警接线(#4/#5 收口):`PullConsts.FAILURE_ALERT_THRESHOLD=3`;
      `PullLogService.shouldAlertContinuousFailure` 无状态判定(pull_log 自身去重,恰达阈值轮次告警一次);
      OrderPullJob/ProductPullJob catch 内接线,告警写失败不阻断主流程
- [x] 2026-09-06 前端通知中心页落地(#14 前端面收口,铃铛下拉之外的全量分页):系统管理目录新页 id=22
      (perm system:notification:list,菜单种子回写 01_schema_init.sql,已建库直接跑新增段,重登录生效);
      readStatus 搜索过滤 + 单条/全部已读(个人操作后端归属校验,无 permKey),操作后同步铃铛徽标 store;
      NotificationApi.page 统一 {list,total} 程式(铃铛消费点同步适配);手写页(gen:page 存在即跳过会撞
      P2 手工 api 文件,程式对齐生成页);⚠️ 顺手修种子漏配:sys_role_menu 缺 (1,21) 拉单日志授权行,已连同 (1,22) 补上
- [x] Webhook 通知渠道 V1(2026-09-08,IM 群机器人先行):pushAllUsers 出口发布 NotifyPushedEvent
      (erp-system 内事件,发布方监听方同模块不进 common),WebhookPushService @TransactionalEventListener
      AFTER_COMMIT + fallbackExecution 消费——**事务提交后才外推**(同 #11 发货回传拍板),失败只记日志
      不回滚站内通知不上抛;三渠道钉钉(markdown,加签)/飞书(text,加签)/企微(markdown,免签)签名口径
      =官方文档(钉钉 sign 挂 URL base64 后 URL-encode、飞书 sign 挂报文体 key=ts+"\n"+secret data=空串官方特例、
      企微免签),HmacSHA256 走 JDK 原生不引 hutool-crypto;HTTP=RestClient(同 LwaTokenClient 先例)
      connect 3s/read 5s 防拖死告警 Job,响应码尽力校验(errcode/code 非零 warn,非 JSON 不判失败);
      配置拍板:erp.notify.webhook 四键(enabled/type/url/secret)同域部署级**不进 sys_config**——
      url/secret 凭证类(hook 地址内嵌 token,docs/07 §7 红线)走 local.properties/环境变量
      (ERP_NOTIFY_WEBHOOK_URL/ERP_NOTIFY_WEBHOOK_SECRET,模板已登记),enabled 默认 false(外呼保护性默认关,
      与 erp.alert 内呼默认开反向);企微内容截 1000 字符防超 4096 字节;站内零用户也发布事件(告警为源,
      外推不随收件人数量增减);单测 +10(开关短路/三渠道报文/钉钉签名独立 Mac 交叉核对/飞书体签名自洽/
      企微截断/失败吞掉/非零码仅告警/AFTER_COMMIT 相位注解),erp-system 83 全绿;邮件/短信后续同款挂监听
- [ ] 后续渠道:邮件/短信推送(在 pushAllUsers 出口扩展,同 Webhook 各挂一个监听,不提前抽象)——
      **2026-09-08 用户拍板:通知渠道后续迭代与邮件/短信整体排期放最后**,不插队当前主线;
      通知铃铛+已读 ✅ 随 #16 落地

## #15 公开前治理与发布策略(2026-09-05 边界定稿;旧仓库 ChenYiG7/erp 已删库重建,现仓库 ChenYiG7/ec-erp 私有)

商密边界定稿(拍板):**随仓库发布** = 全部 Java 源码/pom/erp-codegen/scripts/CLAUDE.md/TODO.md/.claude/skills/README/
`docs/07-开发规范守则.md`/`docs/sql/`;**商密本地留存** = docs/01~06 设计文档、docs/08 精读清单(个人学习笔记)、
docs/design/、docs/devlog/。历史排查结论(2026-09-05 两轮盘点):15 提交历史完全干净,docs/密钥/个人信息从未入库;当前跟踪文件内容干净,
仅 JwtProperties 密钥默认值一处实质残留(已修,见下)。
**提交历史重排(2026-09-05 拍板):归零重放为单提交 `feat: ec-erp v0.1 初始版本`**——泄露面按构造归零,
顺带消除叙事疙瘩(0604584「docs不入公开仓库」被 07/sql 重新入库推翻、3886989 更名 25 文件噪声);
过程叙事由本地 devlog 承担,旧 15 提交仅存本地 tag `backup/pre-public-20260905`(**严禁推送该 tag 到远端**)。
公开后历史照常按任务细粒度生长。

已完成(2026-09-05):
- [x] `.gitignore` docs 规则分层:`docs/*` + `!docs/sql/` + `!docs/07`;补 `.claude/settings.local.json`(原靠全局 gitignore)/`.env`/`*.bundle` 三条防御规则
- [x] `docs/07-开发规范守则.md` + `docs/sql/01_schema_init.sql` 入库(07 头部已加私有 docs 引用声明;07 敏感扫描干净:无 PII/内网/密钥/业务数字)
- [x] `JwtProperties.secret` 删 dev-only 默认值 → 与 ERP_TOKEN_KEY 同规:无默认值,未设置/空白/不足 32 字节启动即失败(JwtTokenService 构造期);TODO.md/README 过时"默认密钥"记载同步修正
- [x] TODO.md #3 选型背景资质状态中性化(不透露资质持有情况)
- [x] CLAUDE.md 商密边界/devlog 降频表述更新;凭证轮换:人工确认风险自担不换(旧仓泄露的 MySQL/Redis 密码,TODO0 未尽事项闭环)

公开时检查单(届时执行,当前不动远程/不翻可见性/不建镜像工具):
- [x] 路线 A(全公开,单仓翻可见性)一次性公开版文案提交 ✅ 2026-09-05(文案+推送完成,⑥翻可见性待人工):
      ① README 目录结构节 docs/ 说明改为「07/09 规范 + sql 建表脚本随仓库发布;01~06/08/devlog 为作者私有」(09 随 P4 入库后公开面同步扩容);
      ② TODO.md 顶部加同义一句声明;③ `.claude/skills/reading-list/SKILL.md` 补「docs/08 为作者个人学习笔记,不随仓库发布」;
      ④ 补 `LICENSE` —— **拍板 MIT**(Copyright 2026 ChenYiG7;erp-web/ 内 Geeker-Admin 上游 Apache-2.0 副本保留,兼容);
      ⑤ ~~`git push --force`~~ 实际**普通 push 即可**——归零单提交 710aa8f 早已在 origin/master,后继提交按"公开后细粒度生长"正常推
      (710aa8f..c9462fe,含 P7 收尾 fix 与公开版文案两条);推送前敏感终检通过(局域网 IP/私钥/真实密码/个人邮箱全零命中,
      erp-web/.env* 为 Vite 构建常量、package.json 邮箱为上游 Geeker 作者署名,均保留);
      **⚠️ 执行时发现 `backup/pre-public-20260905` tag 缺失(git tag -l 空)——旧 15 提交仅以 dangling 对象存活,gc 后即永久丢失;
      已从 fsck 悬空提交 0604584 补打本地 tag(15 提交链完整),ls-remote 确认远端无 tag,**严禁推送该 tag**;
      本仓库系全新 init 归零(非旧仓 reset),旧历史只存在于该备份 tag**;
      ⑥ GitHub Settings → Change visibility 翻公开(人工执行,git 无法替代)——**用户拍板 2026-09-05 暂缓,仓库维持私有,择机再翻**
- [ ] 路线 B(部分公开,双仓镜像)——届时再落地,不提前建:白名单导出脚本(git archive 白名单 + docs 私有引用替换 + orphan 分支推公开仓);
      闭源模块分发走编译产物(jar)或加密源码包(Release 附件+口令),**不采用 git-crypt 混仓**
- [ ] 持续纪律:docs/sql/ 与 07/09 现属公开面——今后 docs/sql 只放可公开 DDL、07/09 只放可公开规约;其余 docs 仍严禁 `git add -f`

## #16 前端工程(Geeker-Admin v2 底座,2026-09-05 拍板立项)

> 拍板:底座 = Geeker-Admin v2(`Geeker-Admin/Geeker-Admin@main`,实测 Vue 3.5.38 / Vite 8.0.16 Rolldown / TS 6.0.3 /
> Element Plus 2.14.2 / Pinia 3.0.4 / vue-router 5.1.0 / oxlint+oxfmt / pnpm 11.8 / node ≥22);
> **许可证实测 Apache-2.0(非对比文章所标 MIT),已确认接受**——erp-web/ 保留上游 LICENSE 副本 + README 声明二次开发。
> 位置 = 本仓库根 `erp-web/`(纯 Node 工程,不进根 pom);规范 = `docs/09-前端开发规范守则.md`(公开面,随仓库发布);
> 前端代码生成器 = `erp-web/tools/`(api:sync 抓 /v3/api-docs → openapi.json 快照 + gen:page 行式 spec 驱动产
> api/页面骨架/菜单 SQL,移植 erp-codegen 理念:存在即跳过/守卫报错/TODO(编号) 槽位)。
> 会话纪律:日常前端开发在 erp-web/ 下开会话;契约唯一查询源 = openapi.json,前端会话禁读后端 Java 源码。
> 完整计划见 2026-09-05 前端立项 plan(P0~P7 八阶段)。

阶段清单(顺序不可换,每阶段末验收):
- [x] P0 立项固化:本条目 + devlog 骨架(拍板段)✅ 2026-09-05
- [x] P1 工程搭建:clone v2 → 裁剪(demo 页/mock/重依赖 echarts·wangeditor 等)→ 品牌化(.env/标题/端口 5173/
      vite proxy /api→8088 禁 rewrite)→ .gitattributes LF 防线 → pnpm install/dev/build 冒烟 → 首提交 ✅ 2026-09-05
- [x] P2 后端接线:axios 双错误形态(HTTP 200+code≠200 业务报错 / 真实 401→清 token 跳登录·403→提示;
      Result 解包止步 data)/ auth+notification api / 登录链路(pinia persist)/ dynamicRouter 按 /me menus 树转换
      (menuType 1目录·2菜单(import.meta.glob 映射,无视图报错禁白屏)·3按钮→perms 收集 v-auth)/
      通知 60s 轮询+visibilitychange 暂停 ✅ 2026-09-05(全链路冒烟待 P7 后端起后补验)
- [x] P3 前端生成器:erp-web/tools/(api-sync.mjs + gen-page.mjs + lib 五件(fsutil/openapi/types/spec/render)+
      specs/shop·shop-product 拍板表 + test/ 冒烟夹具;tools/** 已排除 oxlint——CLI console 属正当输出);
      路径→模块例外映射表(shops/pull-logs/shop-products/shop-product-skus→shop,orders→order,auth→system);
      ✅ 2026-09-05 冒烟夹具验证:可写域四件 + readonly 三件 + GET/POST 动作升格 + 守卫负例(缺快照/未知键/
      未登记 TODO 均带行号报错)+ 二跑全 SKIP + type:check/lint 全绿;
      ⚠️ shop/product 真快照生成挂起随 P6(本机 MySQL/Redis 未起 + local.properties 缺 ERP_TOKEN_KEY,后端无法起;
      specs 已按 01_schema_init.sql 字段备好,P6 起后端 api:sync 后 --force 重生成核账)
- [x] P4 docs/09-前端开发规范守则.md(12 章,风格对齐 07;.gitignore 白名单已验 git add 生效)✅ 2026-09-05
      (技术栈定版/目录分层与 api 收口/双错误形态/ProTable 范式含 v2 两坑:toolbarLeft auth 无效·#operation 插槽必给/
      金额 string 红线/权限 perms 空放行约定/store 划分含 $reset 不可用/安全红线/生成器优先门禁/反模式对照表)
- [x] P5 仓库治理:根 CLAUDE.md(必读+构建+铁律8+速查表 erp-web 行+公开面补 09)/ erp-web/CLAUDE.md(契约唯一查询源/
      命令速查/生成器正道/高频契约事实)/ .claude/skills/add-page(镜像 add-domain 七步)✅ 2026-09-05
- [x] P6 MVP 八页面 ✅ 2026-09-05 晚(环境解锁后真快照落地:登录布局动态菜单 P2 已通 / 用户(角色分配弹窗+
      prompt 重置密码)/ 角色(菜单授权树:回显只勾叶子,保存并半选)/ 菜单(树形例外页手写:NONE 分页+数组 requestApi)/
      字典(clearDict 强刷接线)/ 通知铃铛 P2 已通 / 店铺(authUrl 按钮接线,示范①)/ 商品库(TreeFilter 接线,示范②)
      + shop-product 平台商品只读页;
      **真快照核账修掉生成器 4 盲区**:分页参数对象/混合形态(springdoc POJO 渲染)、records.$ref 在 items(无详情域)、
      Response 取数分页行优先(商品详情是 Result<Map>)、同域同名动作加方法前缀(getRoles/putRoles)+ rules 逗号;
      菜单种子收敛:id=7 对齐 spec、+100 字典/101 平台商品、按钮段 200~242、shop_platform 字典种子 14 条,
      已建库手工 SQL 见 01_schema_init.sql 注释)
- [x] P7 验证收尾 ✅ 2026-09-05 晚:门禁四件全绿(type:check/lint/lint:stylelint/build)/ 生成器六域二跑全 SKIP +
      冒烟夹具回归 + api:sync 二跑无变化 / API 级冒烟:登录·/me·menus/tree·七列表端点全通,401(真状态码)与
      HTTP200+code≠200 弹窗两形态实测符合拦截器契约 / devlog TODO16 四段补全 / 本条目勾结;
      ⚠️ **遗留(随 #16 收尾清单,非前端阻塞)**:① ~~后端写链路统一 500~~ **已排查闭环(2026-09-05 晚):后端无 bug**——
      根因是冒烟工具在 Windows 终端发中文 body 实为 GBK 编码,Jackson 按 UTF-8 严格解析拒收(HttpMessageNotReadableException),
      读接口无 body 故全通;以工作区最新代码(含 CryptoService @Value + Service @Lazy 修复)干净实例冒烟,
      读六端点全 200、纯 ASCII body POST dicts/roles 全 200;浏览器 UI 手工链路验收 ✅ 2026-09-05 深夜全走通
      (角色授权重登录生效/店铺 auth-url 跳转/通知已读/字典联动);② 菜单 icon 渲染体系已浏览器核对收口(EP 组件名体系可用,无需改造);
      验收过程沉淀:element-plus 已按需显式导入(main.ts 不再全局注册),模板用 el-* 漏 import = 运行时
      Failed to resolve component——生成器 render.js 新增 elImportsFor() 按表单实际控件精确收口 + 15 个存量页面/组件手工补导,
      验收线七段全通,#16 全清 ✅;
      ⚠️ 坑(2026-09-06 补记):Rolldown codeSplitting 分组默认**递归捕获依赖**(不带约束),
      字母序最靠前的页面组会吞掉 element-plus/ProTable 等共享闭包(新增 aftersale 页即触发,单 chunk 1.2MB 失衡、
      共享 chunk 全部消失);修复 = vite.config `includeDependenciesRecursively: false` + dynamicRouter glob
      排除 `views/**/components/**`(私有组件禁入路由注册表);修复后 vendor 按包拆分、页面 chunk 0.1~12KB;
      ⚠️ 坑(2026-09-06 二记):gen:page 表单 enum 产 el-option 时 string 值 JSON.stringify 双引号与属性引号嵌套
      (`:value=""SELF""`),运行时 value 为空串且 type:check 查不出——修复 render.js 产单引号字面量
      (`:value="'SELF'"`,数字/布尔不受影响);warehouse 域首个 string enum 下拉当场抓获,存量域无受害(均 dict/数字枚举);
      ⚠️ 盲区补修(2026-09-06 三记):gen:page 菜单行 perm_key 硬编码 null → 改产 `<permPrefix>:list`
      (对齐 01_schema_init.sql 既有 menuType=2 种子约定,拉单日志页当场触发,免回写时人工补);菜单 sort 仍固定 1,回写种子时人工调整

- [x] 2026-09-06 前端人工槽收尾(SkuSelector 公共组件,src/components/SkuSelector/index.vue):
      契约无全局 SKU 搜索端点(GET /api/goods/skus 仅 SPU 内列表,ProductQuery 仅 keyword/categoryId)——
      组件内部 = 商品 keyword 搜索(top5)→ 各 SPU 并行拉 SKU 拍平(300ms 防抖 + 序号防竞态,选项上限 50);
      接线两处裸 ID 手填:采购建单明细行(PurchaseOrderForm)+ SKU匹配人工绑定(prompt 升级 BindDialog 弹窗);
      后端若补全局 SKU 搜索端点仅换组件内部实现,调用方不动;apis/goods/sku.ts + interface/goods/sku.ts 契约外人工登记

验收线:登录→动态菜单→系统四页 CRUD(用户含角色分配、角色含菜单授权、菜单树形、字典含 type)→ 通知红点+已读 →
店铺页 auth-url 跳转按钮 → 商品列表分页过滤,全部走通 ✅ 2026-09-05 深夜(#16 全清;后续域页面按 add-page 生成器逐域铺开)

## #17 开源 ERP 对标参考库(2026-09-05 登记,三期/四期功能规划对照)

> 拍板:三个成熟开源电商 ERP 作为**产品 roadmap 对标库**——学功能形态/数据模型/交互,不搬实现(技术栈代差,
> 架构纪律不因对标松动);许可先看再借鉴;做对应域时回此表对照,防漏掉被市场验证过的功能面。
> OmniTrade 9 大 AI 服务**全部纳入三期/四期规划**(2026-09-05 拍板),落位收口 erp-ai 三层(tools/graph/agent)
> + ai_suggestion 人工确认闭环,不建独立微服务;具体排期各期开工前逐项拍板。

| 项目 | 定位/技术栈 | 许可 | 对标价值 |
|---|---|---|---|
| [wimoor-erp/wimoor](https://github.com/wimoor-erp/wimoor) | 亚马逊单平台商业 ERP 开源版(多年商业化运营;SB2+JDK8+微服务 nacos/seata) | MIT | 功能清单最全:补货规划/采购单/商品分析/趋势分析/FBA 发货规划/广告管理;API 限流表设计(t_amz_api_timelimit)已被 #3 参考 |
| [nplszfl/OmniTradeERP](https://github.com/nplszfl/OmniTradeERP) | 跨境 10+ 平台"智能 ERP"(Java21+SpringCloud 微服务;Amazon/eBay/Shopee/Lazada/TikTok) | MIT | **9 大 AI 服务功能形态 = 三期 #6 扩容对标**(落位表见下);选品 Feedback 权重自调优闭环 |
| [qiliping/qihang-erp-open](https://gitee.com/qiliping/qihang-erp-open) | 国内平台电商中台(淘宝/京东/拼多多/抖店/微信小店/快手/小红书;**Boot4.1+SpringAI2.0+MP3.5 单体,技术栈与本项目最接近**) | **AGPL-3.0** | 国内 7 平台对接面/电子面单打印发货/供应商代发/云仓发货/11 个 AI @Tool 清单/飞书钉钉企微 Webhook 通知渠道 |

⚠️ **许可纪律**:qihang 为 AGPL-3.0(强传染)——**只看 README/文档/演示交互,禁读其源码**,防"重写式移植"
衍生作品争议(本项目 MIT,代码血缘必须干净);wimoor/OmniTrade 虽 MIT,默认也只借鉴思路,
确需参考表结构时在 devlog 记出处。

### OmniTrade 9 大 AI 服务 → ec-erp 落位(主体挂三期 #6,归属列记排期)

> **三期开工注记(2026-09-06)**:#6 AI 地基已落地(查询契约四件 + 只读 tools 首批四类 + chat 同步/SSE 双通道 +
> ai_suggestion 确认闭环 + TOOL 行审计,详见 #6 勾选);**库存预警规则引擎(V1 三规则)与 graph/ 补货工作流
> 同日收口**;2026-09-08 三期 AI 面基本收口:采购建议/文案生成/AI 客服 RAG(向量库拍板 SimpleVectorStore)
> 当日三连,余量仅智能定价(竞品价数据卡,随 adapter 扩容)与报表/选品(四期)。

| OmniTrade 服务 | 功能面 | ec-erp 落位 | 归属 |
|---|---|---|---|
| 库存预警 | 滞销检测/低库存/积压预警 | 规则先筛(#6"两段式"的规则半边),出口 #14 站内通知已就位;qihang 同款规则(销售额为零/发货超时/退款过多)一并纳入规则集 | 三期最先(成本最低) |
| 库存预测/补货建议 | 时序预测+补货量建议 | #6 已规划:SAA Graph 补货建议工作流(取数 LLM→规则校验→报告) | 三期(已对齐) |
| 订单异常检测 | 规则引擎+AI 评分双层融合/风险分级 | #6 已规划:"规则引擎先筛+LLM 评分"两段式控成本 | 三期(已对齐) |
| 智能采购建议 | 采购预测/供应商比价/采购计划 | ✅ 2026-09-08 V1 落地(graph/purchase 四节点:补货缺口按最新采购供应商聚合,预估金额=最新单价×建议量;比价随多供应商数据积累评估,定时接线待拍板)——建议层叠加 #10 采购域之上(只产建议进 ai_suggestion,不碰状态机与单据) | 三期(已对齐) |
| 智能定价 | 竞品价监控/动态调价/利润优化 | 竞品价拉取随 adapter 平台扩容;定价建议进 ai_suggestion,人工确认后走改价 | 三期候选 |
| 产品描述生成 | SEO 文案/批量生成/平台风格适配 | ✅ 2026-09-08 V1 落地(graph/copywriting 三节点:扫启用商品→LLM 批量产 listing 文案(标题/五点/描述/关键词)→落 ai_suggestion;人工采纳后文案在详情 payload 复制使用,V1 不自动回填平台——改写 listing 随 adapter 上架类接口扩容再评估;平台风格适配 V2) | 三期候选(V1 已落地) |
| 智能报表 | 日/周/月报自动生成+Excel 导出 | erp-report 域(休眠),依赖销售/广告数据面先齐 | 四期 BI |
| AI 客服 | RAG 知识库/意图识别/多语言 7×24 | ✅ 2026-09-08 RAG V1 落地(向量库拍板 SimpleVectorStore 文件持久化零新基建,向量不入库 ai_kb_chunk 正本可重建;上传/粘贴接入 → 切块向量化 → chat 双通道检索注入,admin 双闸写侧,降级零侵入);意图识别/多语言随四期 | 三期(V1 已落地) |
| 智能选品 | 多维加权评分/趋势/风险评估+Feedback 自调优 | 评分引擎产建议进 ai_suggestion;其 Feedback 闭环由 ai_suggestion(待确认/已采纳/已忽略)天然承接;权重自调优四期评估 | 四期候选 |

### 非 AI 功能面(各项目对标,随期吸收)

- 补货规划/商品分析/趋势分析(wimoor):补货规划依赖库存预测先行;商品/趋势分析归四期 BI
- 广告管理(wimoor 广告抓取+管理全套):erp-ads 休眠域,四期;数据抓取随各平台 adapter 扩容
- 电子面单打印发货/面单账户管理(wimoor、qihang):国内平台发货刚需,#11 遗留"电子面单随 #3 adapter"升格为独立功能面
- 供应商代发/云仓发货(qihang 备货单模式):对齐 #11 遗留"FBA/OVERSEAS 供应商代发与海外仓流程待业务确认"
- 外部通知渠道:飞书/钉钉/企微 Webhook(含钉钉签名/失败重试/高优外推低优站内分级)——#14"后续渠道"的对标实现,
  届时参考其渠道抽象,落点仍是 pushAllUsers 出口扩展(不提前抽象纪律不变)
- AI @Tool 清单(qihang 11 个:店铺/订单/售后/商品/库存/采购/会员/供应商/物流/仓库/库存流水)——
  #6 tools/ 只读 @Tool 集的对标 checklist,对应域数据齐一个开一个;
  ⚠️ qihang 宣称 Tools 可读写 ERP 数据,与铁律 7 冲突,**只读红线不采纳其读写形态**

⚠️ **对标姿势**:OmniTrade README 营销数字(利润 +15%/客服成本 -70% 等)不可验,其更新日志自曝 v2.0.0 前
定价服务为硬编码模拟、RAG embedding 为 SHA-256 伪向量——**按功能形态对标,不按实现质量对标**;
qihang 开源版/企业版双轨宣传,只取开源版功能面,企业版能力(多商户/京东云仓)不在射程。

## 已就绪(无需再动)
- 二期单据地基(2026-09-03 三批):erp-codegen 升级 `parts=`(子表只出 entity+mapper)/ `readOnly=`(系统写入表只读,
  不产 SaveRequest、Service/Controller/Test 只渲染读侧)两参数,冲突守卫报错;docs/03 §5 草案 6 表定稿进 01_schema_init.sql
  (CREATE IF NOT EXISTS 幂等,已建库重跑即补建)并一次生成:erp-purchase(supplier 全 CRUD / purchase_order 全 CRUD /
  purchase_order_item 子表 / purchase_inbound 全 CRUD)、erp-fulfill(delivery_order 全 CRUD)、erp-aftersale(aftersale_order 只读);
  22 个单测全绿(purchase 15 + fulfill 5 + aftersale 2);AI/财务三期表(settlement/ad_report/ai_suggestion 等)字段未定,等开工再落
- 休眠模块地基第二批(2026-09-03):6 表骨架按域角色裁剪落地——erp-shop(pull_log / shop_product / shop_product_sku)、
  erp-order(shop_order / shop_order_item,子表仅 entity+mapper)、erp-inventory(inventory_flow)。
  系统写入表一律对外只读(订单/拉取日志/listing/库存/流水无人工 CRUD 写接口,写入口唯一:
  saveUnifiedOrder(#4 待实现)/ OrderPullJob(#4)/ 同步 upsert(#5)/ InventoryService.change(已落地));
  ShopOrderResponse 剔 raw_json(排查直查 DB);三个模块 42 个单测全绿(shop 31 + order 2 + inventory 9);
  新域菜单种子未登记,随前端工程统一规划;erp-shop pom 新引 spring-security-core(仅 @PreAuthorize 注解,过滤链仍收口 erp-api)
- 17 模块骨架,`mvn -DskipTests compile` 通过(Boot 4.0.6 + Spring AI 2.0.0-M5 + SAA 2.0.0-M1.1 + AgentScope 2.0.0-RC5 + MP 3.5.17)
- 安全认证 + RBAC(#1,2026-09-02):JWT(JJWT 0.13.0)+ Security 7 过滤链 + BCrypt + 菜单三表,默认管理员 admin/admin@123
- 凭证加密(#2,2026-09-02):AES-256-GCM `CryptoService`,密钥 `ERP_TOKEN_KEY`(无默认值,启动强制校验);
  店铺凭证加密落库/掩码回写防护/`getShopSession` 解密装配;单测 17 个(erp-shop 首批单测)
- 防腐层 SPI:`PlatformClient` / `AdapterRegistry` / `UnifiedOrder|Product|Refund`
- 简单 CRUD:dict/brand 直连 Mapper(纯配置域合规);shop/product(sku)/category/user/role/menu 域已整域收口 Service——Controller 不直连 Mapper(2026-09-03,docs/07 §2.1 整域判定)
- 接口文档(2026-09-03):springdoc 3.1.0(Boot 4 专用线),`/swagger-ui.html` 预览 + `/v3/api-docs` 供 Apifox 订阅;全部 Controller 已标 @Tag/@Operation(中文);生产 yml `springdoc.api-docs.enabled=false` 关闭
- 全局异常、分页(PageQuery + MP Page)、MyBatis-Plus 配置(@MapperScan + 分页插件)
- 建表脚本 `docs/sql/01_schema_init.sql`(2026-09-02 修订)与 `docs/03-数据库设计.md` 已对齐
- 基建补齐(2026-09-03):19 表时间戳两列全覆盖(开发库 ALTER 已执行,清单存档 #7)、全部业务模块 pom 对齐标准栈(web+validation+MP 三件套+test)、starter-validation 引入、分页统一 `query.pageSize()` 钳制版、连接与密钥全部外置 git-ignored `local.properties`(键=环境变量名,env 优先级更高;application.yml 明文已清零)
- **erp-codegen 代码生成器**(2026-09-03):`docs/sql/01_schema_init.sql` DDL → Entity/Mapper/Service/Controller/单测 骨架(存在即跳过、pom 守卫、float/double 报错),用法见 erp-codegen/README.md;试点产出 erp-inventory / erp-warehouse 两域(TODO(#7) 占位);
  同日随 #8 升级八件套(Query/VO/SaveRequest + 显式映射,docs/07 §1),新生成域直接合规,存量两域待 #8 翻新
- **erp-codegen 状态机守卫测试生成器**(2026-09-04,铁律 #8"同一路径四连沉淀"):`StateMachineTestGenerator`
  (`-Dcodegen.mainClass` 切换,零业务依赖纯 JDK)——"条件更新即守卫"状态机(#10 采购/#11 发货/#12 售后已四连,
  守卫用例盘点机械率 >85%)不再 AI 手写,spec 文件驱动(行式,UTF-8)生成 `XxxStateMachineTest.java`
  (cas 命中+脱靶合并式 / 单不存在 / 尾参必填 never() / 成功路径实参核对 verify,与手写 `XxxServiceTest` 并存,存在即跳过);
  spec 即状态机拍板表文档化(改状态机 → 改 spec → force 重跑),样板 `erp-aftersale/testgen-aftersale.txt`;
  边界:跨域动账断言(InventoryChangeApi captor)/发足收齐判定/类型分流全量负例/复合事务动作(ship/confirm)不在射程,
  生成物类尾 TODO(编号) 槽位人工补(manual 必须配 todoId,禁裸 TODO);
  试点:aftersale spec 6 行 action → 生成 7 用例与手写 12 用例并存 `mvn -pl erp-aftersale test` 19 全绿,生成物验证后删除(spec 入库,下个状态机域首用)

## ⚠️ MyBatis-Plus 3.5.9+ 的坑(已规避,写代码时注意)
- Boot 4 必须用 `mybatis-plus-spring-boot4-starter`(不能再用 boot3 版),且需**额外**引 `mybatis-plus-extension`(分页插件所在)+ `mybatis-plus-jsqlparser`(分页 SQL 解析),starter 不再默认携带;
- **`IService`/`ServiceImpl` 已被移除**(新替代品是 `extension.repository.IRepository`)。本项目统一写法:**Mapper 做通用 CRUD(selectPage/insert/updateById/deleteById),Service 只装业务逻辑**,不依赖 MP 的泛型 Service 基类,版本再变也不受影响;
- 若启动报 AgentScope/SAA 自动装配错误,先在依赖里临时注释掉 erp-ai 的对应 starter(AI 代码本来就没写,不影响一二期)。

## ⚠️ SQL 兼容性红线(写 mapper XML 时必须遵守)
- **开发库 = MySQL 9.7.2 LTS,mapper XML 自定义 SQL 一律 9.7.2 原生形态**:
  VALUES 行 upsert 统一 8.0.19+ 行别名 `AS new`——ShopOrderMapper/ShopProductMapper/ShopProductSkuMapper/
  AftersaleOrderMapper 四处(**行别名定义后 ODKU 内列引用必须全限定:`new.` 前缀=本条插入值、表名前缀=冲突行
  现值,未限定一律 1052 歧义,9.7.2 实测**;弃用语法 VALUES(col) 禁用);
  INSERT...SELECT 场景行别名不支持(9.7.2 实测 1064):直传用**源表别名引用**(InventorySnapshotDailyMapper
  .upsertSnapshot),聚合值用**派生表别名引用**(OrderSalesDailyMapper.upsertWindow);
  "每组取排序键最大一行"用 **ROW_NUMBER() 窗口函数**(PurchaseOrderItemMapper.findLatestSupplierRows);
- 且单测全 mock Mapper 永远测不出来,**mapper XML 的自定义 SQL 必须真库验证**
  (脚本 scripts/validate_mapper_sql.py,七项断言+弃用告警探测);
- **教训(勿忘)**:单测 mock 测不出 XML 语法错误,作业会静默空跑——SalesSnapshotJob 曾连日语法错误空跑
  (order_sales_daily 零行,补货动销/滞销积压规则拿空销量数据);**改 SQL/换引擎当天必须重跑验证脚本**。

## #18 系统设置:大模型等启动后可变项前端可配 ✅ 2026-09-07 落地
- [x] sys_config 键值表(config_group 实际三组=AI/ALERT/SALES——对话与 Agent 的提示词/连接键统一归 AI 组,词表收口
      ConfigConsts:GROUP_AI 19 键/GROUP_ALERT 9 键/GROUP_SALES 2 键;config_key 与 yml relaxed-binding 键同名;
      uk_config_key;凭证类键禁入表——安全红线 docs/07 §7,词表白名单天然拦截,api-key 只走环境变量/local.properties;
      CREATE IF NOT EXISTS 幂等已入 01_schema_init.sql,已建库重跑 sys_config 段+菜单 29/290 段即可)
- [x] SystemConfigService(erp-system,域唯一写入口):按键取覆盖值(valueOf,trim 空白归 null)/分组合并视图
      (listByGroup:词表全量键 × DB 已存行 eq+内存交集,禁 .in() 急切解析坑 docs/07 §10)/保存三重校验
      (词表白名单→组键匹配→类型可解析 INT/LONG/DECIMAL/BOOL/TEXT,长度钳制 ≤1024)/空值=删覆盖行回落默认;
      **保存后 Spring 事件失效缓存**:发布 SystemConfigChangedEvent(erp-common,发布方 erp-system 与
      监听方 erp-api 互不依赖),SystemConfigApiImpl @EventListener 精准按提交键清 TTL 缓存(空键集全量清)——
      模型/提示词/阈值保存秒级生效;Controller 读侧登录即可/写侧 admin 双闸(@PreAuthorize hasRole('admin'));
      单测 SystemConfigServiceTest 12 个(空值语义/合并视图/三重校验/upsert+删行/事件发布)
- [x] SystemConfigApi 契约(erp-contract 六件)+ 实现收口 erp-api(SystemConfigApiImpl:委托 erp-system Service,
      @Lazy 断构造环;30s TTL 缓存 null 值也缓存防穿透;事件监听失效)
- [x] AI 配置运行时生效收口 AiRuntimeProperties(erp-ai,优先级 = sys_config DB 覆盖值 > yml/代码默认;
      每次取值经 SystemConfigApi,DB 无行/解析失败回落默认不抛错——配置错误不阻断业务;数值下限钳制收口取值口;
      禁 @PostConstruct 快照——快照即失去热更语义;单测 AiRuntimePropertiesTest 6 个):
      ①AI 工作流参数(补货 4 键/异常 4 键)——ReplenishCollectNode/ReplenishCalculateNode/
      AnomalyScanNode/AnomalyScoreNode 每轮取值已接线(扫描护栏 scanPageSize/scanMaxRows 属系统级稳定参数仍走 yml)
      ②AI 对话/Agent 提示词(chat system-prompt、agent support/ops、replenish summary、anomaly score)
      ——ErpChatService 每请求/AgentService 每轮/两 LLM 节点每轮取值已接线
      ③AI 智能体护栏(max-iters/history-max-messages/tool-audit-max-length)——AgentService 已接线
      ④chat 模型名热切 ✅ 实际落地偏离拍板注记:spring-ai 2.0.1 `options()` 签名收 ChatOptions.Builder 泛型
      (非 DefaultToolCallingChatOptions),ErpChatService 每请求 `ChatOptions.builder().model(覆盖值)`,
      无覆盖返回 null 保持原链路;DB 覆盖值即缓存(TTL 30s),无需 Service 内另建 TTL
      ⑤agent 连接热切(base-url/model 两键;**api-key 禁入 sys_config**,拍板⑤"只写不读"与 docs/07 §7 红线冲突,
      按红线执行=DB 无此键):AgentService model() 懒建单例加连接指纹(base-url|model)比对,变更即重建
      OpenAIChatModel,api-key 不入指纹(凭证轮换重启生效)
      ⑥库存预警阈值(9 键含 enabled)——AlertEngine 每轮/AlertJob 每轮取值切 runtime 已接线
      (默认值源 ErpAlertProperties 同模块;scanPageSize/scanMaxRows 护栏不入表)
      ⑦销量统计(enabled/rebuild-days)——SalesSnapshotJob 已接线;ErpSalesProperties 在 erp-api,
      erp-ai 禁反向依赖(铁律 2),AiRuntimeProperties 出 Optional 覆盖口,Job 侧回落 yml 默认
      ⑧系统设置前端页 ✅ system/config/index(分组 tab 面板手写页,gen:page 不适用——非 CRUD 列表页):
      AI/ALERT/SALES 三 tab,词表全量键渲染(占位行=代码默认值),布尔键开关/prompt 键 textarea/
      已覆盖·默认 tag 标注,保存按钮 v-auth="'system:config:save'";api/apis/system/config.ts 两端点;
      SysConfig 类型入 interface/index.ts;菜单 29/290 种子已入 01_schema_init.sql(已建库手工补齐段见脚本注释)
- [x] 单测补充:SystemConfigApiImplTest 6 个(TTL 命中/陈旧重读/精准失效/全量失效/null 穿透缓存/事件不触 DB);
      测试桩 RuntimePropsStub(graph 测试包内,函数式 SystemConfigApi 桩:AiRuntimeProperties 全量回落 yml 默认);
      存量节点/Service/Job 测试构造器全量适配,语义不变
- 验证:mvn 全 reactor 18 模块 test 全绿(erp-system 73 / erp-ai 102+ / erp-api 70+);前端 vue-tsc 0 错、
  oxlint 0 错、新文件 oxfmt 通过(存量 95 文件 fmt 基线陈旧系 oxfmt 版本差异,非本次引入
  ——✅ 2026-09-08 基线对齐收口:全仓 fmt 一次归一 103/288 文件(纯格式零语义),三件门禁复验全绿;
  权威格式=oxfmt,openapi.json 等生成物导出后跑 fmt 归一);
  api:sync 契约快照 86→87 路径(+GET/PUT /api/system/configs/group/{group})

## #19 财务/结算域(三期 settlement 主线,2026-09-08 立项;求职作品集 L3 深度样本,利润核算的"准"=领星护城河位)

> 落位拍板(2026-09-08 会话结论):三期 AI 面收口后,复杂逻辑缺口集中在 L3 财务利润——settlement 主线先行
> (脱机落地,真凭证即插即用),补货算法升级穿插其后,AI 扩容冻结(没有 L3 利润数据,ACOS/定价/选品开不了)。
> 四步路线:①立项+DDL(本条)→ ②结算报告解析 → ③利润核算 V1(实时口径先行)→ ④退款勾稽(#12 遗留收口)。

- [x] ① 立项 + DDL(2026-09-08):
  - 三表定稿进 01_schema_init.sql(docs/03 §6 同步定稿,2026-09-08):`settlement_report`(周期正本,
    uk_shop_settlement 幂等键=平台 SettlementId,重拉 upsert;status=PARSED 勾稽平/FAILED 可重拉覆盖)、
    `settlement_detail`(金额事件流水,report 级幂等——重拉按 report 先删后插同 #4 纪律,行级不设唯一键:
    平台允许多行同键事件;order_item_id=SKU 级利润归集键对应 shop_order_item.platform_order_item_id;
    金额带符号存报告原值禁取绝对值)、`exchange_rate`(汇率快照,折算按业务日回溯取最近报价禁取表内最新,
    本位币 V1 固定 CNY);scripts/settlement_migration.py 幂等落开发库(三表+关键索引自检 ALL GREEN);
  - 拍板:fee_type 解析器归一集 V1 = SALE/REFUND/COMMISSION/FBA_FEE/STORAGE/ADVERTISING/TRANSFER/OTHER
    (只加不改);勾稽口径 = Σ明细金额 = 报告头 TotalAmount 才入库,否则整单 FAILED(禁静默截断);
    ad_report_daily 留草案随 erp-ads 广告数据面激活;
  - erp-finance 骨架:entity 三件 + mapper 三件(BaseMapper 通能力,零自定义 SQL),包结构 com.own.erp.finance
    (@MapperScan com.own.erp.**.mapper 天然覆盖);Service/Controller/解析器刻意不建——复杂逻辑占位待②③切片;
- [x] ② 结算报告解析(2026-09-08 脱机落地,✅ 报表类型勘误:**GET_V2_SETTLEMENT_REPORT_DATA_FLAT_FILE_V2**——
      立项时误记 GET_V2_SETTLEMENT_REPORT_FLAT_FILE;旧 V1 flat file/XML 官方 2026-11-11 移除禁再引用):
      结算报告**不可主动创建**(平台自动按打款周期调度)——链路=SpApiReportsClient.listSettlementReports
      (getReports 搜 COMPLETED,marketplaceIds 限站点 + pageSize=12≈半年 + NextToken 翻页防御上限 5 页)
      → fetchSettlementReportContent(下载链抽共用 downloadDocument,listing/settlement 同款)
      → AmazonSettlementTranslator TSV 按列名解析(三段结构:结算头行/事件行/"Settlement Total" 汇总段容忍跳过);
      拍板:①SPI `pullSettlements(session)` **无时间窗**(离散费用正本非时序流,docs/04 拍板表),
      游标退化为幂等 upsert(PRODUCT 先例),落库 erp-finance `SettlementService.saveUnifiedSettlement`
      (事务=正本 upsert + 明细先删后插;uk_shop_settlement 幂等——PARSED 跳过/FAILED 重拉覆盖);
      ②勾稽=Σ明细 vs 报告头 total-amount,不平整单 FAILED 留痕(费用事实是资产禁静默截断);
      ③金额报告原值带符号 + **本地化小数格式**(EUR "1.234,56" 按币种拍板解析);
      ④fee_type 归一只加不改,**⚠ 实测坑:报文里佣金/FBA 费行 transaction-type=Order,描述与
      amount-type 检查必须先于 Order 短路**(单测 fixture 两轮抓出 COMMISSION/FBA_FEE 双误判);
      ⑤币种无需站点推导(V2 报文自带 currency 列,与 listing 报表差异);
      单测 +21(AmazonSettlementTranslatorTest 8 + SpApiReportsClientTest +3 + AmazonClientTest 守卫 +2
      + SettlementServiceTest 5),erp-platform-sdk 87 / erp-finance 5 全绿;
      ⚠ 编排接线(手动触发或低频 Job)随真凭证联调拍板——无凭证时 Job 只会空转;
      翻译 fixture 为官方文档结构推导样例,真凭证样本到位后 --force 校准一轮(docs/07 §8)
- [x] ③ 利润核算 V1(2026-09-08 落地,实时销售利润先行,docs/02 §14 三口径第一层):
      订单口径 SKU 级利润 = 售价 − 成本 − 平台佣金(− 退款);成本先移动加权(基于 inventory_flow,
      docs/02 §114 拍板;product_sku.cost_price 静态价撑不起利润核算,FIFO 全局批次核算更重放后面);
      汇率折算按下单日回溯 exchange_rate;周期口径(结算单)随②数据到位后做差值校准:
  - 成本账 DDL:inventory_flow 加 unit_cost/cost_amount 两列(带符号,Σ 可逐笔重放校验 sku_cost_state) +
    新表 sku_cost_state(uk_sku,全局跨仓账本不分仓,调拨两腿不进账);scripts/profit_v1_migration.py 幂等落
    开发库 ALL GREEN;菜单种子:财务中心(31,sort=6)/实时销售利润(32)/汇率快照(33)+录入按钮(3301),
    通知中心 6→7/系统管理 7→8 顺移,scripts/profit_menu.py 存量库对齐 ALL GREEN;
  - 移动加权核心 erp-inventory InventoryCostService.apply(recordFlow 同事务调,FlowCostOps 枚举分派):
    仅 IN_PURCHASE 重算加权(scale 8 HALF_UP),出库/退货/调整按当时加权价结转共用 settleAtAvgCost,
    IN_TRANSIT/LOCK_SHIP/TRANSFER_OUT/TRANSFER_IN 不进成本账(NULL);缺价入库按当时加权价暂估、
    首次无价记 0(禁猜价);账本结存<0 抛 BusinessException 拒动账;
  - 并发拍板:SELECT FOR UPDATE 锁 sku_cost_state 行串行化同 SKU 成本计算(锁序 inventory 行→state 行
    单向无死锁),首建撞 uk_sku 捕 DuplicateKeyException 回退重读;InventoryChangeCommand 加 unitCost,
    PurchaseInboundService.confirm 传采购单价(单测断言 10.50 全链贯通);
  - 汇率回溯 erp-finance ExchangeRateService.resolveRate:CNY 短路=1,quoted_at<=下单时间最近一条
    (禁取表内最新),无报价返回 NULL 禁猜;/api/finance/exchange-rates 写侧 hasRole('admin');
  - 利润查询:契约 ProfitQueryApi 归 erp-contract(Query/Row 20 字段/Summary),视图 SQL 归 erp-finance
    (ProfitQueryMapper.xml 四条语句,Java 依赖走契约不破坏铁律 2);归集键:成本=OUT_SHIP×发货单行
    (biz_type/biz_id 指向发货单+sku 匹配,部分发货多笔自然 SUM),佣金=settlement_detail COMMISSION 行按
    shop_id+order_item_id(=platform_order_item_id) 聚合(SUM 报告原值负数);assemble 批量 map+逐行回溯汇率;
    缺口纪律(同②勾稽):缺成本利润置 null、缺佣金按 0 计毛利并打标记、缺汇率行不折算——三缺口单独计数
    (missingRate/costMissing/commissionMissing)不静默归零;
  - 前端六件:api interface/apis×2 + finance/profit(汇总卡+筛选+ProTable+缺口 tag+负利润红字) +
    finance/exchange-rate(列表+录入弹窗,双 v-auth);
  - 测试 +26(InventoryCostServiceTest 11/ExchangeRateServiceTest 7/ProfitQueryServiceTest 8,适配 2),
    全 reactor 20 模块 BUILD SUCCESS,vue-tsc/oxlint 0 错;
  - mapper XML 真库验证 scripts/validate_profit_sql.py 六项 ALL GREEN(主查询全参/无参两形态/分页 LIMIT/
    成本聚合 SUM(-cost_amount)/佣金同键 SUM/哑元清理,990001 哑元段);⚠ 提取器三坑:<where> 须补 WHERE
    关键字、foreach 的 open/close 括号在标签属性里随标签丢失、无参形态 <if> 须整块剔除(留内容会生成
    = NULL 滤空全表)
- [x] ④ 退款勾稽(2026-09-08 落地,#12 遗留"退款金额与财务勾稽"收口):aftersale_order.refund_amount 对
      settlement_detail REFUND 行按订单聚合比对,差异经 #14 站内通知扇出(notify_type=REFUND_DIFF):
      归集键 = 店铺+平台订单号拍板——结算 REFUND 行无退款单 ID(平台结算报文不携带),逐单勾稽不可行,
      售后侧经 shop_order 翻译 platform_order_id 对齐(settlement_detail.order_id=平台原文),
      同订单多售后单/部分退款自然 SUM;参与范围拍板:售后侧=已退款终态(REFUNDED/COMPLETED)且
      refund_amount 非空(未决态钱未退不比,平台先行退款的时序差等状态同步后自然纳入),
      结算侧=PARSED 报告 REFUND 行(FAILED 报告明细是待校准暂存态,参与会污染比对,重拉转 PARSED 后纳入);
      金额方向:售后恒正(AmazonRefundTranslator 绝对值口径)/结算 Σ(−amount) 转正同向比对
      (报告原值带符号纪律不破坏);差异判定三类:AMOUNT_MISMATCH(双侧有行且 |差|>容差 0.01,
      容差=结算列 DECIMAL(18,2) 精度口径,防售后 DECIMAL(12,4) 舍入尾差误报)/
      MISSING_IN_SETTLEMENT(售后已退款而该订单在 PARSED 报告中无任何 REFUND 行,疑似报告未拉/
      退款未入结算周期)/CURRENCY_MISMATCH(任一侧同订单多币种或两侧币种不同,金额比对失真禁混币计算);
      ⚠ 已知边界(V1 不告警真凭证后评估):结算 REFUND 行按②拍板含退款负佣金行,售后 refund_amount
      为 Principal 本金口径,两侧或存佣金级系统性偏差——差异告警带双侧金额供人工判读,
      真凭证校准 translator 归一时随②一并定版;落位:erp-finance RefundReconciliationService
      (纯读侧不改表,reconcile 包三 record=RefundSideRow 投影/RefundDiffEvent 差异事件/
      RefundReconciliationAlert 聚合告警,Mapper XML 两聚合语句跨域只读 join 同 ProfitQueryMapper 先例)
      + erp-api RefundReconciliationJob(模式同 #6 AlertJob:每日一扫 fixedDelay(结算 14 天一份
      不支持高频)→ MDC traceId → 全局锁 reconciliation:refund → 勾稽 → 静默期去重
      existsRecent(默认 24h)→ pushAllUsers 聚合单条扇出,明细列 topN 20 声明总笔数,
      bizType/bizId 留空同 #6 聚合口径,通知写失败不阻断);开关 erp.finance.refund-reconciliation.*
      (enabled/interval-ms/quiet-hours,yml 已登记);零 DDL(读侧勾稽无表结构变更,无新前端面——
      差异走通知中心既有页);单测 +16(RefundReconciliationServiceTest 9:三类差异全分支/容差恰界=平/
      跨报告多行归并/跨店铺同订单号隔离/topN 截断声明总笔数 + RefundReconciliationJobTest 7:
      开关短路/锁被占跳过/无差异不扇出/静默期去重/聚合单条扇出/推送失败不上抛/锁必释放)
