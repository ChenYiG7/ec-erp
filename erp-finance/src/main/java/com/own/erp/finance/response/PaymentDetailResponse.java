package com.own.erp.finance.response;

import lombok.Builder;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 资金流水详情(#31):流水主体 + 分摊行(仅 PURCHASE_PAYMENT 有行,其余为空列表)
 */
@Builder
public record PaymentDetailResponse(

        /** 流水主体 */
        PaymentRecordResponse payment,

        /** 分摊明细(采购付款按采购单展开;结算回款/手工调整为空列表) */
        List<PaymentAllocResponse> allocs
) {
}
