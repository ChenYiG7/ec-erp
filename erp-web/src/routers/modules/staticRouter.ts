import type { RouteRecordRaw } from 'vue-router'
import { HOME_URL, LOGIN_URL } from '@/config'

/**
 * staticRouter (静态路由)
 */
export const staticRouter: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: HOME_URL,
  },
  {
    path: LOGIN_URL,
    name: 'login',
    component: () => import('@/views/login/index.vue'),
    meta: {
      title: '登录',
    },
  },
  {
    path: '/layout',
    name: 'layout',
    component: () => import('@/layouts/index.vue'),
    // component: () => import("@/layouts/indexAsync.vue"),
    redirect: HOME_URL,
    children: [
      {
        // 首页固定工作台,不属于 sys_menu 菜单(后端菜单种子不含 home)
        path: HOME_URL,
        name: 'home',
        component: () => import('@/views/home/index.vue'),
        meta: {
          icon: 'HomeFilled',
          title: '首页',
          isLink: null,
          isHide: true,
          isFull: false,
          isAffix: true,
          isKeepAlive: false,
        },
      },
    ],
  },
]

/**
 * errorRouter (错误页面路由)
 */
export const errorRouter = [
  {
    path: '/403',
    name: '403',
    component: () => import('@/components/ErrorMessage/Error403.vue'),
    meta: {
      title: '403页面',
    },
  },
  {
    path: '/404',
    name: '404',
    component: () => import('@/components/ErrorMessage/Error404.vue'),
    meta: {
      title: '404页面',
    },
  },
  {
    path: '/500',
    name: '500',
    component: () => import('@/components/ErrorMessage/Error500.vue'),
    meta: {
      title: '500页面',
    },
  },
  // Resolve refresh page, route warnings
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/components/ErrorMessage/Error404.vue'),
  },
]
