# TODO(#3) pullProducts与pullRefunds联调预备骨架+uploadTracking规划

- 日期: 2026-09-06
- 收尾提交: ba4f68d 后续工作区改动(未提交时以 TODO.md #3 条目为准)

## 拍板
- pullProducts 选型 **Reports GET_MERCHANT_LISTINGS_ALL_DATA**:Listings Items API 只有单 SKU
  get/put/patch/delete,无枚举能力拿不到全量 listing;报表全量快照 + saveUnifiedProduct upsert 幂等,
  PRODUCT 游标退化为拉取频率控制(无增量窗口)。
- pullRefunds 选型 **Finances listFinancialEvents**:Amazon 退款无独立状态流接口,退款事实以记账事件为准;
  事件无原生唯一 ID → 组合幂等键 `OrderId|Sku|PostedDate` 拍板(同秒同单同 SKU 多笔的真冲突随真凭证观测再演进);
  FINISHED+REFUND_ONLY → #12 saveUnifiedRefund 分流 REFUNDED 终态回传,契合"仅平台终态回传条件推进"设计。
- uploadTracking 占位消除规划(实现随 #11):MFN = POST /orders/v0/orders/{orderId}/shipment;
  #11 ship 本地推进后回传,失败记 pull_log 不回滚本地发货;FBA/海外仓不回传。

## 改动
- 新增 SpApiReportsClient(createReport→轮询→getReportDocument→S3 预签名下载不走 SigV4→GZIP 解压)、
  SpApiFinancesClient(记账窗+NextToken 翻页)、AmazonListingTranslator(TSV 按列名解析,asin1 分组)、
  AmazonRefundTranslator;AmazonClient 两占位消除接线,AmazonAdapterConfig 补两 Bean
  (report-poll-interval-ms 可配);uploadTracking 保留占位、规划入方法注释与 docs/04。
- 单测 +17:两个客户端假服务(JDK HttpServer,AIR)+ 两个翻译器(官方模板/schema 推导 fixture);
  AmazonClientTest 构造器与守卫断言同步。
- docs/04 新增「Amazon 拉取/回写面选型拍板」节;TODO.md #3 骨架落地条目;CLAUDE.md sdk 行更新。

## 坑
- 翻译 fixture 是官方模板/schema 推导的自制样例,不是官方脱敏报文——按 docs/07 §8 纪律已在类注释与
  TODO.md 双处标注"真凭证样本到位后 --force 校准一轮",防骨架被当终态。
- 报表轮询是同步阻塞拉单线程(平台侧生成 15~60 分钟)——骨架阶段接受,接真凭证实测时长必要时异步任务化(TODO 已注)。
- 本机网络策略拦 GitHub raw 拉取,finances.json 官方模型无法在线核对——字段名凭公开文档知识落,
  校准点已显式登记,不在无凭据状态下伪装"已核"。

## 未尽
- 售后拉单 Job 仍不接线(随真凭证,防假报文 pull_log 失败噪音,#12 口径);
- listing 币种列缺失 → 站点↔币种映射落库侧推导,随真凭证联调落地(TODO(#3) 槽位);
- SP-API 限流真值按 x-amzn-RateLimit-Limit 响应头校准、Sandbox 冒烟,均随真凭证联调(见 #3 遗留条目)。
