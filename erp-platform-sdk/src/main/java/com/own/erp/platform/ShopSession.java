package com.own.erp.platform;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 一次平台调用所需的会话上下文:店铺标识 + 当前有效 Token。
 *     由 erp-shop 的授权中心负责构建与刷新,adapter 内不做 Token 续期。
 */
@Data
@AllArgsConstructor
public class ShopSession {

    private Long shopId;
    private PlatformType platform;
    private AuthToken token;
}
