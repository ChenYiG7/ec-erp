package com.own.erp.contract;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 店铺只读查询契约(#6 tools 扩容:Shop/Purchase/Delivery 随查询契约开工具,qihang 11 类
 *         checklist 口径"数据齐一个开一个"):erp-ai 工具取数唯一正道(铁律 2,禁横向依赖 erp-shop),
 *         实现收口 erp-api(ShopQueryApiImpl,委托 ShopService.pageShops/getShopById)。
 *         行视图只带 AI 查询所需字段——凭证三件(appKey/accessToken/appSecret)一律不出契约,
 *         掩码值也不给(模型无消费场景,docs/07 §7 凭证纪律);AI 只读(铁律 7)
 */
public interface ShopQueryApi {

    /**
     * 店铺分页查询(按 id 倒序,服务端口径);过滤条件全空 = 全量分页。
     * 分页大小钳制 1..100(工具侧足够,服务端 PageQuery ≤500 兜底)
     */
    QueryPage<ShopView> pageShops(ShopFilter filter);

    /** 店铺详情(含令牌过期时间,答"店铺授权是否有效"走这里;不存在返回 null) */
    ShopView getShop(Long shopId);

    /**
     * 过滤条件 + 分页入参(全 record):pageNo/pageSize 为 int,@Builder 不设时默认 0,
     * 经 page()/size() 归一后生效(AI 侧漏传分页按第 1 页 / 20 条执行)
     */
    @Builder
    record ShopFilter(

            /** 平台编码(PlatformType 枚举名,精确,可空) */
            String platform,

            /** 状态:1=启用 0=停用(可空) */
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

    /** 店铺行视图 */
    @Builder
    record ShopView(

            /** 店铺ID(shop.id) */
            Long id,

            /** PlatformType 枚举名:TAOBAO/AMAZON/... */
            String platform,

            /** 店铺名称 */
            String shopName,

            /** 平台侧卖家/店铺标识(Amazon: SellerId;国内平台: 店铺 sid) */
            String sellerId,

            /** 1=启用 0=停用 */
            Integer status,

            /** 令牌过期时间(授权面;null=未授权) */
            LocalDateTime tokenExpireAt
    ) {
    }
}
