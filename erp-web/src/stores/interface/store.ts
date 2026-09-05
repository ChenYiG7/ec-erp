import type { MenuOptions } from '@/api/interface'

export type LayoutType = 'vertical' | 'classic' | 'transverse' | 'columns'

export type AssemblySizeType = 'large' | 'default' | 'small'

export type LanguageType = 'zh' | 'en' | null

/* GlobalState */
export interface GlobalState {
  layout: LayoutType
  assemblySize: AssemblySizeType
  language: LanguageType
  maximize: boolean
  primary: string
  isDark: boolean
  isGrey: boolean
  isWeak: boolean
  asideInverted: boolean
  headerInverted: boolean
  isCollapse: boolean
  accordion: boolean
  watermark: boolean
  breadcrumb: boolean
  breadcrumbIcon: boolean
  tabs: boolean
  tabsIcon: boolean
  footer: boolean
}

/* UserState */
export interface UserInfo {
  userId?: number
  username?: string
  /** 展示名(后端 nickname) */
  name: string
  roles?: string[]
  isLoggedIn: boolean
}

/* tabsMenuProps */
export interface TabsMenuProps {
  icon: string
  title: string
  path: string
  name: string
  close: boolean
  isKeepAlive: boolean
}

/* TabsState */
export interface TabsState {
  tabsMenuList: TabsMenuProps[]
}

/* AuthState */
export interface AuthState {
  routeName: string
  /**
   * 按钮权限标识集合(后端 permKey 全局唯一,如 system:user:add)
   * 约定:集合为空 = 引导期(尚无 menuType=3 种子),v-auth 放行;非空则严格判断(docs/09 §7)
   */
  perms: string[]
  authMenuList: MenuOptions[]
}

/* KeepAliveState */
export interface KeepAliveState {
  keepAliveName: string[]
}
