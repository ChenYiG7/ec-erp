package com.own.erp.system.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : JWT 签发/校验(HS256,TODO#1)。
 *     载荷只放身份与角色标识,禁放敏感信息(密码/平台凭证);perm_key 细粒度权限不入 token,
 *     改权限后无需等 token 过期,需要按钮级鉴权时按 userId 实时查(sys_user_role → sys_role_menu → sys_menu)
 */
@Service
public class JwtTokenService {

    private static final String CLAIM_UID = "uid";
    private static final String CLAIM_NICKNAME = "nickname";
    private static final String CLAIM_ROLES = "roles";

    private final SecretKey signKey;
    private final long expireHours;

    public JwtTokenService(JwtProperties props) {
        String secret = props.getSecret();
        // 与 CryptoService(ERP_TOKEN_KEY)同规:未设置/空白启动即失败,漏配不 NPE 裸奔
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("erp.jwt.secret 未设置:请通过环境变量 ERP_JWT_SECRET 注入(≥32 字节)");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        // HS256 要求密钥 ≥ 256 位;长度不足直接启动失败,防弱密钥带病上线
        if (secretBytes.length < 32) {
            throw new IllegalStateException("erp.jwt.secret 长度不足:HS256 要求至少 32 字节,当前 "
                    + secretBytes.length + " 字节,请通过环境变量 ERP_JWT_SECRET 注入");
        }
        this.signKey = Keys.hmacShaKeyFor(secretBytes);
        this.expireHours = props.getExpireHours();
    }

    /** 签发 token,有效期 erp.jwt.expire-hours */
    public String create(LoginUser user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.username())
                .claim(CLAIM_UID, user.userId())
                .claim(CLAIM_NICKNAME, user.nickname())
                .claim(CLAIM_ROLES, user.roleKeys())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireHours * 3600_000L))
                .signWith(signKey)
                .compact();
    }

    /**
     * 校验并解析 token;过期/被篡改/格式非法抛 JwtException 或 IllegalArgumentException,
     * 由 JwtAuthenticationFilter 统一按未登录处理(401)
     */
    public LoginUser parse(String token) {
        // 严格预校验报文形状:JJWT 内部用流式 Base64 解码,会静默丢弃末尾不完整的 4 字符组,
        // 导致"合法 token + 尾部追加 1 个字符"仍能通过验签;这里用严格解码器把悬挂字符挡在门外
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new MalformedJwtException("token 段数非法,期望 3 段,实际 " + parts.length);
        }
        for (String part : parts) {
            // Base64url 无填充合法长度 %4 ∈ {0,2,3};%4==1 为悬挂单元,严格解码器直接抛 IllegalArgumentException
            Base64.getUrlDecoder().decode(part);
        }
        Claims claims = Jwts.parser().verifyWith(signKey).build()
                .parseSignedClaims(token).getPayload();
        List<String> roleKeys = new ArrayList<>();
        Object roles = claims.get(CLAIM_ROLES);
        if (roles instanceof List<?> list) {
            for (Object item : list) {
                roleKeys.add(String.valueOf(item));
            }
        }
        return new LoginUser(claims.get(CLAIM_UID, Long.class),
                claims.getSubject(), claims.get(CLAIM_NICKNAME, String.class), roleKeys);
    }
}
