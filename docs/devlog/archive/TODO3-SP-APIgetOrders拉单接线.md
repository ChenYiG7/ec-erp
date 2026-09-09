# TODO(#3) SP-API getOrders拉单接线(SpApiOrdersClient)

- 日期: 2026-09-05
- 收尾提交: 见 git log TODO(#3) getOrders拉单接线

## 拍板
- #3 剩余拆界再收一块:getOrders HTTP 接线是确定性逻辑(签名/分页/翻译),不依赖真凭证,按项目"账号就绪前脱机开发不阻塞"原则脱机落地;真凭证联调(应用授权 + IAM/role-arn + 冒烟 + 限流真值校准)仍留待卖家账号。
- AWS 凭证定位拍板:属 SP-API 应用级(非店铺级),不走 erp-shop 店铺凭证加密链路,走环境变量/local.properties(键=环境变量名,与 MYSQL_PASSWORD 同模式);配 role-arn 时经 STS 换临时凭证并缓存,过期前 10 分钟刷新——与 LWA 刷新节奏同口径。
- 调用方禁二次拼参纪律再落一处:URL 查询串直接用 SpApiSigner 回传的 canonicalQueryString(签的和发的一致),StsTokenClient 同款;翻页防御上限 50 页,NextToken 异常自旋时中止本窗口(拉单下轮重试)而非静默截断。

## 改动
- 新增 `SpApiOrdersClient`:getOrders 按 LastUpdatedAfter/Before 更新时间窗 + NextToken 翻页(MaxCount=100),逐单 getOrderItems 挂明细(明细亦带令牌翻页),经 AmazonOrderTranslator 翻译;签名 service=execute-api;LWA token 走 x-amz-access-token 头(不参与 SigV4);临时凭证时 X-Amz-Security-Token 随头发出且参与签名。
- `AmazonClient.pullOrders` 占位消除:委托 SpApiOrdersClient;会话缺 LWA token/未配 AWS 密钥/未配 marketplace-ids 均友好报错(单店失败隔离,记 pull_log);AmazonAdapterConfig 加 SpApiOrdersClient/StsTokenClient 两 Bean;application.yml 补 spapi-*/aws-*/marketplace-ids 配置键(enabled 默认仍 false),local.properties.example 补模板键。
- 单测 +9(SpApiOrdersClientTest 7 + AmazonClientTest 适配 5):查询串/签名 scope/翻页/明细挂载/安全令牌头/错误只透状态码/空配置守卫/防御上限;erp-platform-sdk 43 全绿,全仓 `mvn test` BUILD SUCCESS。

## 坑
- JDK HttpServer 的 context 按 longest-prefix 匹配,`/orders/v0/orders` 会同时吃掉 getOrderItems 路径——改单根 context "/" 手工按 path 分流。
- 断言 `SignedHeaders=host;` 曾险些写错:签名器恒并入 x-amz-date,host 按字典序在首,单头时串实为 `host;x-amz-date`,断言成立依赖这一排序,若签名器排序改动此断言会红——属有意为之的契约断言。
- 错误用例初版响应体不含 "InvalidInput",assertFalse 恒真无断言力——补成带错误码的错误体,让"异常不回显原文"真被验证。

## 未尽
- 真凭证联调(等卖家账号,预计 10 月上旬):Seller Central 应用授权 + IAM 用户/role-arn 权限上线 + getOrders 冒烟;SP-API 限流真值按响应头 x-amzn-RateLimit-Limit 校准(PlatformRateGuard 间隔随真值调)。
- 已知边界:pullOrders 内部 getOrderItems 属页内循环调用,未单独走 PlatformRateGuard(横切统一施加在 pull 入口);窗口单量大时是否需要页内 pacing,随实调真值评估。
- pullProducts(Listings/Reports 选型)/pullRefunds(Finances API)/uploadTracking(随 #11)仍占位 UnsupportedOperationException;#12 售后平台同步 upsert 随退款拉取一并接。
