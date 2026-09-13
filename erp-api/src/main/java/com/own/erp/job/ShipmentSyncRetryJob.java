package com.own.erp.job;

import com.own.erp.fulfill.service.DeliveryOrderService;
import com.own.erp.shipment.ShipmentSyncService;
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
 * @Date : 2026/9/12
 * @Description : 发货回传补偿扫(#11 激活期余量,docs/plans/p3-fulfill-extensions §2.3):
 *         定时扫已发货未回传成功的发货单重试——PENDING=事件丢失(停机期提交被拒/进程崩溃于 AFTER_COMMIT 后)
 *         或跳过未遂(如店铺平台 adapter 后接入),FAILED=回传失败带计数退避重试;状态筛选/退避/上限语义
 *         全在 DeliveryOrderMapper.selectSyncRetryCandidateIds,本 Job 只管开关与调度节奏。
 *         - 默认关(erp.shipment.retry-enabled,新 Job 噪音纪律):真凭证联调激活时随 sync-async 一并拍板翻开;
 *         - 双闸:sync-enabled=false(只落本地不回传灰度期)时补偿扫无意义,直接跳过;
 *         - 重试即整体重跑 ShipmentSyncService.sync(读单→装配→回传→记 pull_log→回写状态),
 *           成功翻 SUCCESS、失败计数+1 并按计数退避,达上限停扫保持 FAILED 待人工;
 *         - 无锁设计:pullScheduler 单线程 + fixedDelay 进程内天然防重入,跨进程重复扫靠
 *           sync_status 条件更新守卫 + 平台侧幂等兜底(一期单实例,docs/07 §1 ③);
 *         - @Scheduled 线程不经 TraceIdFilter(#9),入口自行 MDC.put traceId、finally 强制清
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentSyncRetryJob {

    private static final String MDC_TRACE_ID = "traceId";
    private static final int TRACE_ID_LENGTH = 16;

    private final DeliveryOrderService deliveryOrderService;
    private final ShipmentSyncService shipmentSyncService;
    private final Clock pullClock;

    /** 补偿扫总开关:默认关(新 Job 噪音纪律),真凭证联调激活时拍板翻开 */
    @Value("${erp.shipment.retry-enabled:false}")
    private boolean retryEnabled;

    /** 回传总开关同口径复检:sync-enabled=false(只落本地不回传)时重试无意义,双闸 */
    @Value("${erp.shipment.sync-enabled:true}")
    private boolean syncEnabled;

    /** 单单重试上限(超限停扫保持 FAILED 待人工) */
    @Value("${erp.shipment.retry-max-count:5}")
    private int retryMaxCount;

    /** 退避基数分钟:第 N 次重试需距上次尝试 N×该值(计算在 Mapper 候选 SQL) */
    @Value("${erp.shipment.retry-backoff-minutes:10}")
    private int retryBackoffMinutes;

    /** 单轮扫描上限(防一次性大批量回传放大平台限流压力) */
    @Value("${erp.shipment.retry-batch-size:100}")
    private int retryBatchSize;

    @Scheduled(fixedDelayString = "${erp.shipment.retry-interval-ms:1800000}")
    public void retryPendingSync() {
        if (!retryEnabled) {
            return;
        }
        if (!syncEnabled) {
            log.debug("发货回传总开关关闭(只落本地不回传),补偿扫跳过");
            return;
        }
        MDC.put(MDC_TRACE_ID, newTraceId());
        try {
            List<Long> candidateIds = deliveryOrderService.listSyncRetryCandidateIds(
                    retryMaxCount, retryBackoffMinutes, LocalDateTime.now(pullClock), retryBatchSize);
            if (candidateIds.isEmpty()) {
                return;
            }
            log.info("发货回传补偿扫开始,待重试单数={}", candidateIds.size());
            for (Long deliveryId : candidateIds) {
                try {
                    shipmentSyncService.sync(deliveryId);
                } catch (Exception e) {
                    // 单单隔离:sync 内部已吞业务失败,此处兜住未预期异常,不中断本轮其余单据
                    log.error("发货回传补偿重试未预期异常 delivery={}", deliveryId, e);
                }
            }
        } finally {
            // Tomcat/调度线程复用,残留会串任务(#9 同款纪律)
            MDC.remove(MDC_TRACE_ID);
        }
    }

    /** 与 TraceIdFilter 生成规则同源:16 位 hex(任务日志与同轮 HTTP 排障日志可读性一致) */
    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, TRACE_ID_LENGTH);
    }
}
