package com.own.erp.contract;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 库存日快照只读查询契约(#6 库存快照数据面,周转报表/存量趋势读侧):
 *         erp-ai/报表域取数唯一正道(铁律 2),实现收口 erp-api(InventorySnapshotQueryApiImpl,
 *         委托 erp-inventory InventorySnapshotDailyService)。参数/返回全 record 不引 MP 类型;
 *         ⚠️ **快照只增不可回溯**——历史日期只能读已有快照行(存量无法由 inventory_flow 简单反推,
 *         V2 再评估);无 page() 全量分页,序列上限 365 天防长区间拉爆(扩展调用方按窗口循环)
 */
public interface InventorySnapshotQueryApi {

    /**
     * 单 SKU 时间序(按日期升序,趋势/周转率用):warehouseId 为空 = 跨仓聚合 SUM 四量,
     * 仓库列置 0;非法入参(空/逆序)由实现返回空集合。
     * limit 入参为空按 365 钳制,1..365 区间合法,超出钳到 365
     */
    List<SnapshotView> listSeries(Long skuId, Long warehouseId, LocalDate from, LocalDate to, Integer limit);

    /**
     * 行视图(快照日×SKU×仓库 存量四量):@Builder 防相邻同类型字段错位(同 InventoryChangeCommand)
     */
    @Builder
    record SnapshotView(

            /** 快照日期 */
            LocalDate statDate,

            /** 内部SKU ID */
            Long skuId,

            /** 仓库ID(跨仓聚合时置 0) */
            Long warehouseId,

            /** 在库快照 */
            Integer qtyOnHand,

            /** 占用快照 */
            Integer qtyLocked,

            /** 在途快照 */
            Integer qtyTransit,

            /** 可用快照 = 在库 - 占用 */
            Integer qtyAvailable
    ) {
    }
}
