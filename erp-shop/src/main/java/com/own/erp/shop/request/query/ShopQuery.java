package com.own.erp.shop.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 店铺分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShopQuery extends PageQuery {

    /** 平台编码,精确过滤(可选) */
    private String platform;

    /** 状态:1=启用 0=停用(可选) */
    private Integer status;
}
