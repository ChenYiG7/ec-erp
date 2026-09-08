package com.own.erp.finance.service;

import cn.hutool.core.util.StrUtil;
import com.own.erp.finance.mapper.RefundReconciliationMapper;
import com.own.erp.finance.reconcile.RefundDiffEvent;
import com.own.erp.finance.reconcile.RefundReconciliationAlert;
import com.own.erp.finance.reconcile.RefundSideRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 退款勾稽(#19④,#12 遗留"退款金额与财务勾稽"收口):aftersale_order.refund_amount
 *         对 settlement_detail REFUND 行按订单聚合比对,差异经 #14 站内通知扇出(扇出在 erp-api Job)。
 *         勾稽口径拍板(docs/03 §6.2):
 *         - 归集键 = 店铺 + 平台订单号:结算 REFUND 行无退款单 ID(平台结算报文不携带),
 *           逐单勾稽不可行;同订单多售后单/部分退款自然 SUM,售后侧经 shop_order 翻译平台订单号;
 *         - 参与范围:售后侧 = 已退款终态(REFUNDED/COMPLETED)且 refund_amount 非空(未决态钱未退不比,
 *           平台先行退款的时序差等状态同步后自然纳入);结算侧 = PARSED 报告 REFUND 行
 *           (FAILED 报告明细是待校准暂存态,参与会污染比对,重拉转 PARSED 后自然纳入);
 *         - 金额方向:售后 refund_amount 恒正(绝对值口径,AmazonRefundTranslator 拍板);
 *           结算 REFUND 行报告原值带符号(退款为负),Σ(−amount) 转正同向比对(禁取绝对值纪律不破坏);
 *         - 差异判定规则三类:①AMOUNT_MISMATCH 双侧有行且 |售后Σ−结算Σ| > 容差(0.01 = 结算列
 *           DECIMAL(18,2) 精度口径,防售后 DECIMAL(12,4) 舍入尾差误报);②MISSING_IN_SETTLEMENT
 *           售后已退款而该订单在 PARSED 报告中无任何 REFUND 行(疑似报告未拉/退款未入结算周期);
 *           ③CURRENCY_MISMATCH 任一侧同订单多币种或两侧币种不同——金额比对失真,禁混币计算;
 *         - 已知边界(V1 不告警,真凭证实测后评估):结算 REFUND 行按 #19② 拍板含退款负佣金行,
 *           售后 refund_amount 为 Principal 本金口径,两侧或存佣金级系统性偏差——差异内容带双侧金额
 *           供人工判读,真凭证校准 translator 归一时随 #19② 一并定版;
 *         - 只读勾稽:不改任何表,告警扇出后差异处置走人工(同 #6 预警模式)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundReconciliationService {

    /** 通知类型:退款勾稽差异告警(sys_notification.notify_type 词表随 #14) */
    public static final String NOTIFY_TYPE_REFUND_DIFF = "REFUND_DIFF";

    /** 告警标题(每轮聚合一条,标题恒定) */
    private static final String ALERT_TITLE = "退款勾稽差异告警";
    /** 单轮差异明细列条数上限(超出截断,总笔数在内容头部声明;全员广播量可控,同 #6 明细列 topN 口径) */
    private static final int CONTENT_MAX_LINES = 20;
    /** 金额容差:结算 amount DECIMAL(18,2) 两位小数 vs 售后 refund_amount DECIMAL(12,4),容差取结算精度防舍入尾差误报 */
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");

    private final RefundReconciliationMapper reconciliationMapper;

    /**
     * 勾稽 + 聚合告警装配(编排入口):无差异返回 null(Job 不扇出);有差异返回待推送告警
     * (每轮至多一条,notifyType=REFUND_DIFF,差异明细列 topN)
     */
    public RefundReconciliationAlert reconcileAlert() {
        List<RefundDiffEvent> diffs = reconcile();
        if (diffs.isEmpty()) {
            return null;
        }
        return RefundReconciliationAlert.builder()
                .notifyType(NOTIFY_TYPE_REFUND_DIFF)
                .title(ALERT_TITLE)
                .content(formatContent(diffs))
                .diffCount(diffs.size())
                .build();
    }

    /**
     * 勾稽比对(纯读侧):两侧聚合行按 店铺+平台订单号 归并逐键判定。
     * 以售后侧为锚(#12 主语口径:aftersale_order.refund_amount 对 settlement REFUND 行勾稽)——
     * 结算侧孤儿 REFUND 行(售后侧无对应单)不产差异,留 TODO 随真凭证实测后评估
     */
    public List<RefundDiffEvent> reconcile() {
        Map<String, List<RefundSideRow>> aftersaleByKey = groupByKey(reconciliationMapper.sumAftersaleRefundByPlatformOrder());
        Map<String, List<RefundSideRow>> settlementByKey = groupByKey(reconciliationMapper.sumSettlementRefundByPlatformOrder());
        List<RefundDiffEvent> diffs = new ArrayList<>();
        for (Map.Entry<String, List<RefundSideRow>> entry : aftersaleByKey.entrySet()) {
            diffs.addAll(compare(entry.getValue(), settlementByKey.getOrDefault(entry.getKey(), List.of())));
        }
        log.info("退款勾稽完成 售后侧订单 {} 笔/结算侧订单 {} 笔,差异 {} 笔",
                aftersaleByKey.size(), settlementByKey.size(), diffs.size());
        return diffs;
    }

    /** 单订单判定:币种一致性 → 结算侧缺失 → 金额容差,三类差异见类注释(勾稽平返回空列表) */
    private List<RefundDiffEvent> compare(List<RefundSideRow> aftersaleRows, List<RefundSideRow> settlementRows) {
        RefundSideRow aftersale = aftersaleRows.get(0);
        BigDecimal aftersaleSum = sum(aftersaleRows);
        if (settlementRows.isEmpty()) {
            return List.of(RefundDiffEvent.builder()
                    .diffType(RefundDiffEvent.TYPE_MISSING_IN_SETTLEMENT)
                    .shopId(aftersale.shopId())
                    .platformOrderId(aftersale.platformOrderId())
                    .aftersaleAmount(aftersaleSum)
                    .aftersaleCurrency(currencies(aftersaleRows))
                    .build());
        }
        String aftersaleCurrency = currencies(aftersaleRows);
        String settlementCurrency = currencies(settlementRows);
        // 币种防御:任一侧混币(脏数据/重拉错位)或两侧币种不同,金额比对失真——留痕禁混币计算
        if (!StrUtil.equals(aftersaleCurrency, settlementCurrency)) {
            return List.of(RefundDiffEvent.builder()
                    .diffType(RefundDiffEvent.TYPE_CURRENCY_MISMATCH)
                    .shopId(aftersale.shopId())
                    .platformOrderId(aftersale.platformOrderId())
                    .aftersaleCurrency(aftersaleCurrency)
                    .settlementCurrency(settlementCurrency)
                    .build());
        }
        BigDecimal settlementSum = sum(settlementRows);
        BigDecimal diff = aftersaleSum.subtract(settlementSum);
        if (diff.abs().compareTo(AMOUNT_TOLERANCE) > 0) {
            return List.of(RefundDiffEvent.builder()
                    .diffType(RefundDiffEvent.TYPE_AMOUNT_MISMATCH)
                    .shopId(aftersale.shopId())
                    .platformOrderId(aftersale.platformOrderId())
                    .aftersaleAmount(aftersaleSum)
                    .settlementAmount(settlementSum)
                    .aftersaleCurrency(aftersaleCurrency)
                    .settlementCurrency(settlementCurrency)
                    .diffAmount(diff)
                    .build());
        }
        return List.of();
    }

    /** 聚合告警内容:头部声明总笔数 + 差异明细逐行(topN 截断);写侧仍有 1000 截断兜底 */
    private String formatContent(List<RefundDiffEvent> diffs) {
        String header = StrUtil.format("共 {} 笔退款勾稽差异", diffs.size())
                + (diffs.size() > CONTENT_MAX_LINES ? "(仅列前 " + CONTENT_MAX_LINES + " 笔)" : "") + ":";
        List<String> lines = new ArrayList<>();
        for (RefundDiffEvent event : diffs.subList(0, Math.min(CONTENT_MAX_LINES, diffs.size()))) {
            lines.add(StrUtil.format("shop={} 订单 {} [{}] {}", event.shopId(), event.platformOrderId(),
                    event.diffType(), describe(event)));
        }
        return header + System.lineSeparator() + String.join(System.lineSeparator(), lines);
    }

    /** 差异明细描述(按类型给双侧金额/币种,缺失侧明示,禁猜) */
    private String describe(RefundDiffEvent event) {
        return switch (event.diffType()) {
            case RefundDiffEvent.TYPE_AMOUNT_MISMATCH -> StrUtil.format("售后 {} {} / 结算 {} {} 差额 {}",
                    event.aftersaleAmount(), event.aftersaleCurrency(),
                    event.settlementAmount(), event.settlementCurrency(), event.diffAmount());
            case RefundDiffEvent.TYPE_MISSING_IN_SETTLEMENT -> StrUtil.format("售后 {} {},结算侧无 REFUND 行",
                    event.aftersaleAmount(), event.aftersaleCurrency());
            case RefundDiffEvent.TYPE_CURRENCY_MISMATCH -> StrUtil.format("售后币种 {} / 结算币种 {},金额不比",
                    event.aftersaleCurrency(), event.settlementCurrency());
            default -> "未知差异类型 " + event.diffType();
        };
    }

    /** 按 店铺+平台订单号 归并(保序;SQL 已按同键分组,同键多币种行归同组交币种防御判定) */
    private Map<String, List<RefundSideRow>> groupByKey(List<RefundSideRow> rows) {
        return rows.stream().collect(Collectors.groupingBy(
                row -> row.shopId() + "|" + StrUtil.nullToEmpty(row.platformOrderId()),
                LinkedHashMap::new, Collectors.toList()));
    }

    /** Σ金额(空列表禁入,调用方保证) */
    private BigDecimal sum(List<RefundSideRow> rows) {
        return rows.stream().map(RefundSideRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 组内币种词(单币种单值;混币逗号并列排序稳定,供告警判读) */
    private String currencies(List<RefundSideRow> rows) {
        Set<String> set = rows.stream().map(RefundSideRow::currency)
                .collect(Collectors.toCollection(TreeSet::new));
        return String.join(",", set);
    }
}
