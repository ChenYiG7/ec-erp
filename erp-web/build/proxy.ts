import type { ProxyOptions } from 'vite'

type ProxyItem = [string, string]

type ProxyList = ProxyItem[]

type ProxyTargetList = Record<string, ProxyOptions>

/**
 * 创建代理，用于解析 .env.development 代理配置
 *
 * ⚠️ 后端真实路径以 /api 开头(无 context-path),代理【禁 rewrite】,
 * 必须原样透传前缀;api 函数路径一律写后端全路径(以 /api 开头)。docs/09 §3
 * @param list
 */
export function createProxy(list: ProxyList = []) {
  const ret: ProxyTargetList = {}
  for (const [prefix, target] of list) {
    const isHttps = target.startsWith('https://')

    // https://github.com/http-party/node-http-proxy#options
    ret[prefix] = {
      target: target,
      changeOrigin: true,
      ws: true,
      // 禁 rewrite:后端真实路径含 /api 前缀,原样透传
      rewrite: path => path,
      // https is require secure=false
      ...(isHttps ? { secure: false } : {}),
    }
  }
  return ret
}
