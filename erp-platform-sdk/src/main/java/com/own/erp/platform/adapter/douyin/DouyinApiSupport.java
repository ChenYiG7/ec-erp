package com.own.erp.platform.adapter.douyin;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.own.erp.platform.gateway.RateLimitObserver;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author : chenyi
 * @Date : 2026/9/13
 * @Description : 抖店开放平台请求封装(防腐层 adapter 横切):统一「签名 + GET + 解包」。
 *         - 每个 API 按 docs/07 §8 签名(签名串 = app_secret + 排序参数 + app_secret,见 {@link DouyinSigner});
 *         - 交易数据面一律 GET + query(参数经 Spring UriComponentsBuilder 编码,zone 转义由签名器在签名串内完成);
 *           业务 param_json 过长切换 POST 属平台特性,联调时按需演进(TODO 槽位);
 *         - 响应统一信封 {code,msg,data,extra}:code!=0 抛 IllegalStateException(禁静默吞,拉单记 pull_log 重试);
 *           异常消息只带 code+短 msg,禁回显 data(可含账号/参数上下文,docs/07 §7);
 *         - 错误码 9 = 访问太频繁:照样上抛,由外层 PlatformGateway 限流 + 拉单重试背压;
 *         - 无限流响应头(x-amzn 系为跨境先例),真值校准随联调走 erp.rate.douyin.* 配置(docs/04)
 */
final class DouyinApiSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String appKey;
    private final String appSecret;
    private final String v;
    private final String signMethod;
    private final Clock clock;
    private final RestClient restClient;

    DouyinApiSupport(String baseUrl, String appKey, String appSecret, String v,
                     String signMethod, Clock clock) {
        this.baseUrl = baseUrl;
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.v = v;
        this.signMethod = signMethod;
        this.clock = clock;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** 会话类命令无需 access_token(如 token.create/token.refresh)调此变体 */
    JsonNode executeAnonymous(String method, String path, ObjectNode paramJson) {
        return execute(method, path, paramJson, null);
    }

    /** 交易数据面调用:paramJson 由各领域客户端构建(业务字段禁散落在本类),access_token 随 query 提交但不参与签名 */
    JsonNode execute(String method, String path, ObjectNode paramJson, String accessToken) {
        String sortedParamJson = DouyinSigner.sortedParamJson(paramJson);
        TreeMap<String, String> params = DouyinSigner.buildSignedParams(
                appKey, appSecret, method, sortedParamJson, v, signMethod, Instant.now(clock).getEpochSecond());
        if (StrUtil.isNotBlank(accessToken)) {
            params.put("access_token", accessToken);
        }
        return send(path, params);
    }

    private JsonNode send(String path, TreeMap<String, String> params) {
        // query 值可能含中文(company/退款理由等):不 encode 直接 toUri 会以未转义原文发送,
        // 平台侧收到的非 ASCII 字节被默认字符集替换为 ???? → 签名串与实际不一致。显式 UTF-8 编码保证往返无损。
        URI url = UriComponentsBuilder.fromUriString(baseUrl).path(path).queryParams(asMulti(params))
                .build().encode(StandardCharsets.UTF_8).toUri();
        JsonNode body;
        try {
            body = restClient.get().uri(url).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            // 只透状态码,禁回显响应原文(docs/07 §7)
            throw new IllegalStateException("抖店 " + path + " 调用失败:HTTP " + e.getStatusCode().value(), e);
        }
        return unwrapEnvelope(path, body);
    }

    /** 统一信封拆解:无 body/无 code 视为脏响应 → IllegalStateException;code!=0 抛带 code+msg 的运行时异常 */
    private JsonNode unwrapEnvelope(String path, JsonNode body) {
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

    private static MultiValueMap<String, String> asMulti(Map<String, String> params) {
        MultiValueMap<String, String> multi = new LinkedMultiValueMap<>();
        params.forEach(multi::add);
        return multi;
    }
}