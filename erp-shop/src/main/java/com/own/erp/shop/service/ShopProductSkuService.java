package com.own.erp.shop.service;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.GoodsSkuApi;
import com.own.erp.shop.entity.ShopProductSku;
import com.own.erp.shop.mapper.ShopProductSkuMapper;
import com.own.erp.shop.request.query.ShopProductSkuQuery;
import com.own.erp.shop.response.ShopProductSkuResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : SKU映射服务(系统心脏):shop_product_sku 域整域收口,Controller 不直连 Mapper(docs/07 §2.1)
 *     match_status:0待匹配(sku_id为NULL) 1商家编码自动 2人工
 *     自动匹配 autoMatch(#5):seller_sku == product_sku.sku_code 精确匹配 → 回填 sku_id/match_status=1,
 *     人工绑定不被覆盖,匹配不中保持 NULL 进待匹配列表;订单落库按本表映射翻译 shop_order_item.sku_id(未绑定 NULL)
 */
@Service
@RequiredArgsConstructor
public class ShopProductSkuService {

    /** 商家编码自动匹配 */
    public static final int MATCH_AUTO = 1;
    /** 人工绑定 */
    public static final int MATCH_MANUAL = 2;

    private final ShopProductSkuMapper shopProductSkuMapper;
    private final GoodsSkuApi goodsSkuApi;

    /** 分页查询(过滤:店铺商品/绑定SKU/匹配状态;match_status=0 即待匹配列表) */
    public Page<ShopProductSkuResponse> page(ShopProductSkuQuery query) {
        LambdaQueryWrapper<ShopProductSku> wrapper = new LambdaQueryWrapper<ShopProductSku>()
                .eq(query.getShopProductId() != null, ShopProductSku::getShopProductId, query.getShopProductId())
                .eq(query.getSkuId() != null, ShopProductSku::getSkuId, query.getSkuId())
                .eq(query.getMatchStatus() != null, ShopProductSku::getMatchStatus, query.getMatchStatus())
                .orderByDesc(ShopProductSku::getId);
        Page<ShopProductSku> result = shopProductSkuMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()), wrapper);
        Page<ShopProductSkuResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(ShopProductSkuResponse::from).toList());
        return responsePage;
    }

    /** 详情,出参 Response;不存在返回 null */
    public ShopProductSkuResponse getById(Long id) {
        ShopProductSku mapping = shopProductSkuMapper.selectById(id);
        return mapping == null ? null : ShopProductSkuResponse.from(mapping);
    }

    /**
     * 订单明细 SKU 翻译取映射(#4 编排侧):按店铺 + seller_sku 批量查"已绑定"的映射,
     * 返回 seller_sku → 内部 sku_id;未绑定的 seller_sku 不在结果里,订单明细 sku_id 落 NULL(订单照常入库)。
     * JOIN 查询见 ShopProductSkuMapper.xml;同店同 seller_sku 跨 listing 重复时取先到值(脏数据兜底,不抛错阻塞拉单)
     */
    public Map<String, Long> mapSellerSkuToSkuId(Long shopId, Collection<String> sellerSkus) {
        if (shopId == null || CollUtil.isEmpty(sellerSkus)) {
            return Map.of();
        }
        return shopProductSkuMapper.selectBoundByShopAndSellerSkus(shopId, sellerSkus).stream()
                .collect(Collectors.toMap(ShopProductSku::getSellerSku, ShopProductSku::getSkuId, (first, second) -> first));
    }

    /**
     * 绑定引用计数(#5 SKU 删除校验,经 erp-contract 接口暴露):skuIds 在本域的绑定行数。
     * 绑定即引用——解除绑定(置 NULL)是删 SKU 的前置动作
     */
    public long countBoundBySkuIds(Collection<Long> skuIds) {
        if (CollUtil.isEmpty(skuIds)) {
            return 0;
        }
        Long count = shopProductSkuMapper.selectCount(new LambdaQueryWrapper<ShopProductSku>()
                .in(ShopProductSku::getSkuId, skuIds));
        return count == null ? 0L : count;
    }

    /**
     * 商家编码自动匹配(#5,系统心脏):seller_sku == product_sku.sku_code 的映射行回填 sku_id、置 match_status=1。
     * skuIdBySellerSku 由 erp-api 编排调 erp-goods 精确匹配得出(本域禁横向依赖,铁律 2);
     * 只处理 sku_id 仍为 NULL 的行——人工绑定(match_status=2)不被自动匹配覆盖;
     * 无码可匹配的行保持 NULL + match_status=0,继续留在待匹配列表。
     *
     * @param skuIdBySellerSku sku_code → product_sku.id(仅含命中的)
     * @return 本次自动回填行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int autoMatch(Long shopId, Map<String, Long> skuIdBySellerSku) {
        if (shopId == null || CollUtil.isEmpty(skuIdBySellerSku)) {
            return 0;
        }
        int matched = 0;
        for (ShopProductSku unbound : shopProductSkuMapper.selectUnboundByShop(shopId)) {
            Long skuId = skuIdBySellerSku.get(unbound.getSellerSku());
            if (skuId == null) {
                continue;
            }
            // 局部更新仅 sku_id/match_status 两列,主键点更;首次同步量大时也可接受(单店一次性),慢了再批量化
            ShopProductSku update = new ShopProductSku();
            update.setId(unbound.getId());
            update.setSkuId(skuId);
            update.setMatchStatus(MATCH_AUTO);
            shopProductSkuMapper.updateById(update);
            matched++;
        }
        return matched;
    }

    /**
     * 人工绑定内部SKU(docs/07 核心流程):回填 sku_id 并置 match_status=2。
     * 局部更新(仅 sku_id/match_status 两列),重复绑定同一 SKU 幂等直接返回;
     * sku_id 存在性经 erp-contract 接口模块校验(2026-09-04 收口,实现收口 erp-api,本域禁横向依赖 erp-goods)
     */
    public void bind(Long id, Long skuId) {
        if (skuId == null) {
            throw new BusinessException("内部SKU不能为空");
        }
        if (!goodsSkuApi.existsSku(skuId)) {
            throw new BusinessException("内部SKU不存在:" + skuId);
        }
        ShopProductSku mapping = shopProductSkuMapper.selectById(id);
        if (mapping == null) {
            throw new BusinessException("SKU映射记录不存在:" + id);
        }
        if (skuId.equals(mapping.getSkuId()) && mapping.getMatchStatus() != null && mapping.getMatchStatus() == MATCH_MANUAL) {
            return;
        }
        ShopProductSku update = new ShopProductSku();
        update.setId(id);
        update.setSkuId(skuId);
        update.setMatchStatus(MATCH_MANUAL);
        shopProductSkuMapper.updateById(update);
    }
}
