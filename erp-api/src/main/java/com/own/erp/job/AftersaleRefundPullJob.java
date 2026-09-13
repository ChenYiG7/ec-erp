package com.own.erp.job;

import cn.hutool.core.util.StrUtil;
import com.own.erp.aftersale.service.AftersaleOrderService;
import com.own.erp.common.constant.PullConsts;
import com.own.erp.platform.AdapterRegistry;
import com.own.erp.platform.PlatformClient;
import com.own.erp.platform.ShopSession;
import com.own.erp.platform.unified.UnifiedRefund;
import com.own.erp.shop.service.PullLogService;
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
import java.util.UUID;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : 售后/退款拉取调度(#3 接线,2026-09-12 脱机落地):遍历启用店铺 → 装配会话 →
 *         adapter.pullRefunds(Finances listFinancialEvents 记账时间窗,docs/04 选型拍板)
 *         → AftersaleOrderService.saveUnifiedRefund 落库(uk_shop_platform_refund 幂等,关联订单未入库
 *         返回 false 跳过等下轮窗口重拉,#12 口径)。模式同 OrderPullJob:单店失败隔离 try/catch 全包 +
 *         pull_log 必记 + 连续 3 次失败升级站内告警 + 店铺级 Redisson 锁防跨进程重入。
 *         窗口游标同订单拉单:上次成功 window_end 左叠 WINDOW_OVERLAP_MINUTES 分钟,
 *         首次拉取回溯 erp.pull.first-pull-days;间隔默认 1 小时(退款事件频率远低于订单更新)。
 *         <p><b>总开关 erp.adapter.refund-pull.enabled 默认 false</b>:卡 #3 SP-API 真凭证,
 *         接早了会对假报文产生 pull_log 失败噪音(#12/#3 拍板),关时零噪音直接返回;
 *         false→true 不改代码即可启。@Scheduled 线程不经 TraceIdFilter(#9),入口自行 MDC、finally 强制清。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AftersaleRefundPullJob {

    private static final String MDC_TRACE_ID = "traceId";
    private static final int TRACE_ID_LENGTH = 16;
    /** 店铺锁 key 前缀(LockService 内再拼 erp:lock: 命名空间,docs/07 §1 禁魔法值) */
    private static final String LOCK_KEY_PREFIX = "pull:refund:";

    private final ShopService shopService;
    private final AdapterRegistry adapterRegistry;
    private final AftersaleOrderService aftersaleOrderService;
    private final PullLogService pullLogService;
    private final SysNotificationService notificationService;
    private final LockService lockService;
    private final Clock pullClock;

    /** 总开关:默认关(避免假报文失败噪音,#3 真凭证就位后配置开启,开 false→true 不改代码) */
    @Value("${erp.adapter.refund-pull.enabled:false}")
    private boolean enabled;

    /** 首次拉取回溯天数(与订单拉单同配置,平台侧 Finances 窗口上限由 adapter 内截断) */
    @Value("${erp.pull.first-pull-days:90}")
    private int firstPullDays;

    @Scheduled(fixedDelayString = "${erp.adapter.refund-pull.interval-ms:3600000}")
    public void pullRefunds() {
        if (!enabled) {
            return;
        }
        MDC.put(MDC_TRACE_ID, newTraceId());
        try {
            List<Long> shopIds = shopService.listEnabledShopIds();
            log.info("售后退款拉取调度开始,启用店铺数={}", shopIds.size());
            for (Long shopId : shopIds) {
                try {
                    pullOneShop(shopId);
                } catch (Exception e) {
                    // 双保险:pullOneShop 内部已按阶段 catch,此处兜住未预期异常,单店不中断本轮
                    log.error("售后退款拉取调度未预期异常 shop={}", shopId, e);
                }
            }
        } finally {
            MDC.remove(MDC_TRACE_ID);
        }
    }

    /** 单店拉取全流程:会话 → adapter → 抢锁 → 算窗口 → 拉取 → 翻译落库 → 记 pull_log */
    private void pullOneShop(Long shopId) {
        ShopSession session;
        try {
            session = shopService.getShopSession(shopId);
        } catch (Exception e) {
            // 会话装配失败(凭证缺失/解密失败/Token 刷新失败)同样记 pull_log,连续 3 次升级站内告警(同 OrderPullJob)
            LocalDateTime now = LocalDateTime.now(pullClock);
            pullLogService.recordFailure(shopId, PullConsts.DATA_TYPE_REFUND, now, now,
                    "会话装配失败: " + e.getClass().getSimpleName() + ": " + e.getMessage(), 0, PullConsts.PULL_WAY_JOB);
            log.warn("店铺会话装配失败,本轮跳过 shop={} :{}", shopId, e.getMessage());
            alertIfContinuousFailure(shopId, e);
            return;
        }
        PlatformClient client = adapterRegistry.get(session.getPlatform()).orElse(null);
        if (client == null) {
            // adapter 未启用(@ConditionalOnProperty 未注册)或平台未接入,debug 级跳过不打扰(零噪音)
            log.debug("平台 adapter 未接入,跳过售后拉取 shop={} platform={}", shopId, session.getPlatform());
            return;
        }
        LockService.Lease lease = lockService.tryAcquire(LOCK_KEY_PREFIX + shopId);
        if (lease == null) {
            log.debug("售后拉取锁被占(或锁不可用且 fail-closed),跳过本轮 shop={}", shopId);
            return;
        }
        long begin = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now(pullClock);
        LocalDateTime cursor = pullLogService.findLastSuccessWindowEnd(shopId, PullConsts.DATA_TYPE_REFUND);
        LocalDateTime windowEnd = now;
        LocalDateTime windowStart = cursor == null
                ? now.minusDays(firstPullDays)
                : cursor.minusMinutes(PullConsts.WINDOW_OVERLAP_MINUTES);
        try {
            List<UnifiedRefund> refunds = client.pullRefunds(session, toInstant(windowStart), toInstant(windowEnd));
            int count = 0;
            for (UnifiedRefund refund : refunds) {
                // 以会话店铺回填 shopId(防报文内脏值);false = 关联订单未入库跳过等下轮(#12 拍板),计数仍含拉取条数
                refund.setShopId(shopId);
                aftersaleOrderService.saveUnifiedRefund(refund);
                count++;
            }
            long cost = System.currentTimeMillis() - begin;
            pullLogService.recordSuccess(shopId, PullConsts.DATA_TYPE_REFUND, windowStart, windowEnd, count, cost,
                    PullConsts.PULL_WAY_JOB);
            log.info("售后退款拉取成功 shop={} window=[{} ~ {}] count={} cost={}ms",
                    shopId, windowStart, windowEnd, count, cost);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - begin;
            // 失败不推进游标(recordFailure success=0),下轮左叠窗口自动重试
            pullLogService.recordFailure(shopId, PullConsts.DATA_TYPE_REFUND, windowStart, windowEnd,
                    e.getClass().getSimpleName() + ": " + e.getMessage(), cost, PullConsts.PULL_WAY_JOB);
            log.error("售后退款拉取失败 shop={} window=[{} ~ {}] cost={}ms", shopId, windowStart, windowEnd, cost, e);
            alertIfContinuousFailure(shopId, e);
        } finally {
            lease.close();
        }
    }

    /** 连续失败达阈值升级站内告警(口径同 OrderPullJob,通知写失败不阻断主流程) */
    private void alertIfContinuousFailure(Long shopId, Exception cause) {
        try {
            if (!pullLogService.shouldAlertContinuousFailure(shopId, PullConsts.DATA_TYPE_REFUND,
                    PullConsts.FAILURE_ALERT_THRESHOLD)) {
                return;
            }
            notificationService.pushAllUsers(SysNotificationService.TYPE_PULL_FAIL, "售后退款拉取连续失败告警",
                    StrUtil.format("店铺 {} 售后退款拉取连续失败 {} 次,最近错误:{}", shopId,
                            PullConsts.FAILURE_ALERT_THRESHOLD, cause.getMessage()),
                    SysNotificationService.BIZ_TYPE_SHOP, shopId);
            log.warn("已推送售后退款拉取连续失败站内告警 shop={}", shopId);
        } catch (Exception ex) {
            log.error("售后拉取失败站内告警写入异常 shop={}", shopId, ex);
        }
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
