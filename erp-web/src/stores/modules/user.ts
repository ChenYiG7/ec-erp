import { defineStore } from 'pinia'
import type { UserInfo } from '@/stores/interface/store'
import piniaPersistConfig from '@/stores/helper/persist'
import { useStorage } from '@vueuse/core'

const STORE_NAME = 'erp-user'
export const useUserStore = defineStore(
  STORE_NAME,
  () => {
    // 后端 JWT 24h 无 refresh token:单 token,401 即重登(docs/09 §7)
    const accessToken = useStorage('accessToken', '')
    const userInfo = useStorage<UserInfo>('userInfo', {} as UserInfo)

    const setToken = (token: string) => {
      accessToken.value = token
      userInfo.value.isLoggedIn = true
    }

    const clearUserInfo = () => {
      userInfo.value = {} as UserInfo
      accessToken.value = ''
    }

    const setUserInfo = (info: UserInfo) => {
      userInfo.value = info
    }

    const getUserToken = () => {
      return accessToken.value
    }

    return {
      userInfo,
      accessToken,
      setToken,
      setUserInfo,
      clearUserInfo,
      getUserToken,
    }
  },
  {
    persist: piniaPersistConfig(STORE_NAME),
  }
)
