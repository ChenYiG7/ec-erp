package com.own.erp.contract;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 商品库只读查询契约(#6 三期 AI 地基):erp-ai 工具取数唯一正道(铁律 2,禁横向依赖 erp-goods),
 *         实现收口 erp-api(GoodsQueryApiImpl,委托 ProductService.pageProducts/getSkuByCode)。
 *         参数/返回全 record 不引 MP 类型;SKU 批量翻译(#7 专条)走 REST /api/goods/skus/batch,不经本契约
 */
public interface GoodsQueryApi {

    /** 商品 SPU 分页查询(keyword 走商品名/编码包含匹配——商品域现网口径);分页大小钳制 1..100 */
    QueryPage<ProductView> pageProducts(ProductFilter filter);

    /**
     * SKU 编码精确查(唯一键 sku_code;不存在返回 null)。AI 答"这个 SKU 的成本/物流属性"走这里;
     * 带 SPU 名称的翻译结构走 REST /api/goods/skus/batch(#7),不带金额
     */
    SkuView findSkuByCode(String skuCode);

    /**
     * 商品过滤条件 + 分页入参:keyword/categoryId 均可空;pageNo/pageSize 为 int,
     * @Builder 不设时默认 0,经 page()/size() 归一后生效
     */
    @Builder
    record ProductFilter(

            /** 关键字(商品名/编码,口径同商品域现网查询) */
            String keyword,

            /** 类目ID(product_category.id,精确,可空) */
            Long categoryId,

            /** 页码(从 1 起) */
            int pageNo,

            /** 页大小 */
            int pageSize
    ) {

        /** 归一页码(<1 按 1) */
        public int page() {
            return Math.max(pageNo, 1);
        }

        /** 归一页大小(未传/非法按默认 20,上限 100) */
        public int size() {
            return pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        }
    }

    /** 商品 SPU 行视图(不含 attrs_json 大字段,AI 场景无需销售属性全集) */
    @Builder
    record ProductView(

            /** 商品ID(product.id) */
            Long id,

            /** 内部SPU编码,唯一 */
            String spuCode,

            /** 商品名称 */
            String name,

            /** 类目ID(product_category.id) */
            Long categoryId,

            /** 品牌ID(brand.id) */
            Long brandId,

            /** 1启用 0停用 */
            Integer status
    ) {
    }

    /** SKU 行视图(内部 SKU 全量可读字段:成本/跨境物流属性,补货与定价建议的取数基础) */
    @Builder
    record SkuView(

            /** SKU ID(product_sku.id) */
            Long id,

            /** 所属商品ID(product.id) */
            Long productId,

            /** 内部 SKU 编码,唯一 */
            String skuCode,

            /** 条码/EAN/UPC */
            String barcode,

            /** 成本价(本位币,DECIMAL(12,4)) */
            BigDecimal costPrice,

            /** 重量(克) */
            Integer weightG,

            /** 海关编码(跨境) */
            String hsCode,

            /** 含电标记:1含电 0不含(跨境物流属性) */
            Integer battery,

            /** 1启用 0停用 */
            Integer status
    ) {
    }
}
