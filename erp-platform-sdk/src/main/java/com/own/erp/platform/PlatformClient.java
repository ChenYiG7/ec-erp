package com.own.erp.platform;

import com.own.erp.platform.unified.UnifiedOrder;
import com.own.erp.platform.unified.UnifiedProduct;
import com.own.erp.platform.unified.UnifiedRefund;

import java.time.Instant;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 平台适配器统一 SPI(防腐层核心)。
 *     每个平台一个实现类(Spring @Component 注册),由 AdapterRegistry 汇聚。
 *
 *     设计约束:
 *     1. 实现类内部只做"平台报文 → Unified* 模型"的翻译,不写业务逻辑;
 *     2. Token 的获取/刷新由 erp-shop 授权中心统一管理,实现类只消费 ShopSession;
 *     3. 所有限流/重试由外层(PlatformGateway)统一施加,见 docs/04-平台对接层设计.md;
 *     4. 分页拉取由实现类自行处理,返回整段时间窗数据。
 */
public interface PlatformClient {

    PlatformType platform();

    // ---- 授权 ----

    /**
     * 生成平台授权跳转地址(OAuth);免授权平台(如部分国内平台手动导入凭证)可返回 null
     */
    String buildAuthUrl(String redirectUri, String state);

    /**
     * 授权码换 Token
     */
    AuthToken exchangeToken(String authCode, String redirectUri, String appKey, String appSecret);

    /**
     * 刷新访问令牌(docs/04:刷新只在 erp-shop 授权中心触发,adapter 只提供能力、只消费 ShopSession)。
     * 无刷新机制的平台可不覆写(抛 UnsupportedOperationException)
     */
    default AuthToken refreshToken(String refreshToken, String appKey, String appSecret) {
        throw new UnsupportedOperationException("平台不支持 Token 刷新或未实现");
    }

    // ---- 数据拉取(增量,按时间窗) ----

    List<UnifiedOrder> pullOrders(ShopSession session, Instant start, Instant end);

    List<UnifiedProduct> pullProducts(ShopSession session, Instant start, Instant end);

    List<UnifiedRefund> pullRefunds(ShopSession session, Instant start, Instant end);

    // ---- 回写 ----

    /**
     * 上传物流单号(发货回传,MFN 自发货)。FBA/海外仓平台自履约不回传(抛 UnsupportedOperationException),
     * 由编排侧裁剪不装配;命令要素见 {@link PlatformShipment}
     * (2026-09-06 签名收口:原四散参形态缺行级 quantity/shipTime,撑不起 Amazon confirmShipment 必填项)
     */
    void uploadTracking(ShopSession session, PlatformShipment shipment);

    /**
     * 抓取平台电子面单(国内平台);跨境平台可返回 null
     */
    String fetchWaybill(ShopSession session, String platformOrderId);
}
