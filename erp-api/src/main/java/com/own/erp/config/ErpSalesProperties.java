package com.own.erp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 销量日统计调度配置(#6 销量数据面):开关/重算窗口全走配置不硬编码,
 *         默认值在此维护,yml `erp.sales.*` 可覆盖;调度时刻单独走 `erp.sales.cron`
 *         (@Scheduled cron 占位符,与补货/异常检测同款模式)。enabled 默认 true:
 *         纯本地单语句 upsert 无外呼,与 erp.alert.enabled"保护性横切默认开"同款,上线可置 false 灰度
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "erp.sales")
public class ErpSalesProperties {

    /** 总开关(false 时 SalesSnapshotJob 直接返回,不重算) */
    private boolean enabled = true;

    /** 重算窗口(天,含今日):每日 upsert 近 N 天,覆盖订单状态回传/取消单修正 */
    private int rebuildDays = 30;
}
