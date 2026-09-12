package com.own.erp.finance.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.finance.response.PaymentSummaryRow;
import com.own.erp.finance.response.PlatformReceiptRow;
import com.own.erp.finance.response.PurchasePaidRow;
import com.own.erp.finance.response.PaymentRecordResponse;
import com.own.erp.finance.response.SupplierPayableRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 资金流水查询 Mapper(#31,XML 见 resources/mapper/PaymentQueryMapper.xml):
 *     资金流追踪查询面收口本接口——流水列表(join 往来方名)/采购已付聚合/供应商应付视图/
 *     平台回款聚合/期间汇总。跨域表(supplier/purchase_order/shop)只读 join,
 *     finance 拥有查询视图 SQL,Java 依赖仍走契约(同 RefundReconciliationMapper/ProfitQueryMapper 先例,不破坏铁律 2)
 */
public interface PaymentQueryMapper {

    /** 流水分页(按收付款时间倒序;默认滤 VOIDED,includeVoided=true 为对账口径含作废) */
    Page<PaymentRecordResponse> pageRows(Page<PaymentRecordResponse> page,
                                         @Param("direction") String direction,
                                         @Param("bizType") String bizType,
                                         @Param("partyType") String partyType,
                                         @Param("partyId") Long partyId,
                                         @Param("paidFrom") LocalDateTime paidFrom,
                                         @Param("paidTo") LocalDateTime paidTo,
                                         @Param("includeVoided") Boolean includeVoided);

    /** 采购单已付聚合:Σ NORMAL 采购付款分摊(按 purchase_order.id 分组,无分摊的单不在返回中) */
    List<PurchasePaidRow> sumPaidByPoIds(@Param("poIds") Collection<Long> poIds);

    /** 供应商应付/已付分页(子查询口径见 XML;草稿采购单不计应付,未分摊挂账款不计已付) */
    Page<SupplierPayableRow> pageSupplierPayables(Page<SupplierPayableRow> page);

    /** 平台回款聚合(按店铺,期间过滤;跨币种原币 SUM 仅混合参考,精确口径看 CNY 列) */
    List<PlatformReceiptRow> listPlatformReceipts(@Param("paidFrom") LocalDateTime paidFrom,
                                                  @Param("paidTo") LocalDateTime paidTo);

    /** 期间 CNY 汇总(缺汇率行只计数不进合计) */
    PaymentSummaryRow summary(@Param("paidFrom") LocalDateTime paidFrom,
                              @Param("paidTo") LocalDateTime paidTo);
}
