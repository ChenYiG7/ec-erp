package com.own.erp.platform.adapter.douyin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.own.erp.platform.AuthToken;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.TreeMap;

/**
 * @author : chenyi
 * @Date : 2026/9/13
 * @Description : 抖店 OAuth 授权客户端(防腐层 adapter,token.create/token.refresh):
 *         - 授权码换 Token:/token/create,param_json={"code","grant_type":"authorization_code"};
 *         - 刷新:/token/refresh,param_json={"grant_type":"refresh_token","refresh_token":...},
 *           抖店刷新**即轮换**(新 access_token+refresh_token,旧对失效),access_token 7 天/refresh_token 14 天(docs 先例);
 *         - 两接口都要求 sign(本类用调用方传入的 appKey/appSecret 现场签名,复用 {@link DouyinSigner});
 *         - 凭证经 AuthToken 返回后由 erp-shop 授权中心加密落库(docs/04,本类不含任何凭证存储);
 *         - 端点可配(baseUrl 注入),假服务单测不出网(docs/07 §10);真凭证联调时校准签名口径(docs/07 §8)
 */
final class DouyinTokenClient {

    private static final String PATH_CREATE = "/token/create";
    private static final String PATH_REFRESH = "/token/refresh";
    private static final String METHOD_CREATE = "token.create";
    private static final String METHOD_REFRESH = "token.refresh";

    private final String baseUrl;
    private final String v;
    private final String signMethod;
    private final Clock clock;
    private final RestClient restClient;

    DouyinTokenClient(String baseUrl, String v, String signMethod, Clock clock) {
        this.baseUrl = baseUrl;
        this.v = v;
        this.signMethod = signMethod;
        this.clock = clock;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** 授权码换 Token(authCode 有效期 10 分钟,换取即作废) */
    AuthToken exchangeToken(String authCode, String appKey, String appSecret) {
        ObjectNode paramJson = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        paramJson.put("code", authCode);
        paramJson.put("grant_type", "authorization_code");
        JsonNode data = call(METHOD_CREATE, PATH_CREATE, paramJson, appKey, appSecret);
        return toAuthToken(data);
    }

    /** 刷新 Token:抖店刷新返回并轮换(新 access_token+refresh_token,旧对失效) */
    AuthToken refreshToken(String refreshToken, String appKey, String appSecret) {
        ObjectNode paramJson = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        paramJson.put("grant_type", "refresh_token");
        paramJson.put("refresh_token", refreshToken);
        JsonNode data = call(METHOD_REFRESH, PATH_REFRESH, paramJson, appKey, appSecret);
        return toAuthToken(data);
    }

    /** 现场签名 + GET;信封拆解与业务失败语义复用 {@link DouyinApiSupport} 同款(invoke 解包私有,此处就地实现) */
    private JsonNode call(String method, String path, ObjectNode paramJson, String appKey, String appSecret) {
        String sortedParamJson = DouyinSigner.sortedParamJson(paramJson);
        TreeMap<String, String> params = DouyinSigner.buildSignedParams(
                appKey, appSecret, method, sortedParamJson, v, signMethod, Instant.now(clock).getEpochSecond());
        URI url = UriComponentsBuilder.fromUriString(baseUrl).path(path).queryParams(asMulti(params))
                .build().toUri();
        JsonNode body;
        try {
            body = restClient.get().uri(url).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("抖店 " + path + " 调用失败:HTTP " + e.getStatusCode().value(), e);
        }
        if (body == null || !body.has("code")) {
            throw new IllegalStateException("抖店 " + path + " 响应缺少 code 信封(脏响应)");
        }
        int code = body.path("code").asInt(-1);
        if (code != 0) {
            String msg = body.path("msg").asText(null);
            throw new IllegalStateException("抖店 " + path + " 业务失败:code=" + code
                    + (msg == null ? "" : ", msg=" + msg));
        }
        return body.path("data");
    }

    /** access_token 过期时间 = 签发时刻 + expires_in 秒(秒级);refresh_token 有效期 14 天由平台管理 */
    private AuthToken toAuthToken(JsonNode data) {
        if (data == null || !data.has("access_token")) {
            throw new IllegalStateException("抖店 token 响应缺 access_token(脏响应)");
        }
        long expiresIn = data.path("expires_in").asLong(0);
        AuthToken token = new AuthToken();
        token.setAccessToken(data.path("access_token").asText());
        token.setRefreshToken(data.path("refresh_token").asText(null));
        token.setExpireAt(Instant.now(clock).plusSeconds(expiresIn));
        return token;
    }

    private static MultiValueMap<String, String> asMulti(TreeMap<String, String> params) {
        MultiValueMap<String, String> multi = new LinkedMultiValueMap<>();
        params.forEach(multi::add);
        return multi;
    }
}