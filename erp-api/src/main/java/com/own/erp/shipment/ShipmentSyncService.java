package com.own.erp.shipment;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.own.erp.common.api.DeliveryShippedEvent;
import com.own.erp.common.constant.PullConsts;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.ShopOrderApi;
import com.own.erp.fulfill.constant.DeliveryConsts;
import com.own.erp.fulfill.response.DeliveryOrderItemResponse;
import com.own.erp.fulfill.response.DeliveryOrderResponse;
import com.own.erp.fulfill.service.DeliveryOrderService;
import com.own.erp.platform.AdapterRegistry;
import com.own.erp.platform.PlatformClient;
import com.own.erp.platform.PlatformShipment;
import com.own.erp.platform.ShopSession;
import com.own.erp.shop.service.PullLogService;
import com.own.erp.shop.service.ShopService;
import com.own.erp.system.service.SysNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 发货回传平台编排(#11 遗留收口,docs/04 回传拍板):确认发货(ship)事务**提交后**把运单号与
 *         行级发运回传平台(AdapterRegistry → PlatformClient.uploadTracking)。adapter 侧能力已于
 *         2026-09-06 脱机落地(Amazon MFN confirmShipment),本类补的是"编排"这一半。
 *
 *         时序与失败语义(拍板,禁改):
 *         1. **AFTER_COMMIT 才回传**——事件 {@link DeliveryShippedEvent} 由 erp-fulfill 在 ship 事务内发布,
 *            本服务以 {@link TransactionalEventListener}(AFTER_COMMIT)消费:事务提交后本地发货已是既成事实,
 *            回传失败(网络/平台侧/凭证)只记 pull_log + 连续失败告警,**绝不回滚本地发货**(平台侧可手工补);
 *            反之若在事务内发 HTTP,长事务占连接且回滚语义与平台侧已生效的回传不一致。
 *         2. **跳过 vs 失败**分得清(噪音纪律,同拉单 Job 口径):
 *            - 跳过(不记 pull_log):开关关闭 / 平台 adapter 未接入 / 非卖家自履约(FBA·海外仓平台自履约)/
 *              运单号未填(要素未齐,后补运单号后重新发货才回传)/ 单据状态非已发货
 *            - 失败(记 pull_log SHIPMENT + 连续失败告警):会话装配失败(凭证缺失·Token 刷新失败)/
 *              订单不存在 / 缺平台订单号 / 缺任一发货行的 platformOrderItemId(**禁静默丢行**,
 *              半回传比不回传更难对账)/ 平台调用抛错
 *         3. 编排只落 erp-api:erp-fulfill 不具备 ShopSession(erp-shop)与 AdapterRegistry(platform-sdk),
 *            且回传属跨模块编排(铁律 2),故走事件解耦而非域内直调。
 *
 *         承运商口径:发货单 logisticsCompany 是自由文本(中文名),无平台 carrierCode 映射表——
 *         carrierCode 置空、carrierName 兜底(SP-API 编外承运商接受 carrierName,adapter 侧校验"至少其一")。
 *
 *         ⚠️ 已知边界:回传在 ship 请求线程内同步执行(AFTER_COMMIT 后、Controller 返回前),
 *         平台侧慢/超时会拖长"确认发货"响应;V1 接受(失败只记 pull_log 不影响结果正确性),
 *         真凭证联调实测耗时后若不可接受,再改为独立 executor 异步化(不复用 pullScheduler 单线程池,
 *         免回传占住拉单调度线程)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentSyncService {

    /** pull_log 行数语义:一次回传记 1 行(非拉取型,不复用 pulled_count 的条数语义) */
    private static final int SYNC_COUNT = 1;

    private final DeliveryOrderService deliveryOrderService;
    private final ShopOrderApi shopOrderApi;
    private final ShopService shopService;
    private final AdapterRegistry adapterRegistry;
    private final PullLogService pullLogService;
    private final SysNotificationService notificationService;
    private final Clock clock;

    /** 回传总开关(默认开;接真凭证灰度期可置 false 只落库不回传,同 erp.alert.enabled 口径) */
    @Value("${erp.shipment.sync-enabled:true}")
    private boolean syncEnabled;

    /**
     * 事件入口(AFTER_COMMIT):异常一律吞掉转日志——事务已提交,回传失败不得影响 ship 的返回语义,
     * 失败痕迹落 pull_log(docs/07 §3 排障第一入口),由连续失败告警兜底人工介入
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeliveryShipped(DeliveryShippedEvent event) {
        if (!syncEnabled || event == null) {
            return;
        }
        try {
            sync(event.deliveryId());
        } catch (Exception e) {
            log.error("发货回传编排未预期异常 delivery={}", event.deliveryId(), e);
        }
    }

    /**
     * 单次回传全流程:读发货单 → 装配会话(内含 Token 刷新)→ 取 adapter → 要素校验与命令装配
     * → uploadTracking → 记 pull_log(成功/失败)。失败只记不抛——回传失败不回滚本地发货(docs/04),
     * 痕迹落 pull_log 由连续失败告警兜底人工介入
     */
    public void sync(Long deliveryId) {
        DeliveryOrderResponse delivery = deliveryOrderService.getById(deliveryId);
        if (delivery == null) {
            log.warn("发货单不存在,跳过回传 delivery={}", deliveryId);
            return;
        }
        Long shopId = delivery.shopId();
        LocalDateTime now = LocalDateTime.now(clock);
        long begin = System.currentTimeMillis();
        try {
            // 会话装配失败(凭证缺失/解密失败/Token 刷新失败)同拉单口径记 pull_log + 告警
            ShopSession session = shopService.getShopSession(shopId);
            PlatformClient client = adapterRegistry.get(session.getPlatform()).orElse(null);
            if (client == null) {
                // adapter 未接入(如店铺已配置但平台未对接)属正常现象,debug 级不打扰、不产生失败噪音
                log.debug("平台 adapter 未接入,跳过回传 shop={} platform={}", shopId, session.getPlatform());
                return;
            }
            PlatformShipment shipment = assemble(delivery, now);
            if (shipment == null) {
                return;
            }
            client.uploadTracking(session, shipment);
            long cost = System.currentTimeMillis() - begin;
            pullLogService.recordSuccess(shopId, PullConsts.DATA_TYPE_SHIPMENT, now, now, SYNC_COUNT, cost,
                    PullConsts.PULL_WAY_EVENT);
            log.info("发货回传成功 delivery={} shop={} platformOrderId={} rows={} cost={}ms",
                    deliveryId, shopId, shipment.platformOrderId(), shipment.items().size(), cost);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - begin;
            pullLogService.recordFailure(shopId, PullConsts.DATA_TYPE_SHIPMENT, now, now,
                    e.getClass().getSimpleName() + ": " + e.getMessage(), cost, PullConsts.PULL_WAY_EVENT);
            log.error("发货回传失败 delivery={} shop={} cost={}ms", deliveryId, shopId, cost, e);
            alertIfContinuousFailure(shopId, e);
        }
    }

    /**
     * 要素校验并装配回传命令;返回 null = 不适用本回传(跳过,已记日志),要素缺失抛异常转 pull_log 失败。
     * 明细行翻译:发货明细 orderItemId → 订单行 platformOrderItemId(经 ShopOrderApi 契约,
     * 发货域不直连订单域,铁律 2)
     */
    private PlatformShipment assemble(DeliveryOrderResponse delivery, LocalDateTime now) {
        if (!DeliveryConsts.DELIVERY_SHIPPED.equals(delivery.status())
                && !DeliveryConsts.DELIVERY_DELIVERED.equals(delivery.status())) {
            log.debug("发货单非已发货状态,跳过回传 delivery={} status={}", delivery.id(), delivery.status());
            return null;
        }
        if (CollUtil.isEmpty(delivery.items())) {
            log.warn("发货单无明细,跳过回传 delivery={}", delivery.id());
            return null;
        }
        ShopOrderApi.OrderDeliveryView view = shopOrderApi.findDeliveryView(delivery.orderId());
        if (view == null) {
            throw new BusinessException("订单不存在:" + delivery.orderId());
        }
        if (!DeliveryConsts.CHANNEL_SELF_FULFILL.equals(view.fulfillmentChannel())) {
            // FBA/海外仓由平台或仓履约,无回传动作(docs/04 回传差异表)
            log.debug("非卖家自履约订单,跳过回传 order={} channel={}", delivery.orderId(), view.fulfillmentChannel());
            return null;
        }
        if (StrUtil.isBlank(delivery.trackingNo())) {
            log.warn("发货单未填运单号,跳过回传(补填后需重新确认发货)delivery={}", delivery.id());
            return null;
        }
        if (StrUtil.isBlank(view.platformOrderId())) {
            throw new BusinessException("订单缺平台订单号,无法回传:orderId=" + delivery.orderId());
        }
        Map<Long, String> platformItemIdByOrderItem = new HashMap<>();
        for (ShopOrderApi.OrderDeliveryView.Item item : nullToEmpty(view.items())) {
            platformItemIdByOrderItem.put(item.orderItemId(), item.platformOrderItemId());
        }
        List<PlatformShipment.Item> lines = new ArrayList<>(delivery.items().size());
        for (DeliveryOrderItemResponse line : delivery.items()) {
            String platformItemId = platformItemIdByOrderItem.get(line.orderItemId());
            if (StrUtil.isBlank(platformItemId)) {
                // 禁静默丢行:半回传比不回传更难对账(同"未知状态/缺单号抛异常"纪律)
                throw new BusinessException("订单明细缺平台行号,无法回传:orderItemId=" + line.orderItemId());
            }
            int quantity = line.shipQty() == null ? 0 : line.shipQty();
            if (quantity <= 0) {
                throw new BusinessException("发货明细数量非正,无法回传:deliveryItemId=" + line.id());
            }
            lines.add(PlatformShipment.Item.builder()
                    .platformOrderItemId(platformItemId)
                    .quantity(quantity)
                    .build());
        }
        LocalDateTime shipTime = delivery.shippedAt() == null ? now : delivery.shippedAt();
        return PlatformShipment.builder()
                .platformOrderId(view.platformOrderId())
                .trackingNo(delivery.trackingNo())
                // carrierCode 无平台映射表,交由 adapter 侧的 carrierName 兜底(编外承运商)校验
                .carrierName(delivery.logisticsCompany())
                .shipTime(toInstant(shipTime))
                .items(lines)
                .build();
    }

    /** 连续失败达阈值升级站内告警(#14,同 OrderPullJob 口径):恰达阈值轮次告警一次,通知写失败不阻断 */
    private void alertIfContinuousFailure(Long shopId, Exception cause) {
        try {
            if (!pullLogService.shouldAlertContinuousFailure(shopId, PullConsts.DATA_TYPE_SHIPMENT,
                    PullConsts.FAILURE_ALERT_THRESHOLD)) {
                return;
            }
            notificationService.pushAllUsers(SysNotificationService.TYPE_PULL_FAIL, "发货回传连续失败告警",
                    StrUtil.format("店铺 {} 发货回传连续失败 {} 次,最近错误:{}", shopId,
                            PullConsts.FAILURE_ALERT_THRESHOLD, cause.getMessage()),
                    SysNotificationService.BIZ_TYPE_SHOP, shopId);
            log.warn("已推送发货回传连续失败站内告警 shop={}", shopId);
        } catch (Exception ex) {
            log.error("发货回传失败站内告警写入异常 shop={}", shopId, ex);
        }
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    /** 库内时间 → 平台调用入参 Instant(会话时区,见 PullConsts.ZONE) */
    private static Instant toInstant(LocalDateTime time) {
        return time.atZone(PullConsts.ZONE).toInstant();
    }
}
