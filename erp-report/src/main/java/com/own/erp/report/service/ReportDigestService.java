package com.own.erp.report.service;

import com.own.erp.report.mapper.ReportQueryMapper;
import com.own.erp.report.report.InventorySnapshotRow;
import com.own.erp.report.report.ReportDigest;
import com.own.erp.report.report.SalesDailyRow;
import com.own.erp.report.report.SalesSkuRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/9
 * @Description : 经营简报服务(#23 智能报表 V1,四期 BI):按周期(日/周/月)聚合经营摘要文本,
 *     定时任务推送走 #14 出口(SysNotificationService.pushAllUsers → 站内+邮箱+Webhook 三渠道扇出);
 *     预览端点(ReportController /digest/preview)同源零副作用。
 *     <p>数据面复用既有四方法(零新 SQL 零 DDL,同 #20/#22 母本):selectSalesDaily(销量逐日,
 *     汇总合计/动销天数/日均)/ selectSalesSku(销量降序取 TOP5)/ selectLatestSnapshotDate+selectSnapshot
 *     (期末库存取最新快照跨仓合计,快照缺失写"暂无"不猜,#19 禁猜口径);
 *     时钟注入 pullClock(Asia/Shanghai,SchedulingConfig 同款,AIR 单测固定 Clock 可重复)。
 *     <p>窗口拍板:日报=昨日单日(销量日表 01:00 聚合后数据已就位,推送 cron 默认 08:00)/
 *     周报=上周一至上周日(今日为周一时 previous(MONDAY) 即 7 天前,周一早晨跑语义正确)/
 *     月报=上月自然月
 */
@Service
@RequiredArgsConstructor
public class ReportDigestService {

    /** 简报 TOP SKU 行数(简报是摘要,明细走报表中心页) */
    private static final int TOP_SKU_LIMIT = 5;

    private final ReportQueryMapper reportQueryMapper;
    private final Clock pullClock;

    /** 周期类型(窗口语义见 digest;notifyType = REPORT_ + name) */
    public enum Period {
        DAILY, WEEKLY, MONTHLY;

        /** 入参解析(容大小写;非法抛 IllegalArgumentException 走全局异常处理,不静默回落) */
        public static Period parse(String raw) {
            try {
                return Period.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("非法简报周期: " + raw + "(可选 DAILY/WEEKLY/MONTHLY)");
            }
        }
    }

    /** 生成简报(纯读侧零副作用;推送动作在 Job/通知出口,不在本服务) */
    public ReportDigest digest(Period period) {
        LocalDate today = LocalDate.now(pullClock);
        LocalDate[] window = window(period, today);
        LocalDate from = window[0];
        LocalDate to = window[1];

        List<SalesDailyRow> daily = reportQueryMapper.selectSalesDaily(from, to);
        long totalQty = daily.stream().mapToLong(SalesDailyRow::totalQty).sum();
        long activeDays = daily.stream().filter(row -> row.totalQty() > 0).count();
        long windowDays = to.toEpochDay() - from.toEpochDay() + 1;
        List<SalesSkuRow> topSku = reportQueryMapper.selectSalesSku(from, to, TOP_SKU_LIMIT);

        StringBuilder content = new StringBuilder();
        content.append("统计窗口:").append(from).append(" ~ ").append(to).append('\n');
        content.append("销量合计:").append(totalQty).append(" 件 | 动销天数:").append(activeDays)
                .append(" 天 | 日均:").append(totalQty / windowDays).append(" 件\n");
        content.append("销量 TOP").append(TOP_SKU_LIMIT).append(':');
        if (topSku.isEmpty()) {
            content.append("无\n");
        } else {
            content.append('\n');
            for (int i = 0; i < topSku.size(); i++) {
                SalesSkuRow row = topSku.get(i);
                content.append("  ").append(i + 1).append(". ").append(skuLabel(row))
                        .append(':').append(row.totalQty()).append(" 件\n");
            }
        }
        content.append(stockLine());

        return new ReportDigest(period.name(), "REPORT_" + period.name(),
                title(period, from, to), content.toString(), from, to);
    }

    /** 窗口语义:日报昨日单日/周报上周一至周日/月报上月自然月(年月禁写死,随 pullClock 滚动) */
    private LocalDate[] window(Period period, LocalDate today) {
        return switch (period) {
            case DAILY -> new LocalDate[]{today.minusDays(1), today.minusDays(1)};
            case WEEKLY -> {
                LocalDate monday = today.with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
                yield new LocalDate[]{monday, monday.plusDays(6)};
            }
            case MONTHLY -> {
                LocalDate first = today.minusMonths(1).withDayOfMonth(1);
                yield new LocalDate[]{first, first.withDayOfMonth(first.lengthOfMonth())};
            }
        };
    }

    /** 标题:窗口即日期语义(日报 2026-09-08/周报 2026-09-07~2026-09-13/月报 2026-08) */
    private String title(Period period, LocalDate from, LocalDate to) {
        return switch (period) {
            case DAILY -> "经营日报 " + from;
            case WEEKLY -> "经营周报 " + from + "~" + to;
            case MONTHLY -> "经营月报 " + from.toString().substring(0, 7);
        };
    }

    /** SKU 展示名:编码兜底裸 ID(脏 id 回落不报错,同 #22 口径),商品名缺失只显示编码 */
    private String skuLabel(SalesSkuRow row) {
        String code = row.skuCode() != null ? row.skuCode() : "SKU#" + row.skuId();
        return row.productName() == null ? code : code + " " + row.productName();
    }

    /** 期末库存行:最新快照跨仓合计;无快照写"暂无"不猜(#19 禁猜口径,快照任务首跑前简报仍可发) */
    private String stockLine() {
        LocalDate snapDate = reportQueryMapper.selectLatestSnapshotDate();
        if (snapDate == null) {
            return "期末库存:暂无快照(InventorySnapshotJob 未跑或快照缺失)";
        }
        List<InventorySnapshotRow> rows = reportQueryMapper.selectSnapshot(snapDate);
        long onHand = rows.stream().mapToLong(InventorySnapshotRow::qtyOnHand).sum();
        long available = rows.stream().mapToLong(InventorySnapshotRow::qtyAvailable).sum();
        return "期末库存:在库 " + onHand + " 件 | 可用 " + available + " 件(快照日 " + snapDate + ")";
    }
}
