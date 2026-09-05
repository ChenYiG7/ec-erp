package com.own.erp.platform;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 支持的电商平台。
 *     domestic=true 为国内平台,false 为跨境平台。
 */
public enum PlatformType {

    // ---- 国内 ----
    TAOBAO("淘宝", true),
    JD("京东", true),
    PDD("拼多多", true),
    DOUYIN("抖店", true),
    WECHAT_SHOP("微信小店", true),
    KUAISHOU("快手小店", true),
    XHS("小红书", true),

    // ---- 跨境 ----
    AMAZON("亚马逊", false),
    EBAY("eBay", false),
    SHOPEE("Shopee", false),
    LAZADA("Lazada", false),
    TIKTOK_GLOBAL("TikTok Shop", false),
    ALIEXPRESS("速卖通", false),
    TEMU("Temu", false);

    private final String label;
    /** 是否国内平台(决定订单/发货/财务走国内链路还是跨境链路) */
    private final boolean domestic;

    PlatformType(String label, boolean domestic) {
        this.label = label;
        this.domestic = domestic;
    }

    public String getLabel() {
        return label;
    }

    public boolean isDomestic() {
        return domestic;
    }
}
