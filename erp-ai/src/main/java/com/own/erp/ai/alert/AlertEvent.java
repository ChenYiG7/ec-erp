package com.own.erp.ai.alert;

import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 预警事件(#6 库存预警规则引擎):AlertEngine 规则产出的通知载体,erp-api AlertJob
 *     消费后经 SysNotificationService.pushAllUsers(#14 唯一写入口)扇出——erp-ai 不依赖 erp-system,
 *     模块间只传本 record(铁律 2)。聚合口径:每规则每次运行至多一条(明细列 topN),bizType/bizId
 *     留空;静默期去重(erp.alert.quiet-hours)按 notifyType 在 Job 侧收口
 */
@Builder
public record AlertEvent(

        /** 通知类型(词表见 TYPE_* 常量) */
        String notifyType,

        /** 通知标题 */
        String title,

        /** 通知内容(明细列表;通知写侧统一截断 1000) */
        String content,

        /** 关联业务类型(聚合事件为 null) */
        String bizType,

        /** 关联业务ID(聚合事件为 null) */
        Long bizId
) {

    /** 通知类型:低库存(可用≤阈值) */
    public static final String TYPE_LOW_STOCK = "LOW_STOCK";
    /** 通知类型:发货超时(WAIT_SHIP 超时限) */
    public static final String TYPE_SHIP_TIMEOUT = "SHIP_TIMEOUT";
    /** 通知类型:退款异常(窗口内店铺退款单数达阈值) */
    public static final String TYPE_REFUND_ABNORMAL = "REFUND_ABNORMAL";
    /** 通知类型:滞销(有库存但动销窗口内零销量,#6 销量数据面 2026-09-07 接入) */
    public static final String TYPE_SLOW_MOVING = "SLOW_MOVING";
    /** 通知类型:积压(可用库存/日均销量 ≥ 覆盖阈值) */
    public static final String TYPE_OVERSTOCK = "OVERSTOCK";
}
