package com.own.erp.ai.tools;

import com.own.erp.contract.GoodsQueryApi;
import com.own.erp.contract.QueryPage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 商品库查询工具(#6,启航 11 类 checklist:Goods 类已开;余量待随查询契约扩容)。
 *         只读铁律(铁律 7):无任何写路径;契约接口注入一律 @Lazy 断构造环(docs/07 §2.2)。
 *         SKU 批量翻译(跨页 skuId 列可读化)不经本工具——走 REST /api/goods/skus/batch(#7 专条)
 */
@Component
public class GoodsTools {

    private final GoodsQueryApi goodsQueryApi;

    public GoodsTools(@Lazy GoodsQueryApi goodsQueryApi) {
        this.goodsQueryApi = goodsQueryApi;
    }

    @Tool(description = "分页搜索商品SPU(按关键字/类目;关键字匹配商品名或编码)。返回商品列表与总数")
    public QueryPage<GoodsQueryApi.ProductView> searchProducts(
            @ToolParam(required = false, description = "关键字,匹配商品名/编码") String keyword,
            @ToolParam(required = false, description = "类目ID,精确过滤") Long categoryId,
            @ToolParam(required = false, description = "页码,从1起,不传默认1") Integer pageNo,
            @ToolParam(required = false, description = "页大小,不传默认20,最大100") Integer pageSize) {
        return goodsQueryApi.pageProducts(GoodsQueryApi.ProductFilter.builder()
                .keyword(keyword)
                .categoryId(categoryId)
                .pageNo(pageNo == null ? 0 : pageNo)
                .pageSize(pageSize == null ? 0 : pageSize)
                .build());
    }

    @Tool(description = "按内部SKU编码精确查SKU全量信息(成本价/重量/跨境物流属性)。查无返回 null")
    public GoodsQueryApi.SkuView findSkuByCode(
            @ToolParam(description = "内部SKU编码(product_sku.sku_code,唯一)") String skuCode) {
        return goodsQueryApi.findSkuByCode(skuCode);
    }
}
