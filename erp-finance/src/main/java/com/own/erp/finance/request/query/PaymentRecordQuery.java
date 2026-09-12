package com.own.erp.finance.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 资金流水分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定)
 *     #31 过滤件:方向/业务类型/往来方/收付款期间;默认滤 VOIDED(作废留痕不进日常流水),
 *     includeVoided=true 时含作废行(对账口径,前端须显式注明)
 */
/**
 * 保持 class(模型可变性分级 docs/07 §1 的继承例外):record 不能继承类,
 * 而 XxxQuery 必须继承 PageQuery 的钳制分页——可变性豁免,后续 PageQuery 重构再议
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PaymentRecordQuery extends PageQuery {

    /** 资金方向:EXPENSE/INCOME(精确,可空) */
    private String direction;

    /** 业务类型:PURCHASE_PAYMENT/SETTLEMENT_RECEIPT/MANUAL_ADJUST(精确,可空) */
    private String bizType;

    /** 往来方类型:SUPPLIER/PLATFORM/OTHER(精确,可空) */
    private String partyType;

    /** 往来方ID(supplier.id/shop.id,精确,可空) */
    private Long partyId;

    /** 收付款时间起(含) */
    private LocalDateTime paidFrom;

    /** 收付款时间止(含) */
    private LocalDateTime paidTo;

    /** 是否含已作废流水(默认 false 只看 NORMAL;对账口径显式传 true) */
    private Boolean includeVoided;
}
