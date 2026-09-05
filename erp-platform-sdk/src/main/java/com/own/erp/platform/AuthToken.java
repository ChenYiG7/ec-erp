package com.own.erp.platform;

import lombok.Data;

import java.time.Instant;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 平台授权凭证。落库前必须加密存储(参考 docs/04-平台对接层设计.md)。
 */
@Data
public class AuthToken {

    private String accessToken;
    private String refreshToken;
    /** accessToken 过期时间 */
    private Instant expireAt;
}
