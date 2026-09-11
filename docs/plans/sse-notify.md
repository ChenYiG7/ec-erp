# SSE 实时推送通知实施计划书

| 元信息 | 值 |
|---|---|
| TODO 条目 | SSE 浏览器实时推送通知(qihang 对标;前端现有 60s 轮询基础,erp-web 承接) |
| 优先级 | P2(docs/10 G8 标 P3 但 §5/docs/06/TODO.md 均列 P2——以 TODO.md 为准) |
| 前置依赖 | 无 |
| 目标一句话 | 站内通知落库后实时推到在线浏览器(SSE),前端保留 60s 轮询作断线降级,登录即连、断线指数退避重连 |
| 明确不做 | 多实例跨进程广播(当前单进程,留 TODO 槽位);WebSocket 升级;按用户粒度的定向推送语义变更(维持 pushAllUsers 全量扇出) |

## 一、背景与现状(2026-09-10 代码事实)

**后端**
- `erp-system` `SysNotificationService.pushAllUsers(notifyType, title, content, bizType, bizId)→int`:写侧唯一入口,扇出给全部启用用户逐条 insert + 发布 `NotifyPushedEvent`(**站内零用户也发布,事件为源**);Webhook/Mail 两监听器 `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)`。
- 表 `sys_notification`(user_id/title/content/notify_type/biz_type/biz_id/read_status + idx_user_read/idx_created);Controller `/api/system/notifications` 分页、`/unread-count`、`/{id}/read`、`/read-all`。
- **后端 SSE 先例**:`ErpChatController` L81 `POST .../chat`,`produces=text/event-stream` 返 `Flux<String>`——通知 SSE 直接复制此形态(MVC 返 Flux 可行,工程已验证)。

**前端**
- 轮询:`src/stores/modules/notification.ts` `setInterval(refresh, 60_000)` 打 `unread-count`;visibilitychange 隐藏暂停/回前台立刷;`start()` 在 `src/layouts/index.vue` L42 登录布局就绪后调。
- **流式通道已有**:`src/utils/sse.ts` `postSse(url, body, onChunk)`——fetch 手解(EventSource 仅 GET 带不了 Bearer),裸 `data:` 帧+[DONE] 兼容,鉴权/错误单点收口;chat/agent 两域在用。`utils/request/base.ts`(axios)无流式,**不动 base.ts**。

## 二、方案设计

### 2.1 后端订阅端点(erp-system)

- `NotificationSseController`:`POST /api/system/notifications/subscribe`,`produces=text/event-stream`,返 `Flux<ServerSentEvent<String>>`(payload=JSON:`{notifyType,title,content,bizType,bizId}`;心跳帧 `{type:HEARTBEAT}`)。
- 连接注册表 `NotificationSseRegistry`(erp-system 内 plain 组件):`Map<Long userId, List<SinksProcessor>>` 注册/注销;同一用户多 tab 上限 N=5 连接,超限踢最旧(防泄漏)。
- **推送时机(关键,与 Webhook/Mail 同口径)**:`@TransactionalEventListener(phase=AFTER_COMMIT, fallbackExecution=true)` 监听 `NotifyPushedEvent` → 广播给注册表内**全部在线连接**(pushAllUsers 语义 = 全量扇出,不做用户过滤;事件里没有用户列表,别造)。fallbackExecution=true 保持与现有两监听器一致(非事务上下文发布也能推)。
- 心跳:30s 一帧防代理/网关空闲断连;连接关闭/错误时从注册表移除(终态回调)。
- 开关:`erp.notify.sse.enabled` 默认 true;关闭时端点返回 404 语义(前端走轮询降级)。

### 2.2 前端接入(erp-web)

- `notification.ts` store 扩展:登录后 `start()` 里在轮询之外启动 SSE——`postSse('/api/system/notifications/subscribe', {}, onEvent)`;onEvent 收到通知帧 → 调既有 `refresh()`(unread-count + 列表失效)→ Message.vue 弹层自然出现(UI 零改动或最小改动)。
- **降级联动(核心设计)**:SSE 连接健康 → 挂起 60s 轮询;连接断开 → 立即恢复轮询 + 指数退避重连(1s 起,×2,封顶 30s,抖动 ±20%);重连成功 → 再挂起轮询。visibilitychange 既有语义保留(隐藏时两个通道都可暂停,回前台立即 refresh+确保 SSE 连接)。
- 登出/401:关 SSE、清注册(HTTP 401 双错误形态收口 base.ts 既有,不重复处理)。

### 2.3 多实例预留(只留注释,不实现)

- Registry 为进程内 Map;扩多实例时改 Redis pub/sub 广播(频道 `erp:notify:sse`),**留 TODO(新编号) 注释 + 指引**,不写实现。

## 三、实施步骤

1. 后端:NotificationSseRegistry(注册/注销/广播/心跳/上限)→ NotificationSseController(订阅端点)→ NotifyPushedEvent 监听器(AFTER_COMMIT+fallback)→ yml 开关。
2. 单测:Registry 注册/踢旧/移除;监听器在开关关闭时不推(事件仍进 Webhook/Mail 不受影响——**三渠道互不干扰回归**)。
3. 前端:notification.ts 集成 postSse + 重连状态机 + 轮询联动;dev tools 下模拟断连验证。
4. 联调冒烟:两个浏览器(或 tab)在线,A 触发业务事件(如审核/告警),B 1s 内弹通知;kill 后端再起,B 自动重连恢复。
5. 门禁:后端 mvn 编译+单测;前端门禁四件。

## 四、表结构草案

无(复用 sys_notification,零 DDL)。

## 五、验收标准

- 在线接收:pushAllUsers 触发后在线浏览器 ≤2s 收到(心跳不阻塞业务帧)。
- 降级:SSE 关闭(开关)或断连时轮询自动接管,通知不丢(轮询兜底);重连后自动切回。
- 断线重连:杀后端→前端指数退避无雪崩(单实例下重连频率封顶 30s);恢复后 30s 内重连成功。
- 现有三渠道回归:站内落库/Webhook/邮箱行为与本改动前一致(监听器互不依赖)。
- 前端门禁四件绿;vue-tsc 无新增错误;SSE 代码不 import axios(docs/09 §2 红线)。

## 六、红线提醒

- 前端:SSE 一律走 `utils/sse.ts` 通道(fetch 手解),**禁用 EventSource**(带不了 Bearer)、禁改 base.ts、禁拼 URL(api 收口 docs/09 §2)。
- 后端:监听器 AFTER_COMMIT+fallbackExecution 与 Webhook/Mail 同口径;失败只记日志不回滚业务事务;通知通道禁在新事务里反查业务。
- 连接资源:每用户连接上限+终态清理(防 Half-open 泄漏);心跳间隔与反代超时(若有)对齐。
- `erp.notify.sse.enabled` 等 erp.* 键禁入凭证;开关语义=整通道降级到轮询,而非报错。

## 七、交接边界

1. 多 tab 上限值(默认 5)与踢旧策略确认。
2. payload 是否携带 unreadCount(现设计:前端收到帧后主动 refresh,不冗余带计数)。
3. 跨进程广播(Redis pub/sub)留 TODO 编号登记,确认不做实现。
4. 定向推送(按用户/角色)语义变更随 #27 数据权限另议。
