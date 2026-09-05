package com.own.erp.system.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : JWT 配置(erp.jwt.*)。
 *     密钥从环境变量 ERP_JWT_SECRET 注入,与 ERP_TOKEN_KEY 同规:无默认值,不设置启动即失败
 *     (JwtTokenService 构造期校验非空且 ≥32 字节),生产禁入代码/配置文件明文
 */
@Data
@Component
@ConfigurationProperties(prefix = "erp.jwt")
public class JwtProperties {

    /** HS256 签名密钥,至少 32 字节;环境变量 ERP_JWT_SECRET 注入,无默认值(不设置启动即失败) */
    private String secret;

    /** token 有效期(小时) */
    private long expireHours = 24;

    /** 请求头名称 */
    private String header = "Authorization";

    /** token 前缀(含空格) */
    private String bearerPrefix = "Bearer ";
}
