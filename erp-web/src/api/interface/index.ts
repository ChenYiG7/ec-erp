/**
 * 全局 API 类型中枢(对齐后端契约,禁前端自造重复类型)
 *
 * 后端契约事实(docs/09 §3):
 * - 统一返回体 Result{code, msg, data},成功 code=200;http 层已解包,业务代码只见 data
 * - 分页直接用 MyBatis-Plus Page 序列化形态;入参 pageNo/pageSize
 * - 时间字符串 yyyy-MM-dd HH:mm:ss(GMT+8);金额 DECIMAL(12,4) 按 string 处理禁浮点运算
 */

/** 后端统一返回体(http 拦截器已解包,仅在拦截器内使用) */
export interface Result<T = unknown> {
  code: number
  msg: string
  data: T
}

/** MyBatis-Plus Page 序列化形态 */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

/** 分页入参(对齐后端 PageQuery:pageNo 默认 1 / pageSize 默认 20) */
export interface PageQuery {
  pageNo?: number
  pageSize?: number
}

/**
 * 动态路由菜单模型(Geeker 布局/菜单工具依赖的形态,由后端 SysMenuResponse 树转换而来)
 * name 由 component 路径派生(system/user/index -> system-user-index),保证 KeepAlive 组件名一致
 */
export interface MenuOptions {
  path: string
  name: string
  component?: string | (() => Promise<unknown>)
  redirect?: string
  meta: {
    icon: string
    title: string
    /** 搜索菜单面板展示标题(缺省回落 title) */
    customTitle?: string
    isLink: string | null
    isHide: boolean
    isFull: boolean
    isAffix: boolean
    isKeepAlive: boolean
    activeMenu?: string
  }
  children?: MenuOptions[]
}

/** 后端 sys_menu 菜单树节点(GET /api/auth/me -> menus) */
export interface SysMenuResponse {
  id: number
  parentId: number
  menuName: string
  /** 1目录 2菜单 3按钮 */
  menuType: number
  /** 按钮/接口权限标识,如 system:user:add */
  permKey: string | null
  path: string | null
  /** 相对 src/views 的组件路径,如 system/user/index */
  component: string | null
  icon: string | null
  sort: number
  /** 1显示 0隐藏 */
  visible: number
  /** 1启用 0停用 */
  status: number
  children: SysMenuResponse[]
}

/** 登录 / GET /api/auth/me 响应(JWT 24h,无 refresh token,401 即重登) */
export interface LoginResponse {
  /** 仅 login 接口返回 */
  token?: string
  userId: number
  username: string
  nickname: string
  roles: string[]
  /** 按钮权限标识集合(menuType=3 的 permKey 汇总) */
  perms: string[]
  menus: SysMenuResponse[]
}

/** 字典项(后端 dict 域 entity 直连豁免) */
export interface DictItem {
  id: number
  dictType: string
  dictLabel: string
  dictValue: string
  sort: number
  status: number
  remark: string | null
}

/** 站内通知(GET /api/system/notifications) */
export interface SysNotificationResponse {
  id: number
  userId: number
  title: string
  content: string
  /** PULL_FAIL=拉单连续失败告警 */
  notifyType: string
  bizType: string | null
  bizId: number | null
  /** 0未读 1已读 */
  readStatus: number
  readAt: string | null
  createdAt: string
  updatedAt: string
}
