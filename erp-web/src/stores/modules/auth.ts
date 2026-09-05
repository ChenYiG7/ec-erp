import { defineStore } from 'pinia'
import type { AuthState } from '@/stores/interface/store'
import type { LoginResponse, MenuOptions, SysMenuResponse } from '@/api/interface'
import { AuthApi } from '@/api/apis/system/auth'
import { getFlatMenuList, getShowMenuList, getAllBreadcrumbList } from '@/utils'
import { useUserStore } from '@/stores/modules/user'
import { computed, reactive, toRefs } from 'vue'

/**
 * component 路径 -> 路由 name(system/user/index -> system-user-index)
 * 与生成页面 defineOptions(name) 保持一致,是 KeepAlive 生效的前提
 */
export const routeNameFromComponent = (component: string) => component.replace(/\//g, '-')

/** 路径 -> 路由 name(目录节点无 component 时用 path 派生) */
const routeNameFromPath = (path: string) => path.replace(/^\//, '').replace(/\//g, '-')

/**
 * 后端 SysMenuResponse 树 -> Geeker MenuOptions 树
 * - menuType=1 目录:无 component,容器节点
 * - menuType=2 菜单:component 字符串,KeepAlive 默认开启
 * - menuType=3 按钮:不进路由(permKey 已在后端扁平化为 LoginResponse.perms)
 */
const transformMenus = (menus: SysMenuResponse[]): MenuOptions[] => {
  return menus
    .filter(item => item.menuType !== 3 && item.status === 1)
    .sort((a, b) => a.sort - b.sort)
    .map(item => {
      const children = item.children?.length ? transformMenus(item.children) : undefined
      const name = item.component ? routeNameFromComponent(item.component) : routeNameFromPath(item.path || `menu-${item.id}`)
      return {
        path: item.path || '',
        name,
        ...(item.component ? { component: item.component } : {}),
        meta: {
          icon: item.icon || 'Menu',
          title: item.menuName,
          isLink: null,
          isHide: item.visible === 0,
          isFull: false,
          isAffix: false,
          isKeepAlive: true
        },
        ...(children?.length ? { children } : {})
      }
    })
}

export const useAuthStore = defineStore('erp-auth', () => {
  const state = reactive<AuthState>({
    // 按钮权限标识集合(后端 permKey 全局唯一)
    perms: [],
    // 菜单权限列表
    authMenuList: [],
    // 当前页面的 router name，用来做按钮权限筛选
    routeName: ''
  })

  const showMenuListGet = computed(() => getShowMenuList(state.authMenuList))
  const flatMenuListGet = computed(() => getFlatMenuList(state.authMenuList))
  const breadcrumbListGet = computed(() => getAllBreadcrumbList(state.authMenuList))
  const authMenuListGet = computed(() => state.authMenuList)

  const actions = {
    /**
     * GET /api/auth/me 一次拉全:用户信息 + 菜单树 + 按钮权限
     * 登录后与刷新页面均经 initDynamicRouter 走这里(动态路由权威数据源)
     */
    async getAuthInfo() {
      const data: LoginResponse = await AuthApi.me()
      const userStore = useUserStore()
      userStore.setUserInfo({
        userId: data.userId,
        username: data.username,
        name: data.nickname || data.username,
        roles: data.roles,
        isLoggedIn: true
      })
      state.perms = data.perms || []
      state.authMenuList = transformMenus(data.menus || [])
    },
    async setRouteName(name: string) {
      state.routeName = name
    },
    /**
     * 清空菜单/按钮权限状态。必须与 resetRouter 同步调用:只删路由不清这里,
     * 守卫看到 authMenuList 非空会跳过 initDynamicRouter,重登后动态路由永不注册(全站菜单页 404)
     */
    async clearAuth() {
      state.perms = []
      state.authMenuList = []
      state.routeName = ''
    }
  }

  return {
    ...toRefs(state),
    showMenuListGet,
    flatMenuListGet,
    breadcrumbListGet,
    authMenuListGet,
    ...actions
  }
})
