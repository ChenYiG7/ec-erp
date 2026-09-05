package com.own.erp.system.auth;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author : chenyi
 * @Date : 2026/9/5
 * @Description : JwtTokenService 单测(纯 JDK 固定密钥,不依赖 Spring,docs/07 §10)。
 *     重点:签发/解析往返、防篡改、严格 Base64 预校验(JJWT 流式解码会静默丢弃末尾悬挂 4 字符组,
 *     "合法 token+尾部追加字符"仍过验签的怪癖防护)、HS256 弱密钥/未设置密钥启动拦截(docs/07 §7)
 */
class JwtTokenServiceTest {

    /** 纯单测固定密钥(≥32 字节,仅测试可见);密钥无默认值后 setUp 必须显式注入 */
    private static final String TEST_SECRET = "0123456789abcdef0123456789abcdef-UNIT-TEST-ONLY";

    private JwtTokenService service;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(TEST_SECRET);
        service = new JwtTokenService(props);
    }

    @Test
    void constructorRejectsMissingSecret() {
        // 与 ERP_TOKEN_KEY 同规:无默认值,漏配在构造期即拦(而非 NPE 裸奔)
        assertThrows(IllegalStateException.class, () -> new JwtTokenService(new JwtProperties()));
    }

    @Test
    void constructorRejectsShortSecret() {
        JwtProperties weak = new JwtProperties();
        weak.setSecret("too-short");

        // HS256 要求 ≥32 字节,弱密钥带病上线在启动期即拦
        assertThrows(IllegalStateException.class, () -> new JwtTokenService(weak));
    }

    @Test
    void createThenParseRoundTripPreservesIdentityAndRoles() {
        LoginUser in = new LoginUser(7L, "chen", "陈", List.of("admin", "operator"));

        LoginUser out = service.parse(service.create(in));

        assertEquals(7L, out.userId());
        assertEquals("chen", out.username());
        assertEquals("陈", out.nickname());
        assertEquals(List.of("admin", "operator"), out.roleKeys());
    }

    @Test
    void parseRejectsForgedPayload() {
        String token = service.create(new LoginUser(7L, "chen", "陈", List.of("admin")));
        String[] parts = token.split("\\.");
        // 首字符替换后仍是合法 base64url(长度不变),必定走到验签失败
        char first = parts[1].charAt(0);
        parts[1] = (first == 'A' ? 'B' : 'A') + parts[1].substring(1);
        String forged = String.join(".", parts);

        assertThrows(JwtException.class, () -> service.parse(forged));
    }

    @Test
    void parseRejectsTokenWithHangingBase64Suffix() {
        String token = service.create(new LoginUser(7L, "chen", "陈", List.of("admin")));
        // 签名段长度随密钥长度自选算法(HS256=43/HS384=64/HS512=86 字符)而变,禁硬编码段长:
        // 追加到 %4==1 的悬挂单元长度,严格预校验直接拦下(JJWT 裸解码会静默丢弃导致篡改 token 仍过验签)
        String[] parts = token.split("\\.");
        int pad = (5 - parts[2].length() % 4) % 4;
        if (pad == 0) {
            pad = 4;
        }
        String tampered = token + "x".repeat(pad);

        assertThrows(IllegalArgumentException.class, () -> service.parse(tampered));
    }

    @Test
    void parseRejectsNonJwtString() {
        assertThrows(JwtException.class, () -> service.parse("not-a-jwt"));
    }
}
