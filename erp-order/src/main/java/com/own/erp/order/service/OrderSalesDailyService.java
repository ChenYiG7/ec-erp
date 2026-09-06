package com.own.erp.order.service;

import com.own.erp.order.entity.OrderSalesDaily;
import com.own.erp.order.mapper.OrderSalesDailyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 订单销量日统计服务(#6 销量数据面,order_sales_daily 域整域收口):
 *     写侧 = SalesSnapshotJob 每日窗口重算(单语句原子 upsert,uk_sku_date 幂等可重试,
 *     覆盖状态回传/取消单修正);读侧 = 只读契约 SalesQueryApi 实现(erp-api)委托的
 *     近 N 天各 SKU 销量合计(补货动销公式/预警滞销积压规则数据面)。
 *     时间统一走注入 Clock(docs/07 §10)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSalesDailyService {

    private final OrderSalesDailyMapper orderSalesDailyMapper;
    private final Clock pullClock;

    /**
     * 窗口重算:[startDate, endDate) 已支付态订单明细按 支付日×SKU 聚合 upsert;
     * 单语句原子(无需事务),重试安全;返回受影响行数
     */
    public int rebuildWindow(LocalDate startDate, LocalDate endDateExclusive) {
        int affected = orderSalesDailyMapper.upsertWindow(startDate, endDateExclusive);
        log.info("销量日统计窗口重算完成:[{}, {}) 受影响 {} 行", startDate, endDateExclusive, affected);
        return affected;
    }

    /**
     * 近 N 天(含今日)各 SKU 销量合计:只返回有统计记录的 skuId,未记录的由调用方按 0 兜底;
     * 空入参/非法窗口直接空 map 不触库
     */
    public Map<Long, Integer> sumQtyBySku(Collection<Long> skuIds, int trailingDays) {
        if (skuIds == null || skuIds.isEmpty() || trailingDays < 1) {
            return Map.of();
        }
        LocalDate startDate = LocalDate.now(pullClock).minusDays(trailingDays - 1L);
        List<Map<String, Object>> rows = orderSalesDailyMapper.sumQtySince(startDate, List.copyOf(skuIds));
        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            if (row.get("skuId") instanceof Number sku && row.get("qtySold") instanceof Number qty) {
                result.put(sku.longValue(), qty.intValue());
            }
        }
        return result;
    }
}
