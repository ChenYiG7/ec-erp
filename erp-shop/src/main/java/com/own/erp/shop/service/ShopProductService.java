package com.own.erp.shop.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.constant.PullConsts;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.platform.unified.UnifiedProduct;
import com.own.erp.shop.entity.ShopProduct;
import com.own.erp.shop.entity.ShopProductSku;
import com.own.erp.shop.mapper.ShopProductMapper;
import com.own.erp.shop.mapper.ShopProductSkuMapper;
import com.own.erp.shop.request.query.ShopProductQuery;
import com.own.erp.shop.response.ShopProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 店铺商品(listing)服务:shop_product 域整域收口,Controller 不直连 Mapper(docs/07 §2.1)。
 *     listing 为拉取同步写入表,不开放人工写接口;product_id 绑定关系由匹配流程维护;
 *     唯一写入口 saveUnifiedProduct(#5):同步 upsert 不覆盖已绑定的 product_id/sku 绑定状态,
 *     自动匹配跨域查 erp-goods,由 erp-api 编排调 ShopProductSkuService.autoMatch
 */
@Service
@RequiredArgsConstructor
public class ShopProductService {

    private final ShopProductMapper shopProductMapper;
    private final ShopProductSkuMapper shopProductSkuMapper;

    /** 分页查询(过滤:店铺/绑定SPU/平台商品ID) */
    public Page<ShopProductResponse> page(ShopProductQuery query) {
        LambdaQueryWrapper<ShopProduct> wrapper = new LambdaQueryWrapper<ShopProduct>()
                .eq(query.getShopId() != null, ShopProduct::getShopId, query.getShopId())
                .eq(query.getProductId() != null, ShopProduct::getProductId, query.getProductId())
                .eq(StrUtil.isNotBlank(query.getPlatformProductId()), ShopProduct::getPlatformProductId, query.getPlatformProductId())
                .orderByDesc(ShopProduct::getId);
        Page<ShopProduct> result = shopProductMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()), wrapper);
        Page<ShopProductResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(ShopProductResponse::from).toList());
        return responsePage;
    }

    /** 详情,出参 Response;不存在返回 null */
    public ShopProductResponse getById(Long id) {
        ShopProduct shopProduct = shopProductMapper.selectById(id);
        return shopProduct == null ? null : ShopProductResponse.from(shopProduct);
    }

    /**
     * listing 引用计数(#5 SPU 删除校验,经 erp-contract 接口暴露):productIds 被
     * shop_product.product_id 引用的行数——任一店铺同步过该商品即存在引用
     */
    public long countListingByProductIds(Collection<Long> productIds) {
        if (CollUtil.isEmpty(productIds)) {
            return 0;
        }
        Long count = shopProductMapper.selectCount(new LambdaQueryWrapper<ShopProduct>()
                .in(ShopProduct::getProductId, productIds));
        return count == null ? 0L : count;
    }

    /** listing 引用计数(#3 店铺删除校验,域内调用):shopIds 的 listing 行数,>0 即该店同步过商品 */
    public long countByShopIds(Collection<Long> shopIds) {
        if (CollUtil.isEmpty(shopIds)) {
            return 0;
        }
        Long count = shopProductMapper.selectCount(new LambdaQueryWrapper<ShopProduct>()
                .in(ShopProduct::getShopId, shopIds));
        return count == null ? 0L : count;
    }

    /**
     * listing 同步唯一写入口(#5):UnifiedProduct → shop_product/shop_product_sku。
     * 主表 upsert(uk_shop_platform_product)→ uk 反查 id → 级联 upsert SKU 行(uk_shop_seller_sku);
     * 已绑定字段(product_id、sku_id、match_status)永不被同步覆盖;无 seller_sku 的平台 SKU 行
     * 无法映射,不入映射表。
     *
     * @return 店铺商品ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveUnifiedProduct(Long shopId, UnifiedProduct product) {
        if (shopId == null || product == null || StrUtil.isBlank(product.getPlatformProductId())) {
            throw new BusinessException(400, "listing 落库入参不完整:shopId/platformProductId 必填");
        }
        shopProductMapper.upsert(toEntity(shopId, product));
        ShopProduct saved = shopProductMapper.selectOne(new LambdaQueryWrapper<ShopProduct>()
                .eq(ShopProduct::getShopId, shopId)
                .eq(ShopProduct::getPlatformProductId, product.getPlatformProductId())
                .last("LIMIT 1"));
        if (saved == null) {
            // 理论不可达(upsert 后必存在),防御异常并发删单
            throw new BusinessException(500, "listing 落库后按唯一键反查失败,platformProductId=" + product.getPlatformProductId());
        }
        if (CollUtil.isNotEmpty(product.getSkus())) {
            for (UnifiedProduct.Sku sku : product.getSkus()) {
                if (StrUtil.isBlank(sku.getSellerSku())) {
                    // seller_sku 是映射的唯一抓手,缺码行只保留主表,不进映射表
                    continue;
                }
                ShopProductSku row = new ShopProductSku();
                row.setShopProductId(saved.getId());
                row.setSellerSku(sku.getSellerSku());
                row.setQuantity(sku.getStock());
                row.setPrice(sku.getPrice());
                row.setCurrency(sku.getCurrency());
                shopProductSkuMapper.upsert(row);
            }
        }
        return saved.getId();
    }

    /** UnifiedProduct → ShopProduct 显式逐字段映射(禁反射拷贝);platform_sku_id 仅单 SKU 商品可定 */
    private ShopProduct toEntity(Long shopId, UnifiedProduct product) {
        ShopProduct entity = new ShopProduct();
        entity.setShopId(shopId);
        entity.setPlatformProductId(product.getPlatformProductId());
        entity.setPlatformSkuId(CollUtil.size(product.getSkus()) == 1 ? product.getSkus().get(0).getPlatformSkuId() : null);
        entity.setListingStatus(product.getStatus());
        entity.setLastSyncAt(LocalDateTime.now(PullConsts.ZONE));
        return entity;
    }
}
