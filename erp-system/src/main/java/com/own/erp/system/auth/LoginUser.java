package com.own.erp.system.auth;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 登录用户上下文对象:JWT 载荷的内存形态。
 *     JwtAuthenticationFilter 校验通过后写入 SecurityContext,业务侧经 {@link AuthContext#current()} 取用
 */
public record LoginUser(Long userId, String username, String nickname, List<String> roleKeys) {
}
