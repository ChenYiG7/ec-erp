import { computed } from 'vue'
import { useAuthStore } from '@/stores/modules/auth'

/**
 * @description 页面按钮权限(值 = 后端 permKey,如 system:user:add)
 * perms 为空 = 引导期放行,约定见 v-auth 指令与 docs/09 §7
 * */
export const useAuthButtons = () => {
  const authStore = useAuthStore()
  const perms = authStore.perms

  const BUTTONS = computed(() => {
    const currentPageAuthButton: { [key: string]: boolean } = {}
    if (!perms.length) {
      // 引导期:无任何权限种子,视为全放行
      currentPageAuthButton['*'] = true
      return currentPageAuthButton
    }
    perms.forEach(item => (currentPageAuthButton[item] = true))
    return currentPageAuthButton
  })

  return {
    BUTTONS
  }
}
