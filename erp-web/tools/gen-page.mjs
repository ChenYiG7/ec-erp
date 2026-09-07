#!/usr/bin/env node
/**
 * gen:page 主入口:行式 spec + openapi 快照 → 页面四件 + 菜单 SQL
 *
 * 用法: pnpm gen:page --spec tools/specs/<domain>.txt [--force]
 * 纪律(对齐后端 erp-codegen):
 * - 存在即跳过(SKIP),--force 才覆盖人工改动,覆盖前请先 diff
 * - 守卫前置:spec 缺头/未知键/todoId 未登记 TODO.md/openapi 缺快照,一律报错退出,禁静默降级
 * - 只产骨架不产业务:字典联动/跨字段校验/动作调用一律 TODO(编号) 槽位
 */
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { loadOpenapi, detectBaseSeg, classifyEndpoints, verifyChatEndpoints, fail } from './lib/openapi.js'
import { parseSpec } from './lib/spec.js'
import { buildContext, renderApi, renderTypes, renderIndex, renderForm, renderMenuSql, buildChatContext, renderChatTypes, renderChatApi, renderChatIndex } from './lib/render.js'
import { writeIfAbsent } from './lib/fsutil.js'

const WEB_ROOT = path.dirname(path.dirname(fileURLToPath(import.meta.url)))

const args = process.argv.slice(2)
const force = args.includes('--force')
const specIdx = args.indexOf('--spec')
const specArg = specIdx >= 0 ? args[specIdx + 1] : null
if (!specArg) {
  fail('用法: pnpm gen:page --spec tools/specs/<domain>.txt [--force]')
}
const specPath = path.isAbsolute(specArg) ? specArg : path.join(WEB_ROOT, specArg)

// 1) spec 拍板表(缺头/未知键/todoId 未登记在此报错,带行号)
const { spec, warnings } = parseSpec(specPath)

// 2) openapi 快照 + 按模式装配(pageType=chat 走会话页模板,缺省 crud)
const doc = loadOpenapi()
let ctx
let outputs
if (spec.pageType === 'chat') {
  // chat 模式:五端点在快照核账({role} 字面量与快照路径形态一致),无 ProTable/表单
  verifyChatEndpoints(doc, spec.chatBase)
  ctx = buildChatContext({ spec, warnings })
  outputs = [
    [`src/api/interface/${ctx.module}/${ctx.domain}.ts`, renderChatTypes(ctx)],
    [`src/api/apis/${ctx.module}/${ctx.domain}.ts`, renderChatApi(ctx)],
    [`src/views/${ctx.component}.vue`, renderChatIndex(ctx)]
  ]
} else {
  const baseSeg = detectBaseSeg(doc, spec.module, spec.domain, spec.base)
  const endpoints = classifyEndpoints(doc, baseSeg)
  if (!endpoints.page && !endpoints.detail) {
    fail(`域 ${baseSeg} 下既无分页也无详情端点,确认 spec.base= 与后端 Controller 是否对得上`)
  }

  // 3) 渲染上下文(spec 字段装配 openapi 类型)
  ctx = buildContext({ spec, endpoints, doc, baseSeg, warnings })

  // spec 字段与 openapi 契约核账(类型层已兜底 unknown,这里提示展示层风险)
  for (const f of ctx.fields) {
    if (!f.known && ctx.responseSchema?.properties) {
      ctx.warnings.push(`spec 字段 ${f.name} 不在后端响应 schema 中,列展示恒空;确认字段名或后端 Response 是否漏字段`)
    }
  }

  // 4) 四件写出(视图目录以 component 派生,Form 与 index 同目录的 components/ 下)
  const viewDir = path.join('src/views', path.posix.dirname(ctx.component))
  outputs = [
    [`src/api/interface/${ctx.module}/${ctx.domain}.ts`, renderTypes(ctx)],
    [`src/api/apis/${ctx.module}/${ctx.domain}.ts`, renderApi(ctx)],
    [`src/views/${ctx.component}.vue`, renderIndex(ctx)]
  ]
  const formContent = renderForm(ctx)
  if (formContent) {
    outputs.push([path.posix.join(viewDir, `components/${ctx.entity}Form.vue`).replaceAll('\\', '/'), formContent])
  }
}

console.log(
  `[gen:page] ${ctx.nameZh}: module=${ctx.module} domain=${ctx.domain} pageType=${ctx.pageType || 'crud'}` +
    (ctx.pageType === 'chat' ? ` chatBase=${ctx.chatBase}` : ` base=${ctx.basePath} readonly=${ctx.readonly}`) +
    ` component=${ctx.component}`
)
for (const [rel, content] of outputs) {
  const status = writeIfAbsent(path.join(WEB_ROOT, rel), content, { force })
  console.log(`[gen:page] ${status.padEnd(5)} ${rel.replaceAll('\\', '/')}`)
}

// 5) 菜单 SQL(人工核对后执行并回写 docs/sql/01_schema_init.sql,禁脚本直连 DB)
console.log('\n[gen:page] ---- 菜单 SQL(人工核对 id 未占用后执行,并回写 docs/sql/01_schema_init.sql) ----')
console.log(renderMenuSql(ctx))

if (ctx.warnings.length) {
  console.log(`\n[gen:page] ⚠️ ${ctx.warnings.length} 条警告(不阻断,人工过目):`)
  for (const w of ctx.warnings) {
    console.log(`[gen:page]   - ${w}`)
  }
}
