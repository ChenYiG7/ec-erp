package com.own.erp.job;

import cn.hutool.core.util.StrUtil;
import com.own.erp.common.constant.PullConsts;
import com.own.erp.finance.service.SettlementService;
import com.own.erp.platform.AdapterRegistry;
import com.own.erp.platform.PlatformClient;
import com.own.erp.platform.ShopSession;
import com.own.erp.platform.unified.UnifiedSettlement;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 结算报告拉取调度(#19 周期口径,计划书 docs/plans/19-profit-caliber.md §2.4):
 *         遍历启用店铺 → 装配会话 → adapter.pullSettlements(契约无时间窗,平台按打款周期生成离散正本)
 *         → SettlementService.saveUnifiedSettlement 落库(报告 uk 幂等,勾稽不平 FAILED 可重拉覆盖)。
 *         模式同 OrderPullJob:单店失败隔离 try/catch 全包 + pull_log 必记(duration_ms/完整错误),
 *         连续 3 次失败升级站内告警;店铺级 Redisson 锁 LockService.tryAcquire 防跨进程重入。
 *         与拉单差异:无窗口游标(window_start=window_end=本次时刻,同 SHIPMENT 口径)、
 *         频率低频(报告 14 天一份,fixedDelay 默认每日)。
 *         <p><b>总开关 erp.adapter.settlement-pull.enabled 默认 false</b>:卡 #3 SP-API 真凭证,
 *         关时零噪音直接返回(同 ReportDigestJob 保护性默认);false→true 不改代码即可启。
 *         adapter 未实现 pullSettlements 的平台抛 UnsupportedOperationException,debug 级跳过不记失败。
 *         @Scheduled 线程不经 TraceIdFilter(#9),入口自行 MDC.put traceId、finally 强制清。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementPullJob {

    private static final String MDC_TRACE_ID = "traceId";
    private static final int TRACE_ID_LENGTH = 16;
    /** 店铺锁 key 前缀(LockService 内再拼 erp:lock: 命名空间,docs/07 §1 禁魔法值) */
    private static final String LOCK_KEY_PREFIX = "pull:settlement:";

    private final ShopService shopService;
    private final AdapterRegistry adapterRegistry;
    private final SettlementService settlementService;
    private final PullLogService pullLogService;
    private final SysNotificationService notificationService;
    private final LockService lockService;
    private final Clock pullClock;

    /** 总开关:默认关,#3 真凭证就位后配置开启(开 false→true 不改代码) */
    @Value("${erp.adapter.settlement-pull.enabled:false}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${erp.adapter.settlement-pull.interval-ms:86400000}")
    public void pullSettlements() {
        if (!enabled) {
            return;
        }
        MDC.put(MDC_TRACE_ID, newTraceId());
        try {
            List<Long> shopIds = shopService.listEnabledShopIds();
            log.info("结算报告拉取调度开始,启用店铺数={}", shopIds.size());
            for (Long shopId : shopIds) {
                try {
                    pullOneShop(shopId);
                } catch (Exception e) {
                    // 双保险:pullOneShop 内部已按阶段 catch,此处兜住未预期异常,单店不中断本轮
                    log.error("结算报告拉取调度未预期异常 shop={}", shopId, e);
                }
            }
        } finally {
            MDC.remove(MDC_TRACE_ID);
        }
    }

    /** 单店全流程:会话 → adapter → 抢锁 → 拉取翻译落库 → 记 pull_log;不支持的平台静默跳过 */
    private void pullOneShop(Long shopId) {
        ShopSession session;
        try {
            session = shopService.getShopSession(shopId);
        } catch (Exception e) {
            LocalDateTime now = LocalDateTime.now(pullClock);
            pullLogService.recordFailure(shopId, PullConsts.DATA_TYPE_SETTLEMENT, now, now,
                    "会话装配失败: " + e.getClass().getSimpleName() + ": " + e.getMessage(), 0, PullConsts.PULL_WAY_JOB);
            log.warn("店铺会话装配失败,本轮跳过 shop={} :{}", shopId, e.getMessage());
            alertIfContinuousFailure(shopId, e);
            return;
        }
        PlatformClient client = adapterRegistry.get(session.getPlatform()).orElse(null);
        if (client == null) {
            log.debug("平台 adapter 未接入,跳过结算拉取 shop={} platform={}", shopId, session.getPlatform());
            return;
        }
        LockService.Lease lease = lockService.tryAcquire(LOCK_KEY_PREFIX + shopId);
        if (lease == null) {
            log.debug("结算拉取锁被占(或锁不可用且 fail-closed),跳过本轮 shop={}", shopId);
            return;
        }
        long begin = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now(pullClock);
        try {
            List<UnifiedSettlement> settlements = client.pullSettlements(session);
            int count = 0;
            for (UnifiedSettlement settlement : settlements) {
                // 落库以会话店铺为准(防报文内脏值);返回 false=已 PARSED 幂等跳过,计数仍含拉取份数
                settlementService.saveUnifiedSettlement(shopId, settlement);
                count++;
            }
            long cost = System.currentTimeMillis() - begin;
            pullLogService.recordSuccess(shopId, PullConsts.DATA_TYPE_SETTLEMENT, now, now, count, cost,
                    PullConsts.PULL_WAY_JOB);
            log.info("结算报告拉取成功 shop={} count={} cost={}ms", shopId, count, cost);
        } catch (UnsupportedOperationException e) {
            // 平台未实现结算拉取(SPI default 语义):非故障,debug 静默不记失败噪音
            log.debug("平台暂不支持结算报告拉取,跳过 shop={} platform={}", shopId, session.getPlatform());
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - begin;
            pullLogService.recordFailure(shopId, PullConsts.DATA_TYPE_SETTLEMENT, now, now,
                    e.getClass().getSimpleName() + ": " + e.getMessage(), cost, PullConsts.PULL_WAY_JOB);
            log.error("结算报告拉取失败 shop={} cost={}ms", shopId, cost, e);
            alertIfContinuousFailure(shopId, e);
        } finally {
            lease.close();
        }
    }

    /** 连续失败达阈值升级站内告警(口径同 OrderPullJob,通知写失败不阻断主流程) */
    private void alertIfContinuousFailure(Long shopId, Exception cause) {
        try {
            if (!pullLogService.shouldAlertContinuousFailure(shopId, PullConsts.DATA_TYPE_SETTLEMENT,
                    PullConsts.FAILURE_ALERT_THRESHOLD)) {
                return;
            }
            notificationService.pushAllUsers(SysNotificationService.TYPE_PULL_FAIL, "结算报告拉取连续失败告警",
                    StrUtil.format("店铺 {} 结算报告拉取连续失败 {} 次,最近错误:{}", shopId,
                            PullConsts.FAILURE_ALERT_THRESHOLD, cause.getMessage()),
                    SysNotificationService.BIZ_TYPE_SHOP, shopId);
            log.warn("已推送结算报告拉取连续失败站内告警 shop={}", shopId);
        } catch (Exception ex) {
            log.error("结算拉取失败站内告警写入异常 shop={}", shopId, ex);
        }
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, TRACE_ID_LENGTH);
    }
}
