package com.own.erp.shop.security;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 平台凭证加解密(TODO#2,docs/07 §7 红线):
 *     - 算法 AES-256-GCM,每次加密随机 12 字节 IV,输出 Base64(IV ‖ 密文+tag),tag 128 bit;
 *     - 密钥只从环境变量 ERP_TOKEN_KEY 读(32 字节标准 base64),无任何默认值——缺失/非法启动即失败,
 *       这是有意行为;禁入代码/配置文件,生成方式 openssl rand -base64 32;
 *     - null/空串原样透传(可选凭证字段不参与加密),纯空格会照常加密,blank 拦截由 ShopService 负责;
 *     - 日志/异常消息/返回体禁出现明文或密文,本类消息固定不携带任何输入内容
 */
@Service
public class CryptoService {

    private static final String ENV_KEY = "ERP_TOKEN_KEY";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Spring 使用:从环境变量读密钥。显式 @Autowired 标注无参构造,
     * 避免依赖"多构造器时回退无参"的版本相关隐式行为
     */
    @Autowired
    public CryptoService() {
        this(System.getenv(ENV_KEY));
    }

    /**
     * 显式密钥构造:仅供单元测试注入固定密钥(生产装配走 @Autowired 无参构造,密钥只来自环境变量)。
     * 密钥校验只有本构造这一份,无参构造纯委托
     */
    public CryptoService(String base64Key) {
        if (StrUtil.isBlank(base64Key)) {
            throw new IllegalStateException("环境变量 " + ENV_KEY + " 未设置:平台凭证加密必需,生成方式 openssl rand -base64 32");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(ENV_KEY + " 不是合法的标准 base64(base64url 不行),生成方式 openssl rand -base64 32");
        }
        if (raw.length != 32) {
            throw new IllegalStateException(ENV_KEY + " 解码后须为 32 字节(AES-256),当前 "
                    + raw.length + " 字节;生成方式 openssl rand -base64 32");
        }
        this.secretKey = new SecretKeySpec(raw, ALGORITHM);
    }

    /** 明文 → Base64(IV ‖ 密文+tag);null/空串原样返回。同一明文每次密文不同(随机 IV) */
    public String encrypt(String plain) {
        if (StrUtil.isEmpty(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv).put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new CryptoException("AES-GCM 加密失败", e);
        }
    }

    /** Base64(IV ‖ 密文+tag) → 明文;null/空串原样返回。tag 校验失败/格式非法统一抛 CryptoException(消息不含输入内容) */
    public String decrypt(String cipherText) {
        if (StrUtil.isEmpty(cipherText)) {
            return cipherText;
        }
        try {
            byte[] all = Base64.getDecoder().decode(cipherText);
            if (all.length <= IV_LENGTH + TAG_BITS / 8) {
                throw new CryptoException("AES-GCM 解密失败:密文长度非法", null);
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey,
                    new GCMParameterSpec(TAG_BITS, all, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (CryptoException e) {
            throw e;
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new CryptoException("AES-GCM 解密失败", e);
        }
    }
}
