package com.own.erp.shop.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 拉取日志分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定;pageNo/pageSize 钳制 ≤500)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PullLogQuery extends PageQuery {

    /** 店铺ID(shop.id) */
    private Long shopId;

    /** ORDER/PRODUCT/REFUND */
    private String dataType;

    /** 1成功0失败 */
    private Integer success;
}
