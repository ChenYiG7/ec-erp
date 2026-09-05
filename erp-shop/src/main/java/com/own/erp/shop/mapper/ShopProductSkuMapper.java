package com.own.erp.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.shop.entity.ShopProductSku;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : SKU映射 Mapper:通用 CRUD 走 BaseMapper;订单翻译取映射/同步 upsert/自动匹配取源为拉单专用(#4/#5),SQL 见 mapper/ShopProductSkuMapper.xml
 */
public interface ShopProductSkuMapper extends BaseMapper<ShopProductSku> {

    /** 按店铺 + seller_sku 批量取已绑定映射(仅 seller_sku/sku_id 两列,#4 订单明细翻译) */
    List<ShopProductSku> selectBoundByShopAndSellerSkus(@Param("shopId") Long shopId,
                                                        @Param("sellerSkus") Collection<String> sellerSkus);

    /** uk(shop_product_id, seller_sku) 冲突即刷新平台侧快照,sku_id/match_status 绑定状态不被覆盖(#5) */
    int upsert(ShopProductSku sku);

    /** 该店全部未绑定映射(仅 id/seller_sku 两列,#5 自动匹配取源) */
    List<ShopProductSku> selectUnboundByShop(@Param("shopId") Long shopId);
}
