package com.own.erp.finance.mapper;

import com.own.erp.finance.reconcile.RefundSideRow;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 退款勾稽聚合查询 Mapper(#19④,XML 见 resources/mapper/RefundReconciliationMapper.xml):
 *     勾稽是财务域聚合视图——售后侧(aftersale_order join shop_order 取平台订单号)与结算侧
 *     (settlement_detail join settlement_report)两路聚合收口本接口,跨域表只读 join
 *     (finance 拥有勾稽视图 SQL,Java 依赖仍走契约,同 ProfitQueryMapper 先例,docs/07 铁律 2 不破坏)
 */
public interface RefundReconciliationMapper {

    /**
     * 售后侧退款聚合(按店铺+平台订单号+币种):已退款终态且金额非空,Σ refund_amount(正数,原币)
     */
    List<RefundSideRow> sumAftersaleRefundByPlatformOrder();

    /**
     * 结算侧退款聚合(按店铺+平台订单号+币种):PARSED 报告 REFUND 行,Σ(−amount)(报告原值为负,转正同向比对)
     */
    List<RefundSideRow> sumSettlementRefundByPlatformOrder();
}
