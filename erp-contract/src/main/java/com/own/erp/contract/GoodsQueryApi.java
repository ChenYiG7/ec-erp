package com.own.erp.contract;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

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
     * 单商品 SKU 行列表(#17 文案生成取数,2026-09-08 只加方法):按 product_id 查启用与否全部 SKU 行
     * (文案材料含规格属性,状态过滤交调用方);商品无 SKU 返回空列表
     */
    List<SkuView> listSkusByProductId(Long productId);

    /** 品牌名称精确查(brand 直连 Mapper 纯配置域口径;不存在返回 null)——文案生成 prompt 材料 */
    String findBrandNameById(Long brandId);

    /** 类目名称精确查(实现委托 ProductCategoryService;不存在返回 null)——文案生成 prompt 材料 */
    String findCategoryNameById(Long categoryId);

    /**
     * 商品过滤条件 + 分页入参:keyword/categoryId/status 均可空(status 只加字段不改语义,
     * #17 文案生成扫启用商品,2026-09-08);pageNo/pageSize 为 int,
     * @Builder 不设时默认 0,经 page()/size() 归一后生效
     */
    @Builder
    record ProductFilter(

            /** 关键字(商品名/编码,口径同商品域现网查询) */
            String keyword,

            /** 类目ID(product_category.id,精确,可空) */
            Long categoryId,

            /** 状态过滤(1启用 0停用,空=不过滤,#17 文案生成 2026-09-08 加) */
            Integer status,

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

    /**
     * 商品 SPU 行视图(AI 场景字段;attrsJson 销售属性原样 JSON 串 #17 文案生成 2026-09-08 只加字段——
     * 文案材料需要销售属性,tools 面不消费该字段无增量成本)
     */
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

            /** 销售属性(原样 JSON 串,可空) */
            String attrsJson,

            /** 1启用 0停用 */
            Integer status
    ) {
    }

    /** SKU 行视图(内部 SKU 全量可读字段:成本/跨境物流属性,补货与定价建议的取数基础;
     *  attrsJson 规格值原样 JSON 串 #17 文案生成 2026-09-08 只加字段) */
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

            /** 规格值(原样 JSON 串,可空) */
            String attrsJson,

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
