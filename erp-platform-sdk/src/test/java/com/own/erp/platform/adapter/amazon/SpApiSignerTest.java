package com.own.erp.platform.adapter.amazon;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/5
 * @Description : SigV4 签名器单测(AIR:固定时钟、纯内存、不出网,docs/07 §10):
 *     期望签名值由 Amazon 官方实现 botocore 1.42.72 SigV4Auth 冻结时钟 2026-09-05T00:00:00Z 生成
 *     (脚本 scripts/gen_sigv4_expectations.py;官方 aws-sig-v4-test-suite 套件离线拉不到,以官方实现交叉验证替代),
 *     五用例覆盖:最简 GET / 常规查询 / 需 RFC3986 编码的查询(%3A)/ POST JSON body / STS 临时凭证;
 *     密钥为 AWS 文档惯用示例值,非真实凭证。
 */
class SpApiSignerTest {

    private static final Instant FROZEN = Instant.parse("2026-09-05T00:00:00Z");
    private static final String HOST = "sellingpartnerapi-na.amazon.com";
    private static final String SCOPE_CREDENTIAL =
            "AKIDELECTEXAMPLE/20260905/us-east-1/execute-api/aws4_request";
    private static final SpApiSigner.AwsCredentials LONG_TERM =
            new SpApiSigner.AwsCredentials("AKIDELECTEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", null);

    private final SpApiSigner signer = new SpApiSigner();

    private SpApiSigner.SignedHeaders sign(String method, String path, Map<String, String> query,
                                           Map<String, String> headers, String payload,
                                           SpApiSigner.AwsCredentials credentials) {
        return signer.sign(new SpApiSigner.SpApiRequest(method, path, query, headers, payload),
                credentials, "us-east-1", "execute-api", FROZEN);
    }

    @Test
    void vanillaGetMatchesBotocoreSignature() {
        SpApiSigner.SignedHeaders signed = sign("GET", "/orders/v0/orders", Map.of(),
                Map.of("Host", HOST), null, LONG_TERM);

        assertEquals("20260905T000000Z", signed.xAmzDate());
        assertEquals("AWS4-HMAC-SHA256 Credential=" + SCOPE_CREDENTIAL
                        + ", SignedHeaders=host;x-amz-date"
                        + ", Signature=20fbc96c8142dc111f158be94bf87d8e661fb08a2eb4c7784c9a4bb43aa3febb",
                signed.authorization());
        // 规范请求中间产物:空行位置 / 空 payload 哈希(空串 SHA-256,官方文档常量)
        assertEquals("GET\n/orders/v0/orders\n\nhost:" + HOST + "\nx-amz-date:20260905T000000Z\n\n"
                        + "host;x-amz-date\ne3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                signer.canonicalRequest(
                        new SpApiSigner.SpApiRequest("GET", "/orders/v0/orders", Map.of(), Map.of("Host", HOST), null),
                        new TreeMap<>(Map.of("host", HOST, "x-amz-date", "20260905T000000Z"))));
    }

    @Test
    void getWithQueryMatchesBotocore() {
        SpApiSigner.SignedHeaders signed = sign("GET", "/orders/v0/orders",
                Map.of("MarketplaceIds", "ATVPDKIKX0DER", "MaxResults", "100"),
                Map.of("Host", HOST), null, LONG_TERM);

        assertEquals("AWS4-HMAC-SHA256 Credential=" + SCOPE_CREDENTIAL
                        + ", SignedHeaders=host;x-amz-date"
                        + ", Signature=53eca0e70576c96fedf8a4141f88eac6453a3dd539ac987d16a37ec6390217f7",
                signed.authorization());
        // 回传的查询串与签名严格一致(编码 + 排序),调用方原样拼 URL
        assertEquals("MarketplaceIds=ATVPDKIKX0DER&MaxResults=100", signed.canonicalQueryString());
    }

    @Test
    void getWithIsoTimeQueryEncodesColon() {
        SpApiSigner.SignedHeaders signed = sign("GET", "/orders/v0/orders",
                Map.of("LastUpdatedAfter", "2026-09-01T00:00:00Z", "MarketplaceIds", "ATVPDKIKX0DER"),
                Map.of("Host", HOST), null, LONG_TERM);

        assertEquals("AWS4-HMAC-SHA256 Credential=" + SCOPE_CREDENTIAL
                        + ", SignedHeaders=host;x-amz-date"
                        + ", Signature=ddba948ad820bbe107d27d6786e9dbdc88843f65d64726f1d845a5be385f4725",
                signed.authorization());
        assertEquals("LastUpdatedAfter=2026-09-01T00%3A00%3A00Z&MarketplaceIds=ATVPDKIKX0DER",
                signed.canonicalQueryString());
    }

    @Test
    void postJsonBodySignsPayloadHash() {
        SpApiSigner.SignedHeaders signed = sign("POST", "/orders/v0/orders/123-4567890-1234567", Map.of(),
                Map.of("Host", HOST, "Content-Type", "application/json"),
                "{\"marketplaceIds\":[\"ATVPDKIKX0DER\"]}", LONG_TERM);

        assertEquals("AWS4-HMAC-SHA256 Credential=" + SCOPE_CREDENTIAL
                        + ", SignedHeaders=content-type;host;x-amz-date"
                        + ", Signature=4f931da43f7f47cd47cb93aeef374b88fab06a88bbc42ecf77c2f07bb5eb8726",
                signed.authorization());
    }

    @Test
    void sessionTokenParticipatesInSignature() {
        SpApiSigner.AwsCredentials temporary = new SpApiSigner.AwsCredentials(
                "AKIDELECTEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", "AQoEXAMPLETOKEN");

        SpApiSigner.SignedHeaders signed = sign("GET", "/orders/v0/orders", Map.of(),
                Map.of("Host", HOST), null, temporary);

        assertEquals("AWS4-HMAC-SHA256 Credential=" + SCOPE_CREDENTIAL
                        + ", SignedHeaders=host;x-amz-date;x-amz-security-token"
                        + ", Signature=dd067342c84c2cb9fcd04c160f02405e4030592cd64965d0af11c3c37c7c2042",
                signed.authorization());
    }

    @Test
    void missingHostHeaderRejected() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> sign("GET", "/orders/v0/orders", Map.of(), Map.of("X-Amz-Date", "20260905T000000Z"),
                        null, LONG_TERM));
        assertTrue(exception.getMessage().contains("Host"), exception.getMessage());
    }

    @Test
    void tokenMismatchBetweenHeaderAndCredentialsRejected() {
        SpApiSigner.AwsCredentials temporary = new SpApiSigner.AwsCredentials(
                "AKIDELECTEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", "AQoEXAMPLETOKEN");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> sign("GET", "/orders/v0/orders", Map.of(),
                        Map.of("Host", HOST, "X-Amz-Security-Token", "DIFFERENT-TOKEN"), null, temporary));

        assertTrue(exception.getMessage().contains("x-amz-security-token"), exception.getMessage());
    }
}
