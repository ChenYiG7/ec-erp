package com.own.erp.platform.adapter.amazon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.own.erp.platform.AuthToken;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : LWA(Login with Amazon)令牌客户端:#3 Amazon adapter 的授权能力。
 *         - 覆盖授权码换 Token(grant_type=authorization_code)与刷新(grant_type=refresh_token),端点同为 /auth/o2/token;
 *         - base-url 可注入(单测起 JDK HttpServer 假服务,AIR 不出网,docs/07 §10);
 *         - 异常消息只带 HTTP 状态码,禁带凭证/响应原文(防日志泄密,docs/07 §7)
 */
public class LwaTokenClient {

    private final RestClient restClient;
    private final Clock clock;

    public LwaTokenClient(String baseUrl, Clock clock) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.clock = clock;
    }

    /** 授权码换 Token(appKey=LWA client_id,appSecret=client_secret) */
    public AuthToken exchangeToken(String authCode, String redirectUri, String appKey, String appSecret) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", authCode);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", appKey);
        form.add("client_secret", appSecret);
        return post(form);
    }

    /** 刷新 Token:刷新后旧 refresh_token 仍复用(LWA 不轮换,新 accessToken 生效) */
    public AuthToken refreshToken(String refreshToken, String appKey, String appSecret) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", appKey);
        form.add("client_secret", appSecret);
        return post(form);
    }

    private AuthToken post(MultiValueMap<String, String> form) {
        LwaTokenResponse response;
        try {
            response = restClient.post()
                    .uri("/auth/o2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(LwaTokenResponse.class);
        } catch (RestClientResponseException e) {
            // 响应原文可能回显错误场景描述,只透出状态码定位问题
            throw new IllegalStateException("LWA 令牌接口调用失败:HTTP " + e.getStatusCode().value(), e);
        }
        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("LWA 令牌接口响应缺失 accessToken");
        }
        AuthToken token = new AuthToken();
        token.setAccessToken(response.accessToken());
        token.setRefreshToken(response.refreshToken());
        token.setExpireAt(Instant.now(clock).plusSeconds(response.expiresIn()));
        return token;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LwaTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") long expiresIn) {
    }
}
