package com.own.erp.platform.adapter.douyin;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/13
 * @Description : 抖店签名器单测:签名串口径 = app_secret 两端 + app_key/method/param_json/timestamp/v
 *         按名拼接(值经官方 transformStr 转义);摘要期望值由官方 rules 推导并用 Python hashlib/hmac
 *         交叉验证(docs/07 §8,禁 mock 自嗨)。param_json key 排序由 {@link DouyinSigner#sortedParamJson} 保证。
 */
class DouyinSignerTest {

    private static final long TS = 1800000000L;

    @Test
    void md5MatchesReferenceDigest() {
        TreeMap<String, String> params = DouyinSigner.buildSignedParams(
                "appkey123", "secret123", "order.searchList", "{\"page\":0,\"size\":100}",
                "2", "md5", TS);

        // 摘要串 = secret + sort(app_key/method/param_json/timestamp/v)=name+value + secret
        assertEquals("a84d3597bbb7aaeb0540d1022623165c", params.get("sign"));
        assertEquals("md5", params.get("sign_method"));
        assertEquals("appkey123", params.get("app_key"));
        assertEquals("2", params.get("v"));
    }

    @Test
    void hmacSha256MatchesReferenceDigest() {
        TreeMap<String, String> params = DouyinSigner.buildSignedParams(
                "appkey123", "secret123", "order.searchList", "{\"page\":0,\"size\":100}",
                "2", "hmac-sha256", TS);

        assertEquals("8a5350b8fdacaa753c0885ab9859d382a31de4add09c6943c1d34298bd2165f1", params.get("sign"));
    }

    @Test
    void rejectsUnknownSignMethod() {
        assertThrows(IllegalArgumentException.class, () -> DouyinSigner.buildSignedParams(
                "k", "s", "m", "{\"a\":1}", "2", "md6", TS));
    }

    @Test
    void transformStrEscapesSpecialCharsPerOfficialRules() {
        // & → \u0026、< → \u003c、> → \u003e(空格/普通字符原样)
        assertEquals("{\"name\":\"a\\u003cb\\u0026c\\u003ed\"}", DouyinSigner.transformStr("{\"name\":\"a<b&c>d\"}"));
        // literal 退格符 → \u0008
        assertEquals("x\\u0008y", DouyinSigner.transformStr("x\by"));
        // null → 空串(不抛)
        assertEquals("", DouyinSigner.transformStr(null));
    }

    @Test
    void sortedParamJsonSortsKeysAlphabetically() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.set("zz", JsonNodeFactory.instance.booleanNode(true));
        ObjectNode orderAmount = node.putObject("order_amount");
        orderAmount.put("value", 100);
        node.put("aa", "first");
        ObjectNode nested = node.putObject("name");
        nested.put("rack", 1);
        nested.put("bar", 2);

        assertTrue(DouyinSigner.sortedJson(node).startsWith("{\"aa\":"), DouyinSigner.sortedJson(node));
        assertEquals("{\"aa\":\"first\",\"name\":{\"bar\":2,\"rack\":1},\"order_amount\":{\"value\":100},\"zz\":true}",
                DouyinSigner.sortedJson(node));
    }

    @Test
    void buildSignedParamsReturnsOrderedParamsWithSignAndSignMethod() {
        TreeMap<String, String> params = DouyinSigner.buildSignedParams(
                "k", "s", "method.x", "{}", "2", "md5", TS);

        // 签名参数固定五件 + sign + sign_method;TreeMap 自然序
        assertEquals(7, params.size());
        assertEquals(java.util.List.of("app_key", "method", "param_json", "sign", "sign_method", "timestamp", "v"),
                java.util.List.copyOf(params.keySet()));
        assertEquals("method.x", params.get("method"));
        assertEquals("2", params.get("v"));
        assertEquals(String.valueOf(TS), params.get("timestamp"));
    }
}