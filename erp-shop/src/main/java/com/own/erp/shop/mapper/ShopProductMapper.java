package com.own.erp.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.shop.entity.ShopProduct;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 店铺商品 Mapper:通用 CRUD 走 BaseMapper;listing 同步 upsert 为拉单专用(#5),SQL 见 mapper/ShopProductMapper.xml
 */
public interface ShopProductMapper extends BaseMapper<ShopProduct> {

    /** uk(shop_id, platform_product_id) 冲突即刷新平台侧快照,product_id 绑定关系不被覆盖 */
    int upsert(ShopProduct product);
}
