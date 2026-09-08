import http from '@/utils/request'
import type { LoginResponse } from '@/api/interface'

/** 登录入参(后端校验明文密码 + BCrypt,前端禁再做摘要/加盐) */
export interface ReqLoginForm {
  username: string
  password: string
}

/**
 * 认证接口(/api/auth)
 * JWT 24h 无 refresh token:401 即重登,禁造续期逻辑(docs/09 §7)
 */
export const AuthApi = {
  login: (params: ReqLoginForm) => http.post<LoginResponse>('/api/auth/login', params),
  /** 当前登录用户信息 + 菜单树 + 按钮权限(动态路由权威数据源) */
  me: () => http.get<LoginResponse>('/api/auth/me')
}
