# TODO(#3) SigV4签名器+STS临时凭证落地

- 日期: 2026-09-05
- 收尾提交: 见 git log TODO(#3) SigV4签名器

## 拍板
- #3 实调拆两半:SigV4 签名器/STS 临时凭证是确定性算法,不依赖真凭证,先行落地;HTTP 接线 + NextToken 分页留待卖家账号(注册审核中)到位后联调,半成品封死原则不变(pullOrders 仍 UnsupportedOperationException)。
- 期望签名验证源拍板:官方向量套件 aws-sig-v4-test-suite 离线拉不到(GitHub/gh-proxy 均不通),改用本机 botocore 1.42.72 SigV4Auth(Amazon 官方实现)冻结时钟生成 5 用例期望值——交叉验证强度等同向量套件,脚本入库 `scripts/gen_sigv4_expectations.py` 可随时复现。
- STS 查询串直接复用签名器回传的 `canonicalQueryString`(签名器 = 编码/排序唯一事实源),调用方禁二次拼参,杜绝"签的和发的不一致"。

## 改动
- erp-platform-sdk/adapter/amazon 新增 `SpApiSigner`(纯 JDK 无状态,时间戳入参=固定时钟单测;规范请求五段式 + 四轮 HMAC;STS 临时凭证 x-amz-security-token 参与签名;AwsCredentials record 手写脱敏 toString)+ `StsTokenClient`(AssumeRole 换临时凭证,RestClient,XXE 防护,异常只带 HTTP 状态码)。
- AmazonClient javadoc 状态翻新(SigV4/STS 已落地,实调待真凭证);docs/04 头部现状、TODO.md #3、CLAUDE.md 速查表同步。
- 单测 +10(SpApiSignerTest 7 + StsTokenClientTest 3),erp-platform-sdk 34 全绿,全仓 `mvn test` BUILD SUCCESS。

## 坑
- 首版漏把签名器自己生成的 X-Amz-Date 并入规范头,5 个 botocore 向量全红(SignedHeaders 缺 x-amz-date)——官方向量当场抓获,交叉验证价值实证;修复后 7/7 全对。
- botocore 1.42 冻结时钟:`context['timestamp']` 会被 add_auth 覆盖,须 patch 模块级 `get_current_datetime`(读 auth.py 源码定位后一行搞定);脚本注释已记录,下次直接抄。
- record 上 `@ToString.Exclude` 不生效(docs/08 条目 11),AwsCredentials 含 secretKey/sessionToken 必须手写 toString 脱敏——自查(self-review)阶段抓住,提交前修正。

## 未尽
- SP-API getOrders 实调(剩余):SignedHeaders 拼 URL/请求头 + LastUpdatedAfter 时间窗 + NextToken 分页(≤100/页)+ 真凭证联调,TODO(#3) 占位在 AmazonClient.pullOrders;SP-API 限流真值随实调按 x-amzn-RateLimit-Limit 响应头校准。
- IAM 侧上线清单(账号到位后):IAM 用户/角色与 assume-role 权限策略、AWS 密钥走环境变量/local.properties(键=环境变量名,禁入配置文件)、region/端点配置化。
