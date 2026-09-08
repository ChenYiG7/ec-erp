/**
 * v-auth
 * 按钮权限指令(值 = 后端 permKey,如 system:user:add)
 * 约定:perms 集合为空 = 引导期(尚无 menuType=3 种子),放行;非空则严格判断(docs/09 §7)
 * 仅做展示层裁剪,真正的越权拦截在后端 @PreAuthorize
 */
import { useAuthStore } from '@/stores/modules/auth'
import type { Directive, DirectiveBinding } from 'vue'

const auth: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const { value } = binding
    const authStore = useAuthStore()
    const perms = authStore.perms
    if (!perms.length) {
      return
    }
    if (value instanceof Array && value.length) {
      const hasPermission = value.every(item => perms.includes(item))
      if (!hasPermission) {
        el.remove()
      }
    } else {
      if (!perms.includes(value)) {
        el.remove()
      }
    }
  },
}

export default auth
