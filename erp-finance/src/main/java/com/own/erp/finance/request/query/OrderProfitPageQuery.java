package com.own.erp.finance.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 实时销售利润分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定);
 *     GET 形态域内入参,服务层转契约 OrderProfitQuery(契约模型不出 transport 层,docs/07 §2.2)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderProfitPageQuery extends PageQuery {

    /** 店铺ID(可空=全店铺) */
    private Long shopId;

    /** 平台(PlatformType 枚举名,可空=全平台) */
    private String platform;

    /** 内部SKU(可空=全SKU) */
    private Long skuId;

    /** 下单时间起(含,可空) */
    private LocalDateTime dateFrom;

    /** 下单时间止(不含,可空) */
    private LocalDateTime dateTo;
}
