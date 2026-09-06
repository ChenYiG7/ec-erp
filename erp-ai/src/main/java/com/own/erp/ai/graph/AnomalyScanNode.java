package com.own.erp.ai.graph;

import cn.hutool.core.collection.CollUtil;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.service.AiSuggestionService;
import com.own.erp.contract.OrderQueryApi;
import com.own.erp.contract.QueryPage;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 规则筛节点(#6 两段式第一段,纯程序零 token,docs/02 §13"规则引擎先筛"):
 *     只扫 WAIT_PAY/WAIT_SHIP 两态(待处理可干预,终态历史单不扫防重复命中),分页走 OrderQueryApi
 *     只读契约(铁律 2/7);四规则见 {@link AnomalyRule},同单多规则命中合并一行取基线风险 max。
 *     去重(2026-09-07 拍板):同单存在待确认(status=0)建议即跳过,收口在规则筛后、LLM 评分前——零浪费 token;
 *     旧建议被采纳/忽略后若单据仍命中允许再产出,确认闭环自然运转。
 *     金额类规则一律要求 paidTime 非空——Amazon Pending 单落库金额归零(#4 落库口径),无此守卫整批误报;
 *     大额阈值为本位币口径(orderAmount×exchangeRate,汇率缺省按 1)。护栏 scanPageSize/scanMaxRows
 *     按"单状态"钳制;单状态扫描失败只记日志隔离,不殃及另一状态(同 AlertEngine 单规则隔离口径)。
 *     时间统一走注入 Clock(docs/07 §10)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyScanNode implements NodeAction {

    private static final String STATUS_WAIT_PAY = "WAIT_PAY";
    private static final String STATUS_WAIT_SHIP = "WAIT_SHIP";

    private final @Lazy OrderQueryApi orderQueryApi;
    private final ErpAiProperties props;
    private final Clock pullClock;
    private final AiSuggestionService aiSuggestionService;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        LocalDateTime now = LocalDateTime.now(pullClock);
        List<AnomalyItem> items = new ArrayList<>();
        int scanned = scanState(STATUS_WAIT_PAY, now, items) + scanState(STATUS_WAIT_SHIP, now, items);
        int deduped = dedupPending(items);
        log.info("订单异常规则筛完成:扫描 {} 单(两态),可疑 {} 单,去重跳过 {} 单",
                scanned, items.size(), deduped);
        return Map.of(AnomalyStateKeys.KEY_ITEMS, items, AnomalyStateKeys.KEY_SCANNED, scanned);
    }

    /** 同单已存在待确认建议则移除(定时/手动统一口径),返回移除数;无可疑单不查库 */
    private int dedupPending(List<AnomalyItem> items) {
        if (items.isEmpty()) {
            return 0;
        }
        Set<Long> pending = aiSuggestionService.findPendingRefIds(
                AiConsts.TYPE_ANOMALY, AiConsts.REF_TYPE_SHOP_ORDER);
        int before = items.size();
        items.removeIf(item -> pending.contains(item.orderId()));
        return before - items.size();
    }

    /** 单状态分页扫全量并评规则,命中追加进 items;失败只记日志返回已扫行数(单态隔离) */
    private int scanState(String orderStatus, LocalDateTime now, List<AnomalyItem> items) {
        ErpAiProperties.Anomaly cfg = props.getAnomaly();
        int scanned = 0;
        try {
            int pageNo = 1;
            while (scanned < cfg.getScanMaxRows()) {
                QueryPage<OrderQueryApi.OrderView> page = orderQueryApi.pageOrders(
                        OrderQueryApi.OrderFilter.builder()
                                .orderStatus(orderStatus).pageNo(pageNo).pageSize(cfg.getScanPageSize()).build());
                List<OrderQueryApi.OrderView> rows = page == null ? null : page.list();
                if (CollUtil.isEmpty(rows)) {
                    break;
                }
                for (OrderQueryApi.OrderView row : rows) {
                    collectHits(row, orderStatus, now, items);
                }
                scanned += rows.size();
                pageNo++;
                if (rows.size() < cfg.getScanPageSize()) {
                    break;
                }
            }
        } catch (Exception e) {
            // 已扫部分照常生效,失败状态从断点丢弃(同 AlertEngine 单规则隔离口径)
            log.warn("订单异常扫描[{}]执行失败,本轮跳过该状态 :{}", orderStatus, e.getMessage(), e);
        }
        return scanned;
    }

    /** 四规则评一单:命中非空才产出聚合行(hitRules 并存,基线风险取 max) */
    private void collectHits(OrderQueryApi.OrderView row, String orderStatus, LocalDateTime now,
                             List<AnomalyItem> items) {
        if (row == null || row.id() == null) {
            return;
        }
        List<AnomalyRule> hits = new ArrayList<>();
        if (STATUS_WAIT_PAY.equals(orderStatus) && row.orderTime() != null
                && row.orderTime().isBefore(now.minusHours(props.getAnomaly().getUnpaidHours()))) {
            hits.add(AnomalyRule.UNPAID_TIMEOUT);
        }
        // 金额类规则守卫:paidTime 非空才评(Amazon Pending 0 元单防误报);orderAmount 缺失无从评起
        boolean paid = row.paidTime() != null;
        if (paid && row.orderAmount() != null) {
            if (row.orderAmount().signum() <= 0) {
                hits.add(AnomalyRule.ZERO_AMOUNT);
            }
            BigDecimal amountBase = row.orderAmount()
                    .multiply(row.exchangeRate() == null ? BigDecimal.ONE : row.exchangeRate());
            if (amountBase.compareTo(props.getAnomaly().getBigOrderAmount()) >= 0) {
                hits.add(AnomalyRule.BIG_AMOUNT);
            }
            if (row.discountAmount() != null
                    && row.discountAmount().compareTo(
                            row.orderAmount().multiply(props.getAnomaly().getHighDiscountRatio())) >= 0) {
                hits.add(AnomalyRule.HIGH_DISCOUNT);
            }
        }
        if (hits.isEmpty()) {
            return;
        }
        items.add(AnomalyItem.builder()
                .orderId(row.id())
                .shopId(row.shopId())
                .hitRules(hits)
                .baselineRisk(AnomalyRule.maxRisk(hits))
                .currency(row.currency())
                .orderAmount(row.orderAmount())
                .exchangeRate(row.exchangeRate())
                .discountAmount(row.discountAmount())
                .orderTime(row.orderTime())
                .paidTime(row.paidTime())
                .summary("")
                .llmScored(false)
                .build());
    }
}
