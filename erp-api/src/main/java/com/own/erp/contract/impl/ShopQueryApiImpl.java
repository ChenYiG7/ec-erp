package com.own.erp.contract.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.contract.QueryPage;
import com.own.erp.contract.ShopQueryApi;
import com.own.erp.shop.request.query.ShopQuery;
import com.own.erp.shop.response.ShopResponse;
import com.own.erp.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : ShopQueryApi 实现(#6 tools 扩容,编排胶水收口 erp-api,docs/07 §2.2):
 *         erp-ai 工具取数委托 erp-shop ShopService 读出口(pageShops/getShopById,自带脱敏);
 *         entity→契约 record 显式逐字段映射(经域 Response 中转,禁反射拷贝)。
 *         契约 ShopView 不收 Response 的 appKey/accessToken 字段——凭证对模型零消费场景,编译期即不出契约
 */
@Component
@RequiredArgsConstructor
public class ShopQueryApiImpl implements ShopQueryApi {

    private final ShopService shopService;

    @Override
    public QueryPage<ShopView> pageShops(ShopFilter filter) {
        ShopQuery query = new ShopQuery();
        query.setPlatform(filter.platform());
        query.setStatus(filter.status());
        query.setPageNo(filter.page());
        query.setPageSize(filter.size());
        Page<ShopResponse> page = shopService.pageShops(query);
        return QueryPage.of(page.getRecords().stream().map(ShopQueryApiImpl::toView).toList(),
                page.getTotal());
    }

    @Override
    public ShopView getShop(Long shopId) {
        ShopResponse shop = shopService.getShopById(shopId);
        return shop == null ? null : toView(shop);
    }

    /** Response → 行视图显式逐字段映射(凭证字段源头上不取,漏字段编译期可见) */
    private static ShopView toView(ShopResponse shop) {
        return ShopView.builder()
                .id(shop.id())
                .platform(shop.platform())
                .shopName(shop.shopName())
                .sellerId(shop.sellerId())
                .status(shop.status())
                .tokenExpireAt(shop.tokenExpireAt())
                .build();
    }
}
