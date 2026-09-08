package com.own.erp.common.constant;

import java.time.ZoneId;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 拉单调度共享常量(docs/04 拉单策略 / docs/07 §5 幂等与调度)。
 *     erp-api 调度、erp-shop pull_log、erp-order 落库三方共用,故按职能归 erp-common。
 */
public final class PullConsts {

    /** pull_log.data_type:订单 */
    public static final String DATA_TYPE_ORDER = "ORDER";
    /** pull_log.data_type:listing 商品 */
    public static final String DATA_TYPE_PRODUCT = "PRODUCT";
    /** pull_log.data_type:售后/退款 */
    public static final String DATA_TYPE_REFUND = "REFUND";
    /**
     * pull_log.data_type:发货回传(#11 编排 2026-09-08:非拉取型记录,窗口退化为"本次回传时刻"——
     * window_start = window_end = 触发时刻,pulled_count 恒 1(一次回传);
     * 复用 pull_log 而非另建表:排障入口与连续失败告警判定同一套,禁提前抽象)
     */
    public static final String DATA_TYPE_SHIPMENT = "SHIPMENT";

    /** pull_log.pull_way:定时任务触发 */
    public static final String PULL_WAY_JOB = "JOB";
    /** pull_log.pull_way:人工手动触发 */
    public static final String PULL_WAY_MANUAL = "MANUAL";
    /** pull_log.pull_way:业务事件触发(#11:确认发货事件驱动回传平台,非人工非定时) */
    public static final String PULL_WAY_EVENT = "EVENT";

    /**
     * 拉取窗口向左重叠分钟数:平台时钟漂移兜底,重叠段靠唯一键幂等去重(docs/04 拉单策略 1)
     */
    public static final int WINDOW_OVERLAP_MINUTES = 5;

    /**
     * 同店同类型连续失败达到该次数时升级告警(docs/04 拉单策略 5;#14 站内通知,
     * 判定逻辑 PullLogService.shouldAlertContinuousFailure——恰达阈值轮次告警一次,连续段不重复推)
     */
    public static final int FAILURE_ALERT_THRESHOLD = 3;

    /** 库内 DATETIME 会话时区,与 datasource serverTimezone 同源;平台时区翻译归 adapter(docs/07 §1) */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private PullConsts() {
    }
}
