package com.own.erp.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 库存预警规则引擎配置(#6):阈值/扫描量/静默期全走配置不硬编码(铁律 8 业务参数归人工),
 *         默认值在此维护,yml `erp.alert.*` 可覆盖;调度间隔单独走 `erp.alert.interval-ms`
 *         (@Scheduled fixedDelayString,与拉单同款模式)。enabled 默认 true:纯本地扫描无外呼、
 *         24h 静默期兜底每类每日至多一条,与 erp.rate.enabled"保护性横切默认开"同款;上线可 yml 置 false 灰度
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "erp.alert")
public class ErpAlertProperties {

    /** 总开关(false 时 AlertJob 直接返回,不扫描不发通知) */
    private boolean enabled = true;

    /** 静默期(小时):同类型告警在此窗口内只发一条,防刷屏(按 sys_notification 最近发送时间判定) */
    private long quietHours = 24;

    /** 低库存阈值:inventory.qty_available ≤ 此值命中 */
    private int lowStockThreshold = 10;

    /** 发货超时阈值(小时):WAIT_SHIP 且 orderTime 早于 now-N 小时命中 */
    private long shipTimeoutHours = 48;

    /** 退款异常统计窗口(小时):仅统计窗口内创建的 REFUNDED 售后单 */
    private long refundWindowHours = 24;

    /** 退款异常阈值:单店铺窗口内 REFUNDED 单数 ≥ 此值命中 */
    private int refundCountThreshold = 5;

    /** 通知内容明细最大条数(超出以"等"收尾,写侧另有 1000 截断兜底) */
    private int topN = 5;

    /** 单页扫描量(契约钳制 ≤100) */
    private int scanPageSize = 100;

    /** 单规则单轮扫描行数上限(全表分页扫描的兜底护栏,防大表拖死 Job) */
    private int scanMaxRows = 1000;

    /** 滞销/积压动销窗口(天,含今日,#6 销量数据面 2026-09-07 接入):窗口内零销量且有库存判滞销,
     *  日均销量速率 = 窗口销量合计/窗口天数(SalesQueryApi 读 order_sales_daily) */
    private int slowMovingDays = 30;

    /** 积压阈值(天):可用库存/日均销量 ≥ 此值判积压(动销速率按 slowMovingDays 窗口) */
    private int overstockDays = 90;
}
