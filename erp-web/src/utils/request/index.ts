import { RequestHttp } from './base'
import { ResultEnum } from '@/enums/httpEnum'

const config = {
  // 接口基础地址:留空 = 同源(dev 走 vite proxy /api -> 8088;prod 同域反代)
  // api 函数路径一律写后端全路径(以 /api 开头,对齐后端 Controller)——docs/09 §3
  baseURL: import.meta.env.VITE_API_URL,
  timeout: ResultEnum.TIMEOUT as number,
  // JWT 走 Authorization 头,无需携带 cookie 凭证
  withCredentials: false
}

export default new RequestHttp(config)
