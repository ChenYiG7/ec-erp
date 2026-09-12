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

    /**
     * 数据权限授权店铺集(#27① 店铺轴):服务器权威装配——Controller/契约实现层在 GET 绑定后
     * 强制覆盖为 CurrentUserApi.currentShopIds()(不信任前端自带值);
     * null=不限(admin);非空=店铺 IN 过滤;空列表=不可见任何店铺数据(Service 短路零结果)
     */
    private java.util.List<Long> shopIds;

    /** 平台编码,精确过滤(可选) */
    private String platform;

    /** 状态:1=启用 0=停用(可选) */
    private Integer status;
}
