package com.own.erp.finance.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 汇率快照分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExchangeRateQuery extends PageQuery {

    /** 币种(ISO 4217,可空=全量) */
    private String currency;
}
