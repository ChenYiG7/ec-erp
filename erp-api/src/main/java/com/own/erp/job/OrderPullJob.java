package com.own.erp.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.own.erp.common.constant.PullConsts;
import com.own.erp.order.service.ShopOrderService;
import com.own.erp.platform.AdapterRegistry;
import com.own.erp.platform.PlatformClient;
import com.own.erp.platform.ShopSession;
import com.own.erp.platform.unified.UnifiedOrder;
import com.own.erp.shop.service.PullLogService;
import com.own.erp.shop.service.ShopProductSkuService;
import com.own.erp.shop.service.ShopService;
import com.own.erp.system.service.SysNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 拉单调度(#4,一期跑在 erp-api 进程内,量大后迁 erp-worker :8089):
 *         - 每店 15 分钟一拉(fixedDelay,上一轮结束再计时);窗口 = 上次成功 window_end 左叠
 *           WINDOW_OVERLAP_MINUTES 分钟(平台时钟漂移兜底,重叠段靠订单唯一键幂等去重,docs/04 拉单策略);
 *           首次拉取(无成功游标)回溯 erp.pull.first-pull-days(默认 90 天)
 *         - 单店失败隔离:会话装配失败/adapter 未实现/拉取落库异常均只影响本店,try/catch 全包;
 *           拉取结果无论成败必记 pull_log(含 duration_ms 与完整错误信息,docs/07 §3 排障第一入口)
 *         - 幂等:落库靠 uk(shop_id, platform_order_id) upsert(最后防线);调度防重入 = 单线程调度器
 *           (进程内第一道)+ 店铺级 Redisson 锁 LockService.tryAcquire(跨进程,2026-09-04 #13 二次定版:
 *           一期即定型,免二期灰度迁移窗口里进程内锁静默失效;Redis 故障降级语义见 LockService)
 *         - @Scheduled 线程不经 TraceIdFilter(#9),入口自行 MDC.put traceId、finally 强制清
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPullJob {

    private static final String MDC_TRACE_ID = "traceId";
    private static final int TRACE_ID_LENGTH = 16;

    /** 店铺锁 key 前缀(LockService 内再拼 erp:lock: 命名空间,docs/07 §1 禁魔法值) */
    private static final String LOCK_KEY_PREFIX = "pull:order:";

    private final ShopService shopService;
    private final AdapterRegistry adapterRegistry;
    private final ShopOrderService shopOrderService;
    private final ShopProductSkuService shopProductSkuService;
    private final PullLogService pullLogService;
    private final SysNotificationService notificationService;
    private final LockService lockService;
    private final Clock pullClock;

    /** 首次拉取回溯天数:各平台上限不同,超出部分由 adapter 内截断(docs/04 拉单策略 3) */
    @Value("${erp.pull.first-pull-days:90}")
    private int firstPullDays;

    @Scheduled(fixedDelayString = "${erp.pull.order-interval-ms:900000}")
    public void pullOrders() {
        MDC.put(MDC_TRACE_ID, newTraceId());
        try {
            List<Long> shopIds = shopService.listEnabledShopIds();
            log.info("拉单调度开始,启用店铺数={}", shopIds.size());
            for (Long shopId : shopIds) {
                try {
                    pullOneShop(shopId);
                } catch (Exception e) {
                    // 双保险:pullOneShop 内部已按阶段 catch,此处兜住未预期异常,单店不中断本轮
                    log.error("拉单调度未预期异常 shop={}", shopId, e);
                }
            }
        } finally {
            // Tomcat/调度线程复用,残留会串任务(#9 同款纪律)
            MDC.remove(MDC_TRACE_ID);
        }
    }

    /** 单店拉取全流程:装配会话(内含 Token 刷新)→ 取 adapter → 抢锁 → 算窗口 → 拉取 → 翻译落库 → 记 pull_log */
    private void pullOneShop(Long shopId) {
        ShopSession session;
        try {
            session = shopService.getShopSession(shopId);
        } catch (Exception e) {
            // #3:会话装配失败(凭证缺失/解密失败/Token 刷新失败)同样记 pull_log——
            // docs/04「刷新失败告警并停该店铺拉单」:连续 3 次经告警判定升级站内通知,窗口记本轮时刻不推进游标
            LocalDateTime now = LocalDateTime.now(pullClock);
            pullLogService.recordFailure(shopId, PullConsts.DATA_TYPE_ORDER, now, now,
                    "会话装配失败: " + e.getClass().getSimpleName() + ": " + e.getMessage(), 0, PullConsts.PULL_WAY_JOB);
            log.warn("店铺会话装配失败,本轮跳过 shop={} :{}", shopId, e.getMessage());
            alertIfContinuousFailure(shopId, e);
            return;
        }
        PlatformClient client = adapterRegistry.get(session.getPlatform()).orElse(null);
        if (client == null) {
            // adapter 未实现(如仅配置了店铺还没开发对接),正常现象,debug 级不打扰
            log.debug("平台 adapter 未接入,跳过 shop={} platform={}", shopId, session.getPlatform());
            return;
        }
        LockService.Lease lease = lockService.tryAcquire(LOCK_KEY_PREFIX + shopId);
        if (lease == null) {
            log.debug("店铺拉单锁被占(或锁不可用且 fail-closed),跳过本轮 shop={}", shopId);
            return;
        }
        long begin = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now(pullClock);
        LocalDateTime cursor = pullLogService.findLastSuccessWindowEnd(shopId, PullConsts.DATA_TYPE_ORDER);
        LocalDateTime windowEnd = now;
        LocalDateTime windowStart = cursor == null
                ? now.minusDays(firstPullDays)
                : cursor.minusMinutes(PullConsts.WINDOW_OVERLAP_MINUTES);
        try {
            List<UnifiedOrder> orders = client.pullOrders(session, toInstant(windowStart), toInstant(windowEnd));
            // 明细 SKU 翻译映射一次批量取回,落库按 seller_sku 回填 sku_id(未绑定 NULL,订单照常入库)
            Map<String, Long> skuIdBySellerSku = shopProductSkuService.mapSellerSkuToSkuId(shopId, collectSellerSkus(orders));
            int count = 0;
            for (UnifiedOrder order : orders) {
                // 以会话店铺为准,防平台报文内脏 shopId 覆盖
                order.setShopId(shopId);
                shopOrderService.saveUnifiedOrder(shopId, order, skuIdBySellerSku);
                count++;
            }
            long cost = System.currentTimeMillis() - begin;
            pullLogService.recordSuccess(shopId, PullConsts.DATA_TYPE_ORDER, windowStart, windowEnd, count, cost,
                    PullConsts.PULL_WAY_JOB);
            log.info("拉单成功 shop={} window=[{} ~ {}] count={} cost={}ms", shopId, windowStart, windowEnd, count, cost);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - begin;
            // 失败不推进游标(recordFailure success=0),下轮左叠窗口自动重试;整单失败原子性好于半落库
            pullLogService.recordFailure(shopId, PullConsts.DATA_TYPE_ORDER, windowStart, windowEnd,
                    e.getClass().getSimpleName() + ": " + e.getMessage(), cost, PullConsts.PULL_WAY_JOB);
            log.error("拉单失败 shop={} window=[{} ~ {}] cost={}ms", shopId, windowStart, windowEnd, cost, e);
            alertIfContinuousFailure(shopId, e);
        } finally {
            lease.close();
        }
    }

    /**
     * 连续失败达阈值升级站内告警(#14):恰达阈值轮次告警一次(PullLogService 判定),
     * 通知写失败只记日志不阻断拉单主流程
     */
    private void alertIfContinuousFailure(Long shopId, Exception cause) {
        try {
            if (!pullLogService.shouldAlertContinuousFailure(shopId, PullConsts.DATA_TYPE_ORDER,
                    PullConsts.FAILURE_ALERT_THRESHOLD)) {
                return;
            }
            notificationService.pushAllUsers(SysNotificationService.TYPE_PULL_FAIL, "订单拉取连续失败告警",
                    StrUtil.format("店铺 {} 订单拉取连续失败 {} 次,最近错误:{}", shopId,
                            PullConsts.FAILURE_ALERT_THRESHOLD, cause.getMessage()),
                    SysNotificationService.BIZ_TYPE_SHOP, shopId);
            log.warn("已推送订单拉单连续失败站内告警 shop={}", shopId);
        } catch (Exception ex) {
            log.error("拉单失败站内告警写入异常 shop={}", shopId, ex);
        }
    }

    /** 汇总本批订单全部 seller_sku,供一次性批量查映射(禁循环查库 N+1,docs/07 §12) */
    private Set<String> collectSellerSkus(List<UnifiedOrder> orders) {
        if (CollUtil.isEmpty(orders)) {
            return Set.of();
        }
        return orders.stream()
                .filter(order -> CollUtil.isNotEmpty(order.getItems()))
                .flatMap(order -> order.getItems().stream())
                .map(UnifiedOrder.Item::getSellerSku)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
    }

    /** 库内窗口 → 平台调用入参 Instant(会话时区,见 PullConsts.ZONE) */
    private static Instant toInstant(LocalDateTime window) {
        return window.atZone(PullConsts.ZONE).toInstant();
    }

    /** 与 TraceIdFilter 生成规则同源:16 位 hex(任务日志与同轮 HTTP 排障日志可读性一致) */
    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, TRACE_ID_LENGTH);
    }
}
