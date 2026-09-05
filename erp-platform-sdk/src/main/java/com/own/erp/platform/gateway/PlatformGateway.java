package com.own.erp.platform.gateway;

import com.own.erp.platform.AuthToken;
import com.own.erp.platform.PlatformClient;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.ShopSession;
import com.own.erp.platform.unified.UnifiedOrder;
import com.own.erp.platform.unified.UnifiedProduct;
import com.own.erp.platform.unified.UnifiedRefund;

import java.time.Instant;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : PlatformClient 限流装饰器(#3,docs/04 PlatformGateway 横切层):数据面调用
 *         (拉取/回写)先经 {@link PlatformRateGuard} 取许可再透传;授权面(buildAuthUrl/exchangeToken/
 *         refreshToken)直接透传——LWA 令牌端点不走 SP-API 调用配额。由 AdapterRegistry 统一包装,
 *         各 adapter 实现类对限流零感知(横切不散落)。
 */
public class PlatformGateway implements PlatformClient {

    private final PlatformClient delegate;
    private final PlatformRateGuard rateGuard;

    public PlatformGateway(PlatformClient delegate, PlatformRateGuard rateGuard) {
        this.delegate = delegate;
        this.rateGuard = rateGuard;
    }

    @Override
    public PlatformType platform() {
        return delegate.platform();
    }

    // ---- 授权面:直接透传,不限流 ----

    @Override
    public String buildAuthUrl(String redirectUri, String state) {
        return delegate.buildAuthUrl(redirectUri, state);
    }

    @Override
    public AuthToken exchangeToken(String authCode, String redirectUri, String appKey, String appSecret) {
        return delegate.exchangeToken(authCode, redirectUri, appKey, appSecret);
    }

    @Override
    public AuthToken refreshToken(String refreshToken, String appKey, String appSecret) {
        return delegate.refreshToken(refreshToken, appKey, appSecret);
    }

    // ---- 数据面:先取限流许可 ----

    @Override
    public List<UnifiedOrder> pullOrders(ShopSession session, Instant start, Instant end) {
        rateGuard.acquire(platform(), session.getShopId(), PlatformRateGuard.BUCKET_PULL);
        return delegate.pullOrders(session, start, end);
    }

    @Override
    public List<UnifiedProduct> pullProducts(ShopSession session, Instant start, Instant end) {
        rateGuard.acquire(platform(), session.getShopId(), PlatformRateGuard.BUCKET_PULL);
        return delegate.pullProducts(session, start, end);
    }

    @Override
    public List<UnifiedRefund> pullRefunds(ShopSession session, Instant start, Instant end) {
        rateGuard.acquire(platform(), session.getShopId(), PlatformRateGuard.BUCKET_PULL);
        return delegate.pullRefunds(session, start, end);
    }

    @Override
    public void uploadTracking(ShopSession session, String platformOrderId, String trackingNo, String logisticsCode) {
        rateGuard.acquire(platform(), session.getShopId(), PlatformRateGuard.BUCKET_WRITE);
        delegate.uploadTracking(session, platformOrderId, trackingNo, logisticsCode);
    }

    @Override
    public String fetchWaybill(ShopSession session, String platformOrderId) {
        rateGuard.acquire(platform(), session.getShopId(), PlatformRateGuard.BUCKET_WRITE);
        return delegate.fetchWaybill(session, platformOrderId);
    }
}
