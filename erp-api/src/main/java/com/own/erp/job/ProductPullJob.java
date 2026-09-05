package com.own.erp.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.own.erp.common.constant.PullConsts;
import com.own.erp.goods.service.ProductService;
import com.own.erp.platform.AdapterRegistry;
import com.own.erp.platform.PlatformClient;
import com.own.erp.platform.ShopSession;
import com.own.erp.platform.unified.UnifiedProduct;
import com.own.erp.shop.service.PullLogService;
import com.own.erp.shop.service.ShopProductService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : listing 拉取调度(#5):与 OrderPullJob 同构但游标独立(pull_log data_type=PRODUCT)。
 *         - 频率低于订单(默认 1 小时,listings 变化慢);窗口/锁/失败隔离/pull_log 纪律同 #4,见 OrderPullJob 类注释
 *         - 拉取落库后紧接自动匹配(顺序有讲究:先同步出映射行,再匹配):erp-goods 精确匹配
 *           (ProductService.mapSkuCodesToIds,seller_sku==sku_code)→ ShopProductSkuService.autoMatch
 *           回填 match_status=1,人工绑定不被覆盖
 *         - 编排属跨域调用,收口本类(铁律 2);第三个同构任务(REFUND,#12 随 #3)出现时再议抽公共模板
 *         - 锁选型(docs/07 §1 ③):拉单为效率锁(uk upsert 兜底),店铺级 Redisson 锁 LockService
 *           (2026-09-04 #13 二次定版一期即定型,同 OrderPullJob;临界区含分钟级平台外呼,
 *           禁 DB 悲观锁/手写 setnx)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductPullJob {

    private static final String MDC_TRACE_ID = "traceId";
    private static final int TRACE_ID_LENGTH = 16;

    /** 店铺锁 key 前缀(LockService 内再拼 erp:lock: 命名空间);与 OrderPullJob key 不同,各自独立锁域不互斥 */
    private static final String LOCK_KEY_PREFIX = "pull:product:";

    private final ShopService shopService;
    private final AdapterRegistry adapterRegistry;
    private final ShopProductService shopProductService;
    private final ShopProductSkuService shopProductSkuService;
    private final ProductService productService;
    private final PullLogService pullLogService;
    private final SysNotificationService notificationService;
    private final LockService lockService;
    private final Clock pullClock;

    /** 首次拉取回溯天数:各平台上限不同,超出部分由 adapter 内截断(docs/04 拉单策略 3) */
    @Value("${erp.pull.first-pull-days:90}")
    private int firstPullDays;

    /** listing 变化慢,默认 1 小时一拉,独立于订单间隔 */
    @Scheduled(fixedDelayString = "${erp.pull.product-interval-ms:3600000}")
    public void pullProducts() {
        MDC.put(MDC_TRACE_ID, newTraceId());
        try {
            List<Long> shopIds = shopService.listEnabledShopIds();
            log.info("listing 调度开始,启用店铺数={}", shopIds.size());
            for (Long shopId : shopIds) {
                try {
                    pullOneShop(shopId);
                } catch (Exception e) {
                    // 双保险:单店任何未预期异常不中断本轮其他店铺
                    log.error("listing 调度未预期异常 shop={}", shopId, e);
                }
            }
        } finally {
            MDC.remove(MDC_TRACE_ID);
        }
    }

    /** 单店拉取全流程:装配会话(内含 Token 刷新)→ 取 adapter → 抢锁 → 算窗口 → 拉取 → 同步落库 → 自动匹配 → 记 pull_log */
    private void pullOneShop(Long shopId) {
        ShopSession session;
        try {
            session = shopService.getShopSession(shopId);
        } catch (Exception e) {
            // #3:会话装配失败(凭证缺失/解密失败/Token 刷新失败)同样记 pull_log,同 OrderPullJob
            LocalDateTime now = LocalDateTime.now(pullClock);
            pullLogService.recordFailure(shopId, PullConsts.DATA_TYPE_PRODUCT, now, now,
                    "会话装配失败: " + e.getClass().getSimpleName() + ": " + e.getMessage(), 0, PullConsts.PULL_WAY_JOB);
            log.warn("店铺会话装配失败,本轮跳过 shop={} :{}", shopId, e.getMessage());
            alertIfContinuousFailure(shopId, e);
            return;
        }
        PlatformClient client = adapterRegistry.get(session.getPlatform()).orElse(null);
        if (client == null) {
            log.debug("平台 adapter 未接入,跳过 shop={} platform={}", shopId, session.getPlatform());
            return;
        }
        LockService.Lease lease = lockService.tryAcquire(LOCK_KEY_PREFIX + shopId);
        if (lease == null) {
            log.debug("店铺 listing 锁被占(或锁不可用且 fail-closed),跳过本轮 shop={}", shopId);
            return;
        }
        long begin = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now(pullClock);
        LocalDateTime cursor = pullLogService.findLastSuccessWindowEnd(shopId, PullConsts.DATA_TYPE_PRODUCT);
        LocalDateTime windowEnd = now;
        LocalDateTime windowStart = cursor == null
                ? now.minusDays(firstPullDays)
                : cursor.minusMinutes(PullConsts.WINDOW_OVERLAP_MINUTES);
        try {
            List<UnifiedProduct> products = client.pullProducts(session, toInstant(windowStart), toInstant(windowEnd));
            // 本批 seller_sku 去重累计(局部变量,Service 无状态),供自动匹配一次性查 erp-goods
            Set<String> sellerSkus = new HashSet<>();
            int count = 0;
            for (UnifiedProduct product : products) {
                // 以会话店铺为准,防平台报文内脏 shopId 覆盖
                product.setShopId(shopId);
                shopProductService.saveUnifiedProduct(shopId, product);
                collectSellerSkus(product, sellerSkus);
                count++;
            }
            // 自动匹配:先同步出映射行,再按本批 seller_sku 精确匹配回填(跨域查 erp-goods 收口在本类)
            int matched = shopProductSkuService.autoMatch(shopId, productService.mapSkuCodesToIds(sellerSkus));
            long cost = System.currentTimeMillis() - begin;
            pullLogService.recordSuccess(shopId, PullConsts.DATA_TYPE_PRODUCT, windowStart, windowEnd, count, cost,
                    PullConsts.PULL_WAY_JOB);
            log.info("listing 同步成功 shop={} window=[{} ~ {}] count={} autoMatched={} cost={}ms",
                    shopId, windowStart, windowEnd, count, matched, cost);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - begin;
            pullLogService.recordFailure(shopId, PullConsts.DATA_TYPE_PRODUCT, windowStart, windowEnd,
                    e.getClass().getSimpleName() + ": " + e.getMessage(), cost, PullConsts.PULL_WAY_JOB);
            log.error("listing 同步失败 shop={} window=[{} ~ {}] cost={}ms", shopId, windowStart, windowEnd, cost, e);
            alertIfContinuousFailure(shopId, e);
        } finally {
            lease.close();
        }
    }

    /**
     * 连续失败达阈值升级站内告警(#14,同 OrderPullJob):恰达阈值轮次告警一次,
     * 通知写失败只记日志不阻断同步主流程
     */
    private void alertIfContinuousFailure(Long shopId, Exception cause) {
        try {
            if (!pullLogService.shouldAlertContinuousFailure(shopId, PullConsts.DATA_TYPE_PRODUCT,
                    PullConsts.FAILURE_ALERT_THRESHOLD)) {
                return;
            }
            notificationService.pushAllUsers(SysNotificationService.TYPE_PULL_FAIL, "listing 同步连续失败告警",
                    StrUtil.format("店铺 {} listing 同步连续失败 {} 次,最近错误:{}", shopId,
                            PullConsts.FAILURE_ALERT_THRESHOLD, cause.getMessage()),
                    SysNotificationService.BIZ_TYPE_SHOP, shopId);
            log.warn("已推送 listing 同步连续失败站内告警 shop={}", shopId);
        } catch (Exception ex) {
            log.error("listing 失败站内告警写入异常 shop={}", shopId, ex);
        }
    }

    /** 累计单个 listing 的 seller_sku 到本批集合(去重) */
    private void collectSellerSkus(UnifiedProduct product, Set<String> sellerSkus) {
        if (CollUtil.isEmpty(product.getSkus())) {
            return;
        }
        for (UnifiedProduct.Sku sku : product.getSkus()) {
            if (StrUtil.isNotBlank(sku.getSellerSku())) {
                sellerSkus.add(sku.getSellerSku());
            }
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
