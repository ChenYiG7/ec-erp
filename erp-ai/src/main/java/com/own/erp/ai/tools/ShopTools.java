package com.own.erp.ai.tools;

import com.own.erp.contract.QueryPage;
import com.own.erp.contract.ShopQueryApi;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 店铺查询工具(#6 tools 扩容,qihang 11 类 checklist Shop 类,随 ShopQueryApi 契约开工具)。
 *         只读铁律(铁律 7):方法全部走 ShopQueryApi 只读契约,无任何写路径;
 *         凭证相关字段契约层就不存在(ShopQueryApi javadoc);契约接口注入一律 @Lazy 断构造环(docs/07 §2.2)
 */
@Component
public class ShopTools {

    private final ShopQueryApi shopQueryApi;

    public ShopTools(@Lazy ShopQueryApi shopQueryApi) {
        this.shopQueryApi = shopQueryApi;
    }

    @Tool(description = "分页查询店铺列表(按ID倒序)。过滤条件均可选,全部不传=全量分页;返回店铺摘要与总数")
    public QueryPage<ShopQueryApi.ShopView> listShops(
            @ToolParam(required = false, description = "平台枚举名:TAOBAO/AMAZON等,精确过滤") String platform,
            @ToolParam(required = false, description = "状态:1=启用 0=停用,精确过滤") Integer status,
            @ToolParam(required = false, description = "页码,从1起,不传默认1") Integer pageNo,
            @ToolParam(required = false, description = "页大小,不传默认20,最大100") Integer pageSize) {
        return shopQueryApi.pageShops(ShopQueryApi.ShopFilter.builder()
                .platform(platform)
                .status(status)
                .pageNo(pageNo == null ? 0 : pageNo)
                .pageSize(pageSize == null ? 0 : pageSize)
                .build());
    }

    @Tool(description = "按店铺ID查店铺详情(含令牌过期时间,可判断授权是否有效)。店铺不存在返回 null")
    public ShopQueryApi.ShopView getShop(
            @ToolParam(description = "店铺ID(shop.id)") Long shopId) {
        return shopQueryApi.getShop(shopId);
    }
}
