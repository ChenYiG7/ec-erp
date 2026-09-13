package com.own.erp.platform.adapter.douyin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * @author : chenyi
 * @Date : 2026/9/13
 * @Description : 抖店开放平台请求签名(防腐层 adapter,docs/07 §8 红线下按官方口径落地):
 *         - 任一 API 调用都必须携带 sign;签名串 = app_secret + 参与参数按名排序后 name+value 拼接 + app_secret;
 *           参与字段固定 app_key / method / param_json / timestamp / v(access_token 与 sign_method 不参与签名);
 *         - param_json 内部 **key 必须按字母序序列化**(签名与提交一致,官方「sign 签名规则」排序示例),
 *           由 {@link #sortedParamJson} 统一产出,禁止手拼 JSON 破坏 key 排序;
 *         - 值转义(官方 transformStr):& → \u0026、< → \u003c、> → \u003e、\b → \u0008;
 *         - 算法:sign_method=md5(32 位小写十六进制,官方将下线仅兼容保留)/ hmac-sha256(推荐,十六进制小写,
 *           摘要串仍为 app_secret 两端包裹、以 app_secret 为 HMAC 密钥,与官方推荐算法一致——**真凭证到位后 --force 校准一轮**,
 *           docs/07 §8);
 *         - 本类处处确定性(输入即输出),禁取系统时钟;时间戳由调用方传入(固定时钟单测 AIR 不出网,docs/07 §10);
 *         - 样例为官方规则推导,签名正确性以真凭证联调返回为准,禁 mock 自嗨。
 */
public final class DouyinSigner {

    private static final String PARAM_APP_KEY = "app_key";
    private static final String PARAM_METHOD = "method";
    private static final String PARAM_PARAM_JSON = "param_json";
    private static final String PARAM_TIMESTAMP = "timestamp";
    private static final String PARAM_V = "v";

    /** 参与签名的基础公共参数(会话无关);access_token/sign_method 不入此表(docs 明确排除) */
    private static final String[] SIGN_PARAM_KEYS =
            {PARAM_APP_KEY, PARAM_METHOD, PARAM_PARAM_JSON, PARAM_TIMESTAMP, PARAM_V};

    private DouyinSigner() {
    }

    /**
     * 生成一次 /sign 调用的完整提交参数(含 sign 与 sign_method):paramJson 须为
     * {@link #sortedParamJson} 产出的**按 key 排序**报文。返回 TreeMap 自然序,
     * 由调用方拼 query(仅含公共参数,会话级 access_token 由命令方另加 query 参)
     */
    public static TreeMap<String, String> buildSignedParams(String appKey, String appSecret, String method,
                                                            String sortedParamJson, String v, String signMethod,
                                                            long timestampSeconds) {
        TreeMap<String, String> params = new TreeMap<>(Map.of(
                PARAM_APP_KEY, appKey,
                PARAM_METHOD, method,
                PARAM_PARAM_JSON, sortedParamJson,
                PARAM_TIMESTAMP, String.valueOf(timestampSeconds),
                PARAM_V, v));
        params.put("sign", sign(params, appSecret, signMethod));
        params.put("sign_method", signMethod);
        return params;
    }

    /** 已按名排序的参与参数 → 签名摘要(md5/hmac-sha256 十六进制小写) */
    private static String sign(TreeMap<String, String> sortedParams, String appSecret, String signMethod) {
        StringBuilder content = new StringBuilder(appSecret);
        for (String key : SIGN_PARAM_KEYS) {
            content.append(key).append(transformStr(sortedParams.get(key)));
        }
        content.append(appSecret);
        return digest(content.toString(), appSecret, signMethod);
    }

    /**
     * signt算串 → 摘要:md5 直接取串;hmac-sha256 以 app_secret 为密钥。
     * 未知方法即抛(禁默认静默降级,错误配置显式暴露)
     */
    static String digest(String content, String appSecret, String signMethod) {
        if ("md5".equalsIgnoreCase(signMethod)) {
            return md5Hex(content);
        }
        if ("hmac-sha256".equalsIgnoreCase(signMethod)) {
            return hmacSha256Hex(content, appSecret);
        }
        throw new IllegalArgumentException("不支持的 sign_method: " + signMethod);
    }

    static String md5Hex(String content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("MD5").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }

    static String hmacSha256Hex(String content, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 计算失败", e);
        }
    }

    /**
     * 业务报文序列化为**按 key 字母序**的紧凑 JSON(抖店签名要求 param_json key 排序):
     * 输入 java.util.Map(自然序无关),这里以 Jackson ObjectNode 按键排序输出;
     * 复杂结构(数组/嵌套对象)由调用方用 Jackson 树模型构建后再交给本方法规范(签名前提=唯一确定性序列化)
     */
    public static String sortedParamJson(com.fasterxml.jackson.databind.node.ObjectNode node) {
        return node == null ? null : sortedJson(node);
    }

    static String sortedJson(com.fasterxml.jackson.databind.JsonNode node) {
        if (!node.isObject()) {
            return node.toString();
        }
        StringBuilder sb = new StringBuilder("{");
        java.util.List<String> keys = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(keys::add);
        java.util.Collections.sort(keys);
        boolean first = true;
        for (String key : keys) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(key).append("\":").append(sortedJson(node.get(key)));
        }
        sb.append('}');
        return sb.toString();
    }

    /** 官方值转义(transformStr):& → \u0026、< → \u003c、> → \u003e、\b → \u0008;null 视为空串 */
    static String transformStr(String value) {
        return value == null ? ""
                : value.replace("&", "\\u0026")
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("\b", "\\u0008");
    }
}