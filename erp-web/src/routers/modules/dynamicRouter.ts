import router from '@/routers/index'
import { LOGIN_URL } from '@/config'
import type { RouteRecordRaw } from 'vue-router'
import { ElNotification } from 'element-plus'
import { useUserStore } from '@/stores/modules/user'
import { useAuthStore } from '@/stores/modules/auth'

// 引入 views 文件夹下所有 vue 文件
const modules = import.meta.glob('@/views/**/*.vue')

/**
 * @description 初始化动态路由
 * 数据源:GET /api/auth/me 的 menus 树(authStore.getAuthInfo 一次拉全)
 */
export const initDynamicRouter = async () => {
  const userStore = useUserStore()
  const authStore = useAuthStore()

  try {
    // 1.获取菜单列表 && 按钮权限列表(单接口装配)
    await authStore.getAuthInfo()

    // 2.判断当前用户有没有菜单权限
    if (!authStore.authMenuList.length) {
      ElNotification({
        title: '无权限访问',
        message: '当前账号无任何菜单权限，请联系系统管理员！',
        type: 'warning',
        duration: 3000
      })
      // 必须 clearUserInfo:setToken('') 会把 isLoggedIn 置 true,守卫会把 /login 弹回原页,与 401 拦截器互踢成死循环(docs/09 §3.5)
      userStore.clearUserInfo()
      return Promise.reject(router.replace(LOGIN_URL))
    }

    // 3.添加动态路由
    authStore.flatMenuListGet.forEach(item => {
      item.children && delete item.children
      if (item.component && typeof item.component == 'string') {
        // 后端 component 约定不带前导斜杠(system/user/index),容错剥掉再拼;Geeker 上游 mock 带斜杠,照抄其拼串会漏 '/' 致全体失配
        const componentPath = item.component.replace(/^\//, '')
        const module = modules[`/src/views/${componentPath}.vue`]
        // 守卫前置:菜单 component 无对应视图必须显式报错,禁静默白屏(docs/09 §7)
        if (!module) {
          console.error(`[动态路由] 菜单 component 无对应视图: ${item.component}(path=${item.path}, title=${item.meta.title})——请检查 sys_menu.component 与 src/views 目录是否一致`)
          return
        }
        item.component = module
      }
      if (item.meta.isFull) {
        router.addRoute(item as unknown as RouteRecordRaw)
      } else {
        router.addRoute('layout', item as unknown as RouteRecordRaw)
      }
    })
  } catch (error) {
    // 当按钮 || 菜单请求出错时，重定向到登陆页(必须 clearUserInfo,理由同上:setToken('') 毒化 isLoggedIn 导致 401 死循环)
    userStore.clearUserInfo()
    return Promise.reject(router.replace(LOGIN_URL))
  }
}
