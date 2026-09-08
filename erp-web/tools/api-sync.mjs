#!/usr/bin/env node
/**
 * api:sync:抓后端 springdoc /v3/api-docs → tools/openapi.json 快照(前端契约唯一查询源)
 *
 * - 稳定序列化(2 空格缩进 + 尾换行),入库可 diff
 * - 守卫:连不上/非 JSON 报错退出并提示先起后端,禁静默保留旧快照
 * - 可用 API_DOC_URL 覆盖目标(默认 http://localhost:8088/v3/api-docs)
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const TOOLS_DIR = path.dirname(fileURLToPath(import.meta.url))
const OUT = path.join(TOOLS_DIR, 'openapi.json')
const URL_ = process.env.API_DOC_URL || 'http://localhost:8088/v3/api-docs'

async function main() {
  let raw
  try {
    const res = await fetch(URL_, { headers: { Accept: 'application/json' } })
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }
    raw = await res.text()
  } catch (e) {
    console.error(`[api:sync] 拉取失败(${URL_}): ${e.message}`)
    console.error('[api:sync] 请先起后端(erp-api :8088)再重跑 pnpm api:sync')
    process.exit(1)
  }
  let doc
  try {
    doc = JSON.parse(raw)
  } catch {
    console.error('[api:sync] 响应不是合法 JSON,确认地址是 springdoc /v3/api-docs')
    process.exit(1)
  }
  if (!doc.openapi || !doc.paths) {
    console.error('[api:sync] 文档缺 openapi/paths 字段,疑似网关错误页,中止')
    process.exit(1)
  }
  const json = JSON.stringify(doc, null, 2) + '\n'

  if (!fs.existsSync(OUT)) {
    fs.writeFileSync(OUT, json, 'utf8')
    console.log(`[api:sync] 快照已建立(${Object.keys(doc.paths).length} 个路径) -> tools/openapi.json`)
    return
  }
  const prev = fs.readFileSync(OUT, 'utf8')
  if (prev === json) {
    console.log('[api:sync] 快照无变化')
    return
  }
  fs.writeFileSync(OUT, json, 'utf8')
  const prevDoc = JSON.parse(prev)
  const added = Object.keys(doc.paths).filter(p => !prevDoc.paths[p])
  const removed = Object.keys(prevDoc.paths).filter(p => !doc.paths[p])
  console.log(
    `[api:sync] 快照已更新(${Object.keys(prevDoc.paths).length} -> ${Object.keys(doc.paths).length} 个路径),git diff 可核账`
  )
  if (added.length) {
    console.log('[api:sync] 新增路径:')
    added.forEach(p => console.log(`  + ${p}`))
  }
  if (removed.length) {
    console.log('[api:sync] 移除路径(引用它的生成物需人工复核):')
    removed.forEach(p => console.log(`  - ${p}`))
  }
}

main()
