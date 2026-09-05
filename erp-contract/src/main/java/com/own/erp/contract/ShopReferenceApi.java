package com.own.erp.contract;

import java.util.Collection;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 店铺外部引用计数契约(#3 删除校验,接口模块方案):
 *         erp-shop 删店铺前调用,实现收口 erp-api(ShopReferenceApiImpl)——
 *         订单(shop_order)/售后(aftersale_order)为跨域引用,取数走归属域 Service;
 *         listing 与 pull_log 属 erp-shop 域内,不经本接口直接查。零依赖零实现(铁律 2)
 */
public interface ShopReferenceApi {

    /** 店铺被平台订单引用的行数(shop_order.shop_id);>0 即禁删 */
    long countOrderRefs(Collection<Long> shopIds);

    /** 店铺被售后单引用的行数(aftersale_order.shop_id);>0 即禁删 */
    long countAftersaleRefs(Collection<Long> shopIds);
}
