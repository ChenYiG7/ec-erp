package com.own.erp.platform.gateway;

import com.own.erp.platform.AuthToken;
import com.own.erp.platform.PlatformClient;
import com.own.erp.platform.PlatformInboundPlanRequest;
import com.own.erp.platform.PlatformInboundShipment;
import com.own.erp.platform.PlatformShipment;
import com.own.erp.platform.PlatformTransportContent;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.ShopSession;
import com.own.erp.platform.unified.UnifiedOrder;
import com.own.erp.platform.unified.UnifiedProduct;
import com.own.erp.platform.unified.UnifiedRefund;
import com.own.erp.platform.unified.UnifiedSettlement;

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
    public List<UnifiedSettlement> pullSettlements(ShopSession session) {
        rateGuard.acquire(platform(), session.getShopId(), PlatformRateGuard.BUCKET_PULL);
        return delegate.pullSettlements(session);
    }

    @Override
    public void uploadTracking(ShopSession session, PlatformShipment shipment) {
        rateGuard.acquire(platform(), session.getShopId(), PlatformRateGuard.BUCKET_WRITE);
        delegate.uploadTracking(session, shipment);
    }

    @Override
    public String fetchWaybill(ShopSession session, String platformOrderId) {
        rateGuard.acquire(platform(), session.getShopId(), PlatformRateGuard.BUCKET_WRITE);
        return delegate.fetchWaybill(session, platformOrderId);
    }

    // ---- FBA 入库(#35):计划生成/板箱回传走回写桶,收货状态拉取走拉取桶 ----

    @Override
    public List<PlatformInboundShipment> createInboundShipmentPlan(ShopSession session,
                                                                   PlatformInboundPlanRequest request) {
        rateGuard.acquire(platform(), session.getShopId(), PlatformRateGuard.BUCKET_WRITE);
        return delegate.createInboundShipmentPlan(session, request);
    }

    @Override
    public boolean putTransportContent(ShopSession session, String shipmentId, PlatformTransportContent content) {
        rateGuard.acquire(platform(), session.getShopId(), PlatformRateGuard.BUCKET_WRITE);
        return delegate.putTransportContent(session, shipmentId, content);
    }

    @Override
    public List<PlatformInboundShipment> pullInboundShipments(ShopSession session, List<String> shipmentIds) {
        rateGuard.acquire(platform(), session.getShopId(), PlatformRateGuard.BUCKET_PULL);
        return delegate.pullInboundShipments(session, shipmentIds);
    }
}
