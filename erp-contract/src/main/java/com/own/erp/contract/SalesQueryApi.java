package com.own.erp.contract;

import java.util.Collection;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 销量只读查询契约(#6 销量数据面,查询契约第五件):erp-ai 读销量唯一正道
 *         (铁律 2,禁横向依赖 erp-order),实现收口 erp-api(SalesQueryApiImpl,委托
 *         OrderSalesDailyService.sumQtyBySku)。数据面 = order_sales_daily 日统计表
 *         (docs/03 §7.1,支付日×SKU,已支付态口径);返回仅含有统计记录的 skuId,
 *         未记录的 sku 由调用方按 0 兜底(零动销是合法业务语义,不缺席)
 */
public interface SalesQueryApi {

    /**
     * 近 N 天(含今日)各 SKU 销量合计(购买数量);空入参/非法窗口返回空 map
     *
     * @param skuIds       SKU ID 集合
     * @param trailingDays 统计窗口天数(含今日,≥1)
     */
    Map<Long, Integer> sumQtyBySku(Collection<Long> skuIds, int trailingDays);
}
