package com.own.erp.shop.security;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : CryptoService 单测(AIR:自动化/独立/可重复)。
 *     用固定 32 字节密钥直接构造,不起 Spring、不依赖环境变量与随机性
 */
class CryptoServiceTest {

    /** 测试专用固定密钥(仅测试夹具,非生产密钥),保证用例可重复 */
    private static final byte[] KEY_BYTES = new byte[32];
    private static final String KEY_BASE64 = Base64.getEncoder().encodeToString(KEY_BYTES);

    private static CryptoService cryptoService;

    @BeforeAll
    static void setUp() {
        for (int i = 0; i < KEY_BYTES.length; i++) {
            KEY_BYTES[i] = (byte) (i * 7 + 1);
        }
        cryptoService = new CryptoService(KEY_BASE64);
    }

    @Test
    void encryptDecryptRoundtrip() {
        String plain = "tb_access_token_1234567890";
        String cipher = cryptoService.encrypt(plain);
        assertNotEquals(plain, cipher);
        assertEquals(plain, cryptoService.decrypt(cipher));
    }

    /** GCM 随机 IV:同一明文两次密文不同,但各自都能解回 */
    @Test
    void encryptSamePlainTwiceProducesDifferentCipher() {
        String plain = "same-plain-token";
        String cipher1 = cryptoService.encrypt(plain);
        String cipher2 = cryptoService.encrypt(plain);
        assertNotEquals(cipher1, cipher2);
        assertEquals(plain, cryptoService.decrypt(cipher1));
        assertEquals(plain, cryptoService.decrypt(cipher2));
    }

    /** 篡改密文任意字节 → tag 校验失败 */
    @Test
    void decryptTamperedCipherFails() {
        String cipher = cryptoService.encrypt("tamper-target-token");
        byte[] bytes = Base64.getDecoder().decode(cipher);
        bytes[bytes.length - 1] ^= 0x01;
        assertThrows(CryptoException.class,
                () -> cryptoService.decrypt(Base64.getEncoder().encodeToString(bytes)));
    }

    /** 截断密文 → 长度/解析失败 */
    @Test
    void decryptTruncatedCipherFails() {
        String cipher = cryptoService.encrypt("truncate-target-token");
        String truncated = cipher.substring(0, cipher.length() - 8);
        assertThrows(CryptoException.class, () -> cryptoService.decrypt(truncated));
    }

    /** 非 base64 输入 → 解密失败且消息不含输入内容 */
    @Test
    void decryptIllegalBase64FailsWithoutLeakingInput() {
        String dirty = "这不是!!!base64@@@";
        CryptoException e = assertThrows(CryptoException.class, () -> cryptoService.decrypt(dirty));
        assertFalse(e.getMessage().contains(dirty));
    }

    /** 换密钥实例解密 → tag 校验失败(密钥不匹配场景) */
    @Test
    void decryptWithDifferentKeyFails() {
        byte[] other = new byte[32];
        for (int i = 0; i < other.length; i++) {
            other[i] = (byte) (i * 3 + 5);
        }
        CryptoService otherService = new CryptoService(Base64.getEncoder().encodeToString(other));
        String cipher = cryptoService.encrypt("key-mismatch-token");
        assertThrows(CryptoException.class, () -> otherService.decrypt(cipher));
    }

    /** null/空串透传返回原值;纯空格照常加密(blank 拦截是 ShopService 的职责) */
    @Test
    void nullAndEmptyPassThrough() {
        assertNull(cryptoService.encrypt(null));
        assertEquals("", cryptoService.encrypt(""));
        assertNull(cryptoService.decrypt(null));
        assertEquals("", cryptoService.decrypt(""));
        assertEquals(" ", cryptoService.decrypt(cryptoService.encrypt(" ")));
    }

    /** 密钥非法:null / 非 base64 / 长度不足 32 字节,启动即失败且消息含生成命令 */
    @Test
    void invalidKeyFailsFastWithGenerationHint() {
        assertThrows(IllegalStateException.class, () -> new CryptoService(null));
        assertThrows(IllegalStateException.class, () -> new CryptoService("   "));
        assertThrows(IllegalStateException.class, () -> new CryptoService("not-base64@@@"));
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new CryptoService(shortKey));
        assertTrue(e.getMessage().contains("openssl"));
    }

    /**
     * 列宽红线:access_token/refresh_token 列 VARCHAR(2048),
     * 密文 = 明文+28 字节再 base64,故明文上限约 1508 字节;用上限长度验证 roundtrip 与列宽
     */
    @Test
    void maxPlainLengthFitsDbColumn() {
        byte[] plainBytes = new byte[1508];
        for (int i = 0; i < plainBytes.length; i++) {
            plainBytes[i] = (byte) ('a' + i % 26);
        }
        String plain = new String(plainBytes, StandardCharsets.US_ASCII);
        String cipher = cryptoService.encrypt(plain);
        assertTrue(cipher.length() <= 2048, "密文长度 " + cipher.length() + " 超出 VARCHAR(2048)");
        assertArrayEquals(plainBytes, cryptoService.decrypt(cipher).getBytes(StandardCharsets.US_ASCII));
    }
}
