package com.own.erp.finance.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.finance.profit.OrderProfitAmountGroup;
import com.own.erp.finance.profit.OrderProfitLine;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 实时销售利润查询 Mapper(#19③,XML 见 resources/mapper/ProfitQueryMapper.xml):
 *     利润是财务域聚合视图——主查询(订单行 join 订单,已支付态三态)与成本/佣金两路聚合收口本接口,
 *     跨域表只读 join(finance 拥有利润视图 SQL,Java 依赖仍走契约,docs/07 铁律 2 不破坏);
 *     汇率不进 SQL——逐行按下单日回溯走 ExchangeRateService.resolveRate 唯一口径
 */
public interface ProfitQueryMapper {

    /**
     * 主查询分页(下单时间倒序;过滤条件见 XML;已支付态 WAIT_SHIP/SHIPPED/COMPLETED)
     */
    Page<OrderProfitLine> selectProfitLines(Page<OrderProfitLine> page,
                                            @Param("shopId") Long shopId,
                                            @Param("platform") String platform,
                                            @Param("skuId") Long skuId,
                                            @Param("dateFrom") LocalDateTime dateFrom,
                                            @Param("dateTo") LocalDateTime dateTo);

    /**
     * 主查询全量(汇总用,SQL 硬 LIMIT 防御 20000 行,量级增长落库方案随 V2 周期口径)
     */
    List<OrderProfitLine> selectProfitLinesAll(@Param("shopId") Long shopId,
                                               @Param("platform") String platform,
                                               @Param("skuId") Long skuId,
                                               @Param("dateFrom") LocalDateTime dateFrom,
                                               @Param("dateTo") LocalDateTime dateTo);

    /**
     * 出库成本聚合(按内部订单行):OUT_SHIP 移动加权快照经发货单行归集,Σ(−cost_amount)=正数 CNY
     */
    List<OrderProfitAmountGroup> sumCostByOrderItemIds(@Param("orderItemIds") List<Long> orderItemIds);

    /**
     * 平台佣金聚合(按店铺+平台订单行):settlement_detail COMMISSION 行,Σ amount 原币(带符号,佣金为负)
     */
    List<OrderProfitAmountGroup> sumCommissionByPlatformItemIds(
            @Param("platformItemIds") List<String> platformItemIds);
}
