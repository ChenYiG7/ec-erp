package com.own.erp.platform.adapter.douyin;

import cn.hutool.core.util.StrUtil;
import com.own.erp.platform.AuthToken;
import com.own.erp.platform.PlatformClient;
import com.own.erp.platform.PlatformShipment;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.ShopSession;
import com.own.erp.platform.unified.UnifiedOrder;
import com.own.erp.platform.unified.UnifiedProduct;
import com.own.erp.platform.unified.UnifiedRefund;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/13
 * @Description : 抖店 adapter(P0 首个国内平台,2026-09-13 拍板,防腐层实现类):接单/商品/售后/发货回传,
 *         OAuth 授权对接 erp-shop 授权中心(凭证 AES-GCM 加密落库 docs/04,本类不含任何凭证存储);
 *         未实现的方法显式抛 UnsupportedOperationException(Phase 1 纪律,禁假成功):
 *         - fetchWaybill(电子面单取号)= #17 面单账户/CLODOP 批量打单链路待独立立项后接线;
 *         - pullSettlements(结算报告形态=API 查询+文件下载,见 playbook 差异表)= 联调拍板后接线,
 *           SPI default 本就抛 Unsupported;
 *         - FBA 三方法(Inbound 计划/板箱回传/收货拉取)=FBA 平台独有,国内平台不适用,SPI default 抛 Unsupported;
 *         限流按平台+店+桶走 PlatformGateway 横切(erp.rate.douyin.* 配置),本类不裸绕过;
 *         默认不注册 Bean:erp.adapter.douyin.enabled=true 才启用(未启用拉单调度 AdapterRegistry miss 静默跳过)
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "erp.adapter.douyin.enabled", havingValue = "true")
public class DouyinClient implements PlatformClient {

    /** 授权页前缀(与实际占位由资质申请后按控制台实际授权 URL 校准,见 buildAuthUrl);空配置即报错 */
    @Value("${erp.adapter.douyin.authorize-url:}")
    private String authorizeUrl;

    /** 应用标识(app_key):参与授权跳转与业务签名;未配置时授权地址不可用 */
    @Value("${erp.adapter.douyin.app-key:}")
    private String appKey;

    private final DouyinTokenClient douyinTokenClient;
    private final DouyinOrdersClient douyinOrdersClient;
    private final DouyinProductClient douyinProductClient;
    private final DouyinRefundsClient douyinRefundsClient;
    private final DouyinLogisticsClient douyinLogisticsClient;

    @Override
    public PlatformType platform() {
        return PlatformType.DOUYIN;
    }

    /**
     * 授权跳转地址:拼 app_key + redirect_uri + state(回调原样带回防 CSRF);
     * 精确授权页 URL(含挂载应用标识的参数名)以资质申请后控制台实际为准——未配置 authorize-url 即抛友好错误,
     * 真凭证联调时校准(docs/07 §8)
     */
    @Override
    public String buildAuthUrl(String redirectUri, String state) {
        if (StrUtil.isBlank(authorizeUrl) || StrUtil.isBlank(appKey)) {
            throw new IllegalStateException("未配置 erp.adapter.douyin.authorize-url / app-key,无法生成抖店授权地址");
        }
        String sep = authorizeUrl.contains("?") ? "&" : "?";
        return authorizeUrl + sep + "app_key=" + encode(appKey)
                + "&redirect_uri=" + encode(redirectUri)
                + "&state=" + encode(state);
    }

    /** 授权码换 Token(code 10min 有效,换取即作废;凭证加密落库归授权中心) */
    @Override
    public AuthToken exchangeToken(String authCode, String redirectUri, String appKey, String appSecret) {
        return douyinTokenClient.exchangeToken(authCode, appKey, appSecret);
    }

    /** 刷新 Token:抖店刷新即轮换(新 access_token+refresh_token 生效,旧对失效,授权中心自动调度) */
    @Override
    public AuthToken refreshToken(String refreshToken, String appKey, String appSecret) {
        return douyinTokenClient.refreshToken(refreshToken, appKey, appSecret);
    }

    @Override
    public List<UnifiedOrder> pullOrders(ShopSession session, Instant start, Instant end) {
        return douyinOrdersClient.pullOrders(session, start, end);
    }

    @Override
    public List<UnifiedProduct> pullProducts(ShopSession session, Instant start, Instant end) {
        return douyinProductClient.pullProducts(session, start, end);
    }

    @Override
    public List<UnifiedRefund> pullRefunds(ShopSession session, Instant start, Instant end) {
        return douyinRefundsClient.pullRefunds(session, start, end);
    }

    /** 发货回传(order.logisticsAdd,官方当前整单出库) */
    @Override
    public void uploadTracking(ShopSession session, PlatformShipment shipment) {
        douyinLogisticsClient.uploadTracking(session, shipment);
    }

    /**
     * 电子面单取号(/logistics/newCreateOrder 链路)属 #17 面单账户管理/CLODOP 批量打单,
     * 待该独立项落地后接线;严禁返回假运单号(docs/07 §8)
     */
    @Override
    public String fetchWaybill(ShopSession session, String platformOrderId) {
        throw new UnsupportedOperationException("抖店电子面单取号(#17 面单账户/CLODOP)随 #17 项落地后接线");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}