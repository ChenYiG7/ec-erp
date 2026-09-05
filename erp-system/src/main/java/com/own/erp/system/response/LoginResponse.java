package com.own.erp.system.response;

import lombok.Builder;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 登录/当前用户信息返回体(TODO#1)
 */
@Builder
public record LoginResponse(

        /** JWT,仅登录接口返回;/me 为 null */
        String token,

        Long userId,

        String username,

        String nickname,

        /** 角色标识列表(role_key),前端按此控制入口显隐 */
        List<String> roles,

        /** 权限标识去重集合(perm_key),预留按钮级鉴权 */
        List<String> perms,

        /** 可见菜单树(仅启用节点,response 树形,entity 不外泄) */
        List<SysMenuResponse> menus
) {

    /** 携带 JWT 的副本(wither,docs/07 §1):登录接口签发 token 后补, /me 不调 */
    public LoginResponse withToken(String token) {
        return new LoginResponse(token, userId, username, nickname, roles, perms, menus);
    }
}
