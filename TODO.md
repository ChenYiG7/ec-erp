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
      SP-API 限流真值按响应头 x-amzn-RateLimit-Limit 校准;pullProducts(Listings/Reports 选型)、
      pullRefunds(Finances API)、uploadTracking(随 #11)仍占位 UnsupportedOperationException(docs/07 §12)
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
      (uk_shop_platform_order,docs/07 §5 禁先查后插,XML 见 erp-order mapper/ShopOrderMapper.xml,行别名语法 MySQL 8.0.19+)
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
- 模块边界演进(2026-09-04):erp-order 新增 erp-platform-sdk 依赖,**仅消费 UnifiedOrder 落库模型,禁止调平台 API**;
  后续 erp-goods(UnifiedProduct)/erp-aftersale(UnifiedRefund)落库同规约(docs/07 §2.2 已同步)

## #5 SKU 匹配(系统心脏,二期第4周)
- [x] 2026-09-03 前置就位:shop_product / shop_product_sku 域骨架已生成(listing 域对外只读,
      `/api/shop-products`、`/api/shop-product-skus`,待匹配列表 = match_status=0 过滤);
      人工绑定接口已实现 `PUT /api/shop-product-skus/{id}/bind`(回填 sku_id + match_status=2,重复绑定幂等,6 个单测)
- [x] 2026-09-04 店铺商品同步落地:`ShopProductService.saveUnifiedProduct`(唯一写入口)——主表 upsert
      (uk_shop_platform_product,XML 行别名语法)+ uk 反查 id + 级联 upsert SKU 行(uk_shop_seller_sku);
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
- [x] sku_code 全局唯一校验(2026-09-04 收口,TODO(#5) 槽位消除):createSku/updateSku/createProduct 前置查重
      给友好报错(updateSku 排除自身;createProduct 先拒请求内重复再逐码查库,eq 逐码而非 in 聚合——
      in 急切解析列元数据纯 Mockito 单测不可直测,SPU 下 SKU 个位数开销可忽略),并发窗口 uk_sku 兜底
      捕 DuplicateKeyException 转业务异常(同 #10 po_no 模式);ProductServiceTest +8 用例共 14 个

## #6 AI(三期,依赖已就位、代码全部占位)
- [ ] `ErpChatService.chat`:按类注释实现 ChatClient + tools;接口 SSE 流式
- [ ] `tools/` 只读 @Tool 集(查订单/库存/销量/ACOS);写操作必须人工确认
- [ ] `graph/`:SAA Graph Core 补货建议工作流(节点=取数LLM→规则校验→报告)
- [ ] `agent/`(四期):AgentScope ReActAgent 多 Agent(客服/运营)
- [ ] AI 建议闭环:产出一律写 `ai_suggestion` 建议表(待确认/已采纳/已忽略),人工确认后走业务接口,禁直接写业务表(docs/03 §7 草案表)
- [ ] 会话持久化:`ai_chat_session` / `ai_chat_message` 落库;异常监控用"规则引擎先筛 + LLM 评分"两段式控成本(docs/05)
- ⚠️ 版本提示:Spring AI 2.0.0-M5、SAA 2.0.0-M1.1、AgentScope 2.0.0-RC5 均未 GA,
  三期开工前先看有没有 GA 版本,升级只动根 pom 三个属性

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
      遗留(代码 TODO(#7) 注释在 change 内):按 flow_type 差异化维护 qty_locked/qty_transit(发货占用/取消释放,随 #4)、
      TRANSFER 跨仓上层组合
- [x] change() 并发安全原子化(2026-09-04 #13 锁选型同日落地,docs/07 §1① 正确性锁落 DB):
      存量行 check-then-act(selectOne→算术→updateById)重写为一条原子 UPDATE `updateAvailableDelta`
      (`SET qty_on_hand = qty_on_hand + ?, qty_available = qty_available + ? WHERE sku_id = ? AND warehouse_id = ? AND qty_available + ? >= 0`,
      余额条件进 WHERE,行锁至提交,affected=0 回查区分行不存在/余额不足);UPDATE 命中后同事务回读取 after 记流水;
      首建并发**弃 INSERT IGNORE 改捕 DuplicateKeyException 回退原子 UPDATE 重试一轮**(IGNORE 会把非重复键错误一并吞成 warning,
      与 TODO 原指引的偏差,已拍板);两轮仍冲突按业务冲突上抛;单测 11 个(新增并发首建撞 uk/回查窗口重试/持续冲突三分支)
- [x] pull_log 观测列 `duration_ms` / `pull_way`(脚本已加;开发库已生效,2026-09-03 验证)
- [ ] 逻辑删除:实体加 `@TableLogic` + 表加 `deleted` 列(当前为物理删除,先保持简单)
- [x] 商品分类管理校验补齐(2026-09-04 收口,TODO(#7) 分类槽位消除):create/update 父分类存在性校验(非根防孤儿)+
      成环校验(沿父链上走,链上出现自身即拒=自己/自己子孙禁挂;visited 集合兼防存量脏数据环死循环)+
      删除前子分类/商品引用(category_id)拦截;ProductCategoryServiceTest 12 个
- [x] WarehouseService 删除引用校验(2026-09-04 收口,同 #10 遗留"仓库删除引用校验"条目):
      契约扩容 WarehouseApi.countWarehouseRefs(库存 inventory + 采购单 purchase_order 两域合计,单 id eq 查可直测),
      实现收口 erp-api WarehouseApiImpl(新增 InventoryService/PurchaseOrderService 依赖);
      InventoryService.countByWarehouseId / PurchaseOrderService.countByWarehouseId 两域计数方法;
      `WarehouseService.delete` 任一引用即禁删(引导改状态停用);erp-warehouse pom 新引 erp-contract(零接口实现,铁律 2);
      名称唯一性不做——warehouse 无业务唯一键列,同 supplier 需业务确认后改表
- [ ] 参数校验:spring-boot-starter-validation 已引入(2026-09-03,各业务模块+erp-api);GlobalExceptionHandler 已兜 BindException→400;
      Controller 加 `@Valid` 随业务 DTO 约束注解(@NotNull/@Size 等)落地时逐域启用(shop 域 2026-09-03 已随 #8 启用)
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
- [ ] 遗留:supplier 名称唯一性(表无 uk 列,需业务确认后改表)/ ~~仓库删除引用校验~~(✅ 2026-09-04 已随 #7 收口,
      WarehouseApi 扩 countWarehouseRefs)/ 单据 createdBy 与确认人接 SecurityContext 随 #16 前端工程 /
      采购在途 qty_transit 维护随 #7 change() 按 flow_type 差异化(审核占在途→入库转可用)

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
- [ ] 遗留:电子面单/运单号回传平台随 #3 adapter(AmazonClient TODO(#11) 占位)/ 签收回传平台物流轨迹 /
      createdBy 接 SecurityContext 随 #16 前端工程 / 并发建单超发窗口(一期人工低频接受,ship 动账余额兜底)/
      发货单类型 FBA/OVERSEAS 的供应商代发与海外仓发货流程待业务确认后细化

## #12 售后域(二期)
- [x] 2026-09-03 前置就位:aftersale_order 域骨架已生成(readOnly=true 系统写入表,对外只读查询,写入口留 TODO)
- [x] 2026-09-05 平台售后同步 upsert 落地(脱机部分,TODO(#12) 槽位收口,同 #4 saveUnifiedOrder 套路):
      `AftersaleOrderService.saveUnifiedRefund` 唯一写入口——幂等靠 uk_shop_platform_refund upsert 冲突即更新
      (XML 行别名语法,AftersaleOrderMapper.xml 首建);aftersale_no=platformRefundId(uk_aftersale_no 防御兜底);
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
- [ ] 退款金额与财务勾稽(三期 settlement)
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
- [ ] 后续渠道:邮件/短信/IM 推送(在 pushAllUsers 出口扩展,不提前抽象);前端通知中心页面(菜单种子随前端工程统一登记)→ 通知铃铛+已读随 **#16** 落地

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
      验收线七段全通,#16 全清 ✅

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

| OmniTrade 服务 | 功能面 | ec-erp 落位 | 归属 |
|---|---|---|---|
| 库存预警 | 滞销检测/低库存/积压预警 | 规则先筛(#6"两段式"的规则半边),出口 #14 站内通知已就位;qihang 同款规则(销售额为零/发货超时/退款过多)一并纳入规则集 | 三期最先(成本最低) |
| 库存预测/补货建议 | 时序预测+补货量建议 | #6 已规划:SAA Graph 补货建议工作流(取数 LLM→规则校验→报告) | 三期(已对齐) |
| 订单异常检测 | 规则引擎+AI 评分双层融合/风险分级 | #6 已规划:"规则引擎先筛+LLM 评分"两段式控成本 | 三期(已对齐) |
| 智能采购建议 | 采购预测/供应商比价/采购计划 | 建议层叠加 #10 采购域之上(只产建议进 ai_suggestion,不碰状态机与单据) | 三期候选 |
| 智能定价 | 竞品价监控/动态调价/利润优化 | 竞品价拉取随 adapter 平台扩容;定价建议进 ai_suggestion,人工确认后走改价 | 三期候选 |
| 产品描述生成 | SEO 文案/批量生成/平台风格适配 | listing 文案生成,产出进 ai_suggestion,人工采纳后回填 | 三期候选 |
| 智能报表 | 日/周/月报自动生成+Excel 导出 | erp-report 域(休眠),依赖销售/广告数据面先齐 | 四期 BI |
| AI 客服 | RAG 知识库/意图识别/多语言 7×24 | #6 chat/tools 占位承接;向量库选型(pgvector 等)三期开工拍板;多语言随跨境平台接入 | 三期~四期 |
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
