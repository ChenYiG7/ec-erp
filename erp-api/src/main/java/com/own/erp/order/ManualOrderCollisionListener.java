package com.own.erp.order;

import cn.hutool.core.util.StrUtil;
import com.own.erp.common.api.ManualOrderCollisionEvent;
import com.own.erp.system.service.SysNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 内销合成单号冲突告警编排(#29 订单域补课):erp-order 在拉单写口检测到任务单号
 *         撞 MANUAL 合成单号时发布 {@link ManualOrderCollisionEvent}(已拒绝覆盖),本监听器在
 *         发布事务提交后经 SysNotificationService 扇出站内通知。
 *
 *         编排只落 erp-api:erp-order 不具备 erp-system 通知能力(铁律 2 禁横向依赖),故走事件解耦;
 *         AFTER_COMMIT 相位 + fallbackExecution 兜底(理论上发布方总在事务内,防御式开启);
 *         通知写失败只记日志——告警是旁路,绝不回滚已提交的拉单落库(docs/07 §11 事件范式)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManualOrderCollisionListener {

    private final SysNotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onManualOrderCollision(ManualOrderCollisionEvent event) {
        if (event == null) {
            return;
        }
        try {
            notificationService.pushAllUsers(SysNotificationService.TYPE_ORDER_MANUAL_CONFLICT,
                    "内销单号冲突告警",
                    StrUtil.format("店铺 {} 拉取到平台单号 {} 与内销单 #{} 合成单号相同,已拒绝覆盖拉单数据,请人工核查。",
                            event.shopId(), event.platformOrderId(), event.orderId()),
                    SysNotificationService.BIZ_TYPE_ORDER, event.orderId());
            log.warn("已推送内销单号冲突告警 shop={} platformOrderId={} orderId={}",
                    event.shopId(), event.platformOrderId(), event.orderId());
        } catch (Exception e) {
            log.error("内销单号冲突告警写入异常 shop={} platformOrderId={}",
                    event.shopId(), event.platformOrderId(), e);
        }
    }
}
