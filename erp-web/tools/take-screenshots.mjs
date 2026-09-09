/**
 * README 前端页面截图脚本(开发辅助,非运行时依赖)
 * 用法:先启动后端(:8088)与前端 dev(:5173),然后在 erp-web/ 下执行:
 *   node tools/take-screenshots.mjs
 * 优先驱动系统已装的 Chrome/Edge,不依赖 playwright 下载的浏览器。
 * 输出:docs/images/*.png(项目根)
 */
import { chromium } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT_DIR = join(__dirname, '..', '..', 'docs', 'images')
const BASE = 'http://localhost:5173/#' // hash 路由

mkdirSync(OUT_DIR, { recursive: true })

// 优先驱动系统已安装的 Chrome/Edge(channel 模式,无需下载 playwright 浏览器)
async function launchBrowser() {
  for (const channel of ['chrome', 'msedge']) {
    try {
      return await chromium.launch({ channel, headless: true })
    } catch (e) {
      console.log(`channel ${channel} unavailable:`, String(e).slice(0, 80))
    }
  }
  return chromium.launch({ headless: true }) // 最后兜底:playwright 自带 chromium
}

const browser = await launchBrowser()
const page = await browser.newPage({ viewport: { width: 1600, height: 900 } })

async function shot(name) {
  await page.waitForTimeout(1200) // 等渲染/数据
  await page.screenshot({ path: join(OUT_DIR, name), type: 'png' })
  console.log('saved:', name)
}

// 1. 登录页
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await shot('login.png')

// 2. 登录
await page.fill('input[placeholder="用户名"]', 'admin')
await page.fill('input[placeholder="密码"]', 'admin@123')
await page.click('button:has-text("登录")')
await page.waitForURL(/home/, { timeout: 15000 })
await page.waitForTimeout(5500) // 等欢迎 toast 自动消失
await shot('home.png')

// 3. 遍历候选页面(真实动态菜单路由,见 /api/auth/me menus);打不开(404/空白)的跳过
const candidates = [
  ['/order/list', 'order-list.png'],
  ['/inventory/inventories', 'inventory.png'],
  ['/goods/product', 'goods.png'],
  ['/ai/chat', 'ai-chat.png'],
  ['/ai/agent', 'ai-agent.png'],
]
for (const [path, file] of candidates) {
  try {
    await page.goto(`${BASE}${path}`, { waitUntil: 'networkidle', timeout: 15000 })
    await page.waitForTimeout(2000) // 等动态视图编译/数据渲染
    await shot(file)
  } catch (e) {
    console.log('skip(error):', path, String(e).slice(0, 80))
  }
}

await browser.close()
console.log('done ->', OUT_DIR)
