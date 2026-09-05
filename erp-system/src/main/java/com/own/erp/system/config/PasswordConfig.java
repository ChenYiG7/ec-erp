package com.own.erp.system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 密码编码器(TODO#1)。
 *     只依赖 spring-security-crypto,不引 Security starter(过滤链归 erp-api);
 *     BCrypt 自带随机盐,同一明文每次哈希不同,校验用 matches 而非再编码比对
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
