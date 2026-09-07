package com.own.erp.ai.alert;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAlertProperties;
import com.own.erp.contract.AftersaleQueryApi;
import com.own.erp.contract.InventoryQueryApi;
import com.own.erp.contract.OrderQueryApi;
import com.own.erp.contract.QueryPage;
import com.own.erp.contract.SalesQueryApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 库存预警规则引擎(#6,OmniTrade 落位表"三期最先"):V1 五规则——
 *     低库存(可用≤阈值)/ 发货超时(WAIT_SHIP 超 N 小时)/ 退款异常(店铺窗口内退款单数达阈值)/
 *     滞销(有库存但动销窗口内零销量)/ 积压(可用库存/日均销量 ≥ 覆盖阈值);
 *     滞销/积压 2026-09-07 随销量数据面(order_sales_daily)落地接入,读侧走 SalesQueryApi 只读契约(铁律 2)。
 *     取数只走 erp-contract 只读查询契约(铁律 2/7,禁横向依赖);分页扫全量,scanPageSize/scanMaxRows
 *     钳制防大表拖死;单规则失败只记日志不殃及本轮其余规则;出口仅产 AlertEvent,推送/静默去重收口
 *     erp-api AlertJob(erp-ai 不依赖 erp-system)。无状态可重复:时间统一走注入 Clock(docs/07 §10)。
 *     #18 系统设置:阈值/静默期等可变项每轮经 AiRuntimeProperties 取值(DB 覆盖值优先,yml 默认兜底,
 *     保存即时生效);scanPageSize/scanMaxRows 扫描护栏属系统级稳定参数,仍走 ErpAlertProperties(不入表)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEngine {

    private static final String STATUS_WAIT_SHIP = "WAIT_SHIP";
    private static final String STATUS_REFUNDED = "REFUNDED";

    private final @Lazy InventoryQueryApi inventoryQueryApi;
    private final @Lazy OrderQueryApi orderQueryApi;
    private final @Lazy AftersaleQueryApi aftersaleQueryApi;
    private final @Lazy SalesQueryApi salesQueryApi;
    private final AiRuntimeProperties runtime;
    private final ErpAlertProperties props;
    private final Clock pullClock;

    /** 本轮评估:跑全部规则,返回命中事件(可能为空列表);单规则异常隔离 */
    public List<AlertEvent> evaluate() {
        LocalDateTime now = LocalDateTime.now(pullClock);
        List<AlertEvent> events = new ArrayList<>();
        runRule(events, "低库存", () -> lowStockRule());
        runRule(events, "发货超时", () -> shipTimeoutRule(now));
        runRule(events, "退款异常", () -> refundRule(now));
        runRule(events, "滞销", () -> slowMovingRule());
        runRule(events, "积压", () -> overstockRule());
        return events;
    }

    private void runRule(List<AlertEvent> sink, String ruleName, Supplier<AlertEvent> rule) {
        try {
            AlertEvent event = rule.get();
            if (event != null) {
                sink.add(event);
            }
        } catch (Exception e) {
            log.warn("预警规则[{}]执行失败,本轮跳过 :{}", ruleName, e.getMessage(), e);
        }
    }

    /** 低库存:全量分页扫 inventory,可用 ≤ 阈值命中,聚一条通知(明细 topN) */
    private AlertEvent lowStockRule() {
        int lowStockThreshold = runtime.alertLowStockThreshold();
        int topN = runtime.alertTopN();
        List<InventoryQueryApi.InventoryView> hits = scanPages(pageNo -> inventoryQueryApi.pageInventory(
                InventoryQueryApi.InventoryFilter.builder()
                        .pageNo(pageNo).pageSize(props.getScanPageSize()).build()),
                row -> row.qtyAvailable() != null && row.qtyAvailable() <= lowStockThreshold);
        if (hits.isEmpty()) {
            return null;
        }
        List<String> details = hits.stream().limit(topN)
                .map(row -> StrUtil.format("sku {} 仓 {} 可用 {}", row.skuId(), row.warehouseId(), row.qtyAvailable()))
                .toList();
        return AlertEvent.builder()
                .notifyType(AlertEvent.TYPE_LOW_STOCK)
                .title("低库存预警")
                .content(detailContent(StrUtil.format("低库存 SKU 共 {} 条(阈值≤{})",
                        hits.size(), lowStockThreshold), details, hits.size()))
                .build();
    }

    /** 发货超时:分页扫 WAIT_SHIP 订单,下单时间早于 now-N 小时命中 */
    private AlertEvent shipTimeoutRule(LocalDateTime now) {
        long shipTimeoutHours = runtime.alertShipTimeoutHours();
        int topN = runtime.alertTopN();
        LocalDateTime deadline = now.minusHours(shipTimeoutHours);
        List<OrderQueryApi.OrderView> hits = scanPages(pageNo -> orderQueryApi.pageOrders(
                OrderQueryApi.OrderFilter.builder()
                        .orderStatus(STATUS_WAIT_SHIP).pageNo(pageNo).pageSize(props.getScanPageSize()).build()),
                row -> row.orderTime() != null && row.orderTime().isBefore(deadline));
        if (hits.isEmpty()) {
            return null;
        }
        List<String> details = hits.stream().limit(topN)
                .map(row -> StrUtil.format("订单 {}({})下单于 {}", row.platformOrderId(), row.platform(), row.orderTime()))
                .toList();
        return AlertEvent.builder()
                .notifyType(AlertEvent.TYPE_SHIP_TIMEOUT)
                .title("发货超时预警")
                .content(detailContent(StrUtil.format("待发货超 {} 小时订单共 {} 单",
                        shipTimeoutHours, hits.size()), details, hits.size()))
                .build();
    }

    /** 退款异常:分页扫 REFUNDED 售后单,窗口内按店铺聚合计数,达阈值店铺命中 */
    private AlertEvent refundRule(LocalDateTime now) {
        long refundWindowHours = runtime.alertRefundWindowHours();
        int refundCountThreshold = runtime.alertRefundCountThreshold();
        int topN = runtime.alertTopN();
        LocalDateTime since = now.minusHours(refundWindowHours);
        List<AftersaleQueryApi.AftersaleView> hits = scanPages(pageNo -> aftersaleQueryApi.pageAftersales(
                AftersaleQueryApi.AftersaleFilter.builder()
                        .status(STATUS_REFUNDED).pageNo(pageNo).pageSize(props.getScanPageSize()).build()),
                row -> row.createdAt() != null && !row.createdAt().isBefore(since));
        Map<Long, Integer> countByShop = new HashMap<>();
        hits.forEach(row -> countByShop.merge(row.shopId(), 1, Integer::sum));
        List<Map.Entry<Long, Integer>> hitShops = countByShop.entrySet().stream()
                .filter(entry -> entry.getValue() >= refundCountThreshold)
                .sorted((a, b) -> b.getValue() - a.getValue())
                .toList();
        if (hitShops.isEmpty()) {
            return null;
        }
        List<String> details = hitShops.stream().limit(topN)
                .map(entry -> StrUtil.format("店铺 {} 退款 {} 单", entry.getKey(), entry.getValue()))
                .toList();
        return AlertEvent.builder()
                .notifyType(AlertEvent.TYPE_REFUND_ABNORMAL)
                .title("退款异常预警")
                .content(detailContent(StrUtil.format("近 {} 小时退款≥{}单的店铺共 {} 家",
                        refundWindowHours, refundCountThreshold, hitShops.size()),
                        details, hitShops.size()))
                .build();
    }

    /** 滞销:有可用库存但动销窗口内零销量(销量合计=0)命中,聚一条通知(明细 topN) */
    private AlertEvent slowMovingRule() {
        int slowMovingDays = runtime.alertSlowMovingDays();
        int topN = runtime.alertTopN();
        List<InventoryQueryApi.InventoryView> stocked = scanInventoryStocked();
        if (stocked.isEmpty()) {
            return null;
        }
        Map<Long, Integer> soldBySku = soldBySku(stocked, slowMovingDays);
        List<InventoryQueryApi.InventoryView> hits = stocked.stream()
                .filter(row -> soldBySku.getOrDefault(row.skuId(), 0) == 0)
                .toList();
        if (hits.isEmpty()) {
            return null;
        }
        List<String> details = hits.stream().limit(topN)
                .map(row -> StrUtil.format("sku {} 仓 {} 可用 {}", row.skuId(), row.warehouseId(), row.qtyAvailable()))
                .toList();
        return AlertEvent.builder()
                .notifyType(AlertEvent.TYPE_SLOW_MOVING)
                .title("滞销预警")
                .content(detailContent(StrUtil.format("近 {} 天零动销且有库存 SKU 共 {} 条",
                        slowMovingDays, hits.size()), details, hits.size()))
                .build();
    }

    /** 积压:日均销量>0 且 可用库存/日均销量 ≥ 覆盖阈值(动销速率按滞销同窗口)命中,聚一条通知 */
    private AlertEvent overstockRule() {
        int slowMovingDays = runtime.alertSlowMovingDays();
        int overstockDays = runtime.alertOverstockDays();
        int topN = runtime.alertTopN();
        List<InventoryQueryApi.InventoryView> stocked = scanInventoryStocked();
        if (stocked.isEmpty()) {
            return null;
        }
        Map<Long, Integer> soldBySku = soldBySku(stocked, slowMovingDays);
        List<String> hits = new ArrayList<>();
        int total = 0;
        for (InventoryQueryApi.InventoryView row : stocked) {
            int sold = soldBySku.getOrDefault(row.skuId(), 0);
            if (sold <= 0) {
                continue;
            }
            double dailyAvg = (double) sold / slowMovingDays;
            long coverDays = (long) Math.ceil(row.qtyAvailable() / dailyAvg);
            if (coverDays < overstockDays) {
                continue;
            }
            total++;
            if (hits.size() < topN) {
                hits.add(StrUtil.format("sku {} 仓 {} 可用 {}≈{} 天",
                        row.skuId(), row.warehouseId(), row.qtyAvailable(), coverDays));
            }
        }
        if (total == 0) {
            return null;
        }
        String suffix = total > hits.size() ? " 等" : "";
        return AlertEvent.builder()
                .notifyType(AlertEvent.TYPE_OVERSTOCK)
                .title("积压预警")
                .content(StrUtil.format("库存可支撑 ≥{} 天的 SKU 共 {} 条,明细: {}{}",
                        overstockDays, total, String.join("; ", hits), suffix))
                .build();
    }

    /** 扫描有可用库存的库存行(qty_available>0),滞销/积压共用前置集 */
    private List<InventoryQueryApi.InventoryView> scanInventoryStocked() {
        return scanPages(pageNo -> inventoryQueryApi.pageInventory(
                        InventoryQueryApi.InventoryFilter.builder()
                                .pageNo(pageNo).pageSize(props.getScanPageSize()).build()),
                row -> row.skuId() != null && row.qtyAvailable() != null && row.qtyAvailable() > 0);
    }

    /** 批量取动销窗口内销量合计(空集不触契约) */
    private Map<Long, Integer> soldBySku(List<InventoryQueryApi.InventoryView> rows, int slowMovingDays) {
        return salesQueryApi.sumQtyBySku(rows.stream()
                .map(InventoryQueryApi.InventoryView::skuId)
                .collect(Collectors.toSet()), slowMovingDays);
    }

    /** 通用分页扫描:逐页拉取解包逐行判定,返回全部命中行;null 页/空页/不足页/达 scanMaxRows 四条件停 */
    private <T> List<T> scanPages(IntFunction<QueryPage<T>> pageFetcher, Predicate<T> hitWhen) {
        List<T> hits = new ArrayList<>();
        int scanned = 0;
        int pageNo = 1;
        while (scanned < props.getScanMaxRows()) {
            QueryPage<T> page = pageFetcher.apply(pageNo);
            List<T> rows = page == null ? null : page.list();
            if (CollUtil.isEmpty(rows)) {
                break;
            }
            rows.stream().filter(hitWhen).forEach(hits::add);
            scanned += rows.size();
            pageNo++;
            if (rows.size() < props.getScanPageSize()) {
                break;
            }
        }
        return hits;
    }

    /** 通知正文:汇总句 + 明细列表(超出 topN 截断以"等"收尾,写侧另有 1000 截断兜底) */
    private String detailContent(String summary, List<String> details, int totalHits) {
        if (CollUtil.isEmpty(details)) {
            return summary;
        }
        String suffix = totalHits > details.size() ? " 等" : "";
        return summary + ",明细: " + String.join("; ", details) + suffix;
    }
}
