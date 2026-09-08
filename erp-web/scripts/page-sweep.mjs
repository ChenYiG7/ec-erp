/* eslint-disable */
// 快速全量 sweep(复现第一轮) + 每站诊断 + pageerror 捕获
import { chromium } from '@playwright/test'

const BASE = 'http://localhost:5173'
const ROUTES = [
  '/system/users',
  '/system/roles',
  '/system/menus',
  '/system/dicts',
  '/system/configs',
  '/shop',
  '/goods/product',
  '/goods/shop-products',
  '/goods/shop-product-skus',
  '/goods/categories',
  '/goods/brands',
  '/order/list',
  '/fulfill/delivery-orders',
  '/aftersale/orders',
  '/system/pull-logs',
  '/purchase/suppliers',
  '/purchase/orders',
  '/purchase/inbounds',
  '/inventory/inventories',
  '/inventory/flows',
  '/inventory/warehouses',
  '/system/notifications',
  '/ai/chat',
  '/ai/suggestions',
  '/ai/agent',
]

const browser = await chromium.launch({ channel: 'msedge' })
const page = await browser.newPage()
const errors = []
page.on('pageerror', e => errors.push(`[pageerror] ${String(e).slice(0, 400)}`))
page.on('console', m => {
  if (m.type() === 'error') errors.push(`[console.error] ${m.text().slice(0, 400)}`)
})
page.on('request', r => {
  if (r.url().includes('/api/') && r.method() !== 'OPTIONS') errors.push(`[api] ${r.url().replace(BASE, '')}`)
})

await page.goto(`${BASE}/#/login`)
await page.waitForTimeout(1500)
await page.locator('input:not([type="checkbox"]):not([type="password"])').first().fill('admin')
await page.locator('input[type="password"]').fill('admin@123')
await page.locator('button').filter({ hasText: '登录' }).click()
await page.waitForTimeout(2500)
errors.length = 0 // 登录期的 api 记录清掉

let firstBlank = ''
for (let i = 0; i < ROUTES.length; i++) {
  await page.goto(`${BASE}/#${ROUTES[i]}`)
  await page.waitForTimeout(1800)
  const d = await page.evaluate(() => {
    const main = document.querySelector('.el-main')
    return {
      children: main ? main.children.length : -1,
      text: main ? main.innerText.trim().length : -1,
      leaving: document.querySelectorAll('.fade-transform-leave-active').length,
      entering: document.querySelectorAll('.fade-transform-enter-active').length,
      html: main ? main.innerHTML.slice(0, 120) : 'NO_MAIN',
    }
  })
  const blank = d.children === 0 || d.text < 5
  if (blank && !firstBlank) firstBlank = ROUTES[i]
  console.log(
    `${blank ? 'BLANK' : 'ok   '} ${ROUTES[i]} ch=${d.children} text=${d.text} lv=${d.leaving} en=${d.entering}${i > 0 && blank && firstBlank !== ROUTES[i] ? ' (cascade)' : ''}`
  )
  if (blank && d.html) console.log(`      html: ${d.html}`)
}

console.log('\n--- first blank at:', firstBlank || '(not reproduced)')
console.log('--- pageerror/console.error during sweep (after login) ---')
const errs = errors.filter(e => !e.startsWith('[api]'))
errs.slice(0, 15).forEach(e => console.log(' ', e))
if (!errs.length) console.log('  (none)')

// 空白级联后,F5 是否恢复
if (firstBlank) {
  await page.reload()
  await page.waitForTimeout(2500)
  const d = await page.evaluate(() => {
    const main = document.querySelector('.el-main')
    return { children: main ? main.children.length : -1, text: main ? main.innerText.trim().length : -1 }
  })
  console.log(
    `--- after F5 on last route: children=${d.children} text=${d.text} ${d.text < 5 ? 'STILL BLANK' : 'RECOVERED'}`
  )
}

await browser.close()
