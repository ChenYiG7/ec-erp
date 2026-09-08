package com.own.erp.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.order.entity.OrderSalesDaily;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 订单销量日统计 Mapper(order_sales_daily,#6 销量数据面):通用 CRUD 走 BaseMapper,
 *     窗口重算走 XML upsert(uk_sku_date 冲突即更新,幂等可重试),读侧聚合走 XML GROUP BY
 */
public interface OrderSalesDailyMapper extends BaseMapper<OrderSalesDaily> {

    /**
     * 窗口重算:已支付态(WAIT_SHIP/SHIPPED/COMPLETED)订单明细按 支付日×SKU 聚合 upsert 进 [startDate, endDate) 窗口;
     * uk_sku_date 冲突即覆盖(状态回传/取消单修正),幂等可重试;返回 affected 行数
     */
    int upsertWindow(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /** 近 N 天(含 startDate 起)各 SKU 销量合计:只返回有统计记录的 skuId,未记录的按 0 由调用方兜底 */
    List<Map<String, Object>> sumQtySince(@Param("startDate") LocalDate startDate,
                                          @Param("skuIds") List<Long> skuIds);

    /** 近 N 天(含 startDate 起)各 SKU 逐日销量序列(#6 补货算法 V2 需求波动 σ 数据面):
     *  只返回有统计记录的 skuId×statDate,窗口内零销日由调用方按 0 补齐 */
    List<Map<String, Object>> listQtySince(@Param("startDate") LocalDate startDate,
                                           @Param("skuIds") List<Long> skuIds);
}
