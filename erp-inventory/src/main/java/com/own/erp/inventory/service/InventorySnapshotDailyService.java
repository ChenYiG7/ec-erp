package com.own.erp.inventory.service;

import com.own.erp.inventory.entity.InventorySnapshotDaily;
import com.own.erp.inventory.mapper.InventorySnapshotDailyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 库存日快照服务(inventory_snapshot_daily,#6 库存快照数据面,docs/03 §7.2):
 *     写侧 = erp-api InventorySnapshotJob 每日低峰调 snapshot() 取当前存量 upsert(uk_sku_wh_date 幂等,
 *     同日重跑/补跑覆盖当日值);
 *     读侧 = 只读契约 InventorySnapshotQueryApi 实现(erp-api)委托 listSeries() 取单 SKU 序列
 *     (周转率/存量趋势)。时间入参由调用方提供(Job 经 Clock 派生当日,List 时前端/契约归一)——
 *     本服务无 Clock 依赖,符合域服务"无环境耦合"边界
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventorySnapshotDailyService {

    /** 单 SKU 序列最大条数(年区间):防止 AI/报表长区间拉爆;区间 > LIMIT 取最近 LIMIT 条 */
    private static final int SERIES_MAX_LIMIT = 365;

    private final InventorySnapshotDailyMapper inventorySnapshotDailyMapper;

    /**
     * 按日存量快照:从 inventory 全量四量落 snapshot(uk_sku_wh_date 冲突即覆盖当日值,
     * 同日重跑/补跑幂等);库存为 0 的行也入快照(零库存是有意义的事实:缺货持续天数靠它算);
     * 返回受影响行数(insert + update 合计)
     */
    public int snapshot(LocalDate statDate) {
        int affected = inventorySnapshotDailyMapper.upsertSnapshot(statDate);
        log.info("库存日快照完成:date={} 受影响 {} 行", statDate, affected);
        return affected;
    }

    /**
     * 单 SKU 序列(按日期升序,趋势/周转率用):warehouseId 为空 = 跨仓聚合(SUM 四量,仓库列置 0);
     * 非法入参(空/零/超界)直接空集合不触库,limit 钳制 1..SERIES_MAX_LIMIT
     */
    public List<InventorySnapshotDaily> listSeries(Long skuId, Long warehouseId,
                                                   LocalDate from, LocalDate to, Integer limit) {
        if (skuId == null || from == null || to == null || from.isAfter(to)) {
            return List.of();
        }
        int effectiveLimit = limit == null || limit <= 0 ? SERIES_MAX_LIMIT
                : Math.min(limit, SERIES_MAX_LIMIT);
        return inventorySnapshotDailyMapper.listSeries(skuId, warehouseId, from, to, effectiveLimit);
    }
}
