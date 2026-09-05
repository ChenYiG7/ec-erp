/**
 * 四件渲染 + 菜单 SQL:api / interface / index.vue / XxxForm.vue / INSERT IGNORE sys_menu
 *
 * 契约锚点(改动前先核对,禁凭记忆):
 * - http 包装 src/utils/request/base.ts:get(url,params)/post(url,body)/put(url,body)/delete(url,params),响应已解包 Result.data
 * - useTable 要求返回 ResultPage{list,total}(src/types/global.d.ts);后端分页是 Page{records,total}
 *   → 差异(入参 pageNo/pageSize vs pageNum,出参 records vs list)只在生成的 page 函数内单点收口
 * - ProTable:操作列 = columns type:'operation' + 页面 #operation 插槽;工具栏左 = 不传 toolbarLeft prop 时 #toolbarLeft 插槽生效
 * - enum 支持静态数组或 () => Promise<{label,value}[]>,两种都同时供单元格格式化与搜索下拉(enumMap 注入)
 * - filterEnum 用 === 严格相等:数字形态值必须收敛为 Number(spec 解析层已做)
 * - KeepAlive:页面组件 defineOptions name 必须 = 路由 name = component 路径 / -> -(auth store routeNameFromComponent)
 */
import { pageParameterEntries, resolveRef, refName } from './openapi.js'
import { schemaToTs } from './types.js'

const pascal = s => s.replace(/(^|-|_)([a-zA-Z])/g, (_, __, c) => c.toUpperCase())
const camel = s => {
  const p = pascal(s)
  return p.charAt(0).toLowerCase() + p.slice(1)
}

const FILE_HEADER = () =>
  [
    '/**',
    ' * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)',
    ' * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动',
    ' * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md',
    ' */',
    ''
  ].join('\n')

/** Vue SFC 用 HTML 注释头部(裸 JSDoc 在 <template> 外有解析风险) */
const VUE_FILE_HEADER = () =>
  [
    '<!--',
    '  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)',
    '  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动',
    '  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md',
    '-->',
    ''
  ].join('\n')

/**
 * 组装渲染上下文(spec 解析结果 + openapi 端点分类 + 字段类型装配)
 * @param opts { spec, endpoints, doc, baseSeg, warnings[] }
 */
export function buildContext({ spec, endpoints, doc, baseSeg, warnings = [] }) {
  const ctx = {
    doc,
    module: spec.module,
    domain: spec.domain,
    entity: spec.entity,
    entityCamel: camel(spec.entity),
    nameZh: spec.nameZh,
    permPrefix: spec.permPrefix,
    todoId: spec.todoId,
    readonly: spec.readonly === 'true',
    baseSeg,
    basePath: `/api/${baseSeg}`,
    path: spec.path || `/${spec.domain}`,
    component: spec.component || `${spec.module}/${spec.domain}/index`,
    menuParent: spec.menuParent || '0',
    menuId: spec.menuId || null,
    icon: spec.icon || null,
    endpoints,
    todos: spec.todos || [],
    nested: new Map(),
    skipNested: new Set([`${spec.entity}Response`, `${spec.entity}SaveRequest`, `${spec.entity}Query`]),
    warnings
  }
  ctx.responseName = `${spec.entity}Response`
  ctx.saveRequestName = `${spec.entity}SaveRequest`
  ctx.queryName = `${spec.entity}Query`
  // Response schema:分页域取 Page 记录行;非分页取 detail(或 GET 根路径)的解包 data
  ctx.responseSchema = resolveResponseSchema(doc, endpoints)
  ctx.requestSchema =
    !ctx.readonly && (endpoints.create || endpoints.update) ? unwrapBody(endpoints.create || endpoints.update, doc) : null
  ctx.queryFields = endpoints.page ? queryParameters(endpoints.page.op, doc) : []
  // 动作端点:任意方法的 /{id}/<verb>(状态机动作 + GET auth-url 这类扩展动作);extra 升格的端点补 verb
  const extraAsActions = endpoints.extra
    .map(e => {
      const m = e.path.slice(`/api/${baseSeg}`.length).match(/^\/\{id\}\/([^/]+)$/)
      return m ? { ...e, verb: m[1] } : null
    })
    .filter(Boolean)
  ctx.actionEndpoints = [...endpoints.actions, ...extraAsActions]
  // 按 path+method 判重(extra 原引用与升格副本不同,引用相等会漏判)
  const actionKey = e => `${e.method} ${e.path}`
  const actionKeys = new Set(ctx.actionEndpoints.map(actionKey))
  ctx.otherExtra = endpoints.extra.filter(e => !actionKeys.has(actionKey(e)))
  // 动作请求体类型(<Entity><Verb>Request,openapi 有 inline 属性体时生成)
  ctx.actionBodyTypes = ctx.actionEndpoints
    .map(act => ({ act, body: unwrapBody(act, doc) }))
    .filter(({ body }) => body?.schema?.properties)
    .map(({ act, body }) => ({ tsName: `${spec.entity}${pascal(act.verb)}Request`, schema: body.schema }))
  ctx.fields = spec.fields.map(f => enrichField(f, ctx))
  return ctx
}

function resolveResponseSchema(doc, endpoints) {
  // 分页行优先:detail 可能是 Map/聚合形态(如商品详情返回 Map),records 行才是列表契约
  if (endpoints.page) {
    const data = unwrapData(endpoints.page.op, doc)
    const rec = data?.properties?.records
    const rowRef = rec?.items?.$ref ?? rec?.$ref
    if (rowRef) {
      return resolveRef(doc, rowRef) ?? null
    }
  }
  const source = endpoints.detail || endpoints.page
  if (!source) {
    return null
  }
  return unwrapData(source.op, doc)
}

/** 解包 Result -> data schema(200 响应) */
function unwrapData(op, doc) {
  const schema = op?.responses?.['200']?.content?.['*/*']?.schema ?? op?.responses?.['200']?.content?.['application/json']?.schema
  const resolved = schema ? resolveRef(doc, schema.$ref) ?? schema : null
  if (resolved?.properties && 'code' in resolved.properties && 'data' in resolved.properties) {
    const data = resolved.properties.data
    return resolveRef(doc, data.$ref) ?? data
  }
  return resolved
}

/** 请求体 schema($ref 解到命名形态,返回 {name, schema} 或 null) */
function unwrapBody(endpoint, doc) {
  const content = endpoint?.op?.requestBody?.content?.['application/json']?.schema
  if (!content) {
    return null
  }
  return { name: refName(content.$ref), schema: resolveRef(doc, content.$ref) ?? content }
}

/** 分页端点搜索字段(扁平参数或对象形态 Query $ref 展开,复用 openapi 判定) */
function queryParameters(op, doc) {
  return pageParameterEntries(op, doc)?.fields ?? []
}

/** spec 字段装配:补 TS 类型(openapi 契约优先)+ 数字形态标记(dict 值 Number 收敛用) */
function enrichField(f, ctx) {
  const props = ctx.responseSchema?.properties
  const propSchema = props?.[f.name]
  const meta = fieldType(propSchema, ctx, f.money)
  const isNumeric =
    propSchema?.type === 'integer' ||
    propSchema?.type === 'number' ||
    (propSchema?.$ref && resolveRef(ctx.doc, propSchema.$ref)?.type === 'integer')
  return {
    ...f,
    ts: meta.ts,
    comment: meta.comment,
    numeric: Boolean(isNumeric),
    known: Boolean(propSchema)
  }
}

/** 字段类型:嵌套 $ref 收集为命名 interface;数组元素同;其余委托 schemaToTs(禁 any 守卫) */
function fieldType(schema, ctx, money) {
  if (!schema) {
    return { ts: 'unknown', comment: `// TODO(#${ctx.todoId}) openapi 无该字段定义,人工核对类型` }
  }
  const nested = ensureNested(schema, ctx)
  if (nested) {
    return { ts: nested }
  }
  if (schema.type === 'array') {
    const itemNested = ensureNested(schema.items, ctx)
    if (itemNested) {
      return { ts: `${itemNested}[]` }
    }
  }
  try {
    return schemaToTs(schema, ctx.doc, { money: Boolean(money) })
  } catch (e) {
    return { ts: 'unknown', comment: `// TODO(#${ctx.todoId}) 未知 schema 形态人工核对: ${String(e.message).slice(0, 80)}` }
  }
}

/** $ref 且具 properties → 收集为嵌套 interface(一层展开,防环);否则返回 null */
function ensureNested(schema, ctx) {
  if (!schema?.$ref) {
    return null
  }
  const resolved = resolveRef(ctx.doc, schema.$ref)
  const name = refName(schema.$ref)
  if (!resolved?.properties || !name || ctx.skipNested.has(name)) {
    return null
  }
  if (ctx.nested.has(name)) {
    return name
  }
  ctx.nested.set(name, { name, body: `  // 解析中` }) // 占位防环
  const lines = []
  for (const [key, sub] of Object.entries(resolved.properties)) {
    const meta = simpleNestedType(sub, ctx)
    if (meta.comment) {
      lines.push(`  ${meta.comment}`)
    }
    lines.push(`  /** ${sub.description || key} */`, `  ${key}: ${meta.ts}`)
  }
  ctx.nested.set(name, { name, body: lines.join('\n') })
  return name
}

function simpleNestedType(sub, ctx) {
  const nested = ensureNested(sub, ctx)
  if (nested) {
    return { ts: nested }
  }
  if (sub?.type === 'array') {
    return { ts: `${simpleNestedType(sub.items, ctx).ts}[]` }
  }
  try {
    return schemaToTs(sub, ctx.doc, {})
  } catch {
    return { ts: 'Record<string, unknown>', comment: `// TODO(#${ctx.todoId}) 嵌套形态人工核对` }
  }
}

/* ------------------------------- ① interface ------------------------------- */

export function renderTypes(ctx) {
  const out = [
    FILE_HEADER(),
    `/** ${ctx.nameZh}接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */`,
    ''
  ]

  // Response
  if (ctx.responseSchema?.properties) {
    out.push(`/** ${ctx.nameZh}实体(分页行 / 详情) */`, `export interface ${ctx.responseName} {`)
    for (const [key, sub] of Object.entries(ctx.responseSchema.properties)) {
      const specField = ctx.fields.find(f => f.name === key)
      const meta = fieldType(sub, ctx, specField?.money)
      if (meta.comment) {
        out.push(`  ${meta.comment}`)
      }
      out.push(`  /** ${sub.description || specField?.label || key} */`)
      out.push(`  ${key}: ${meta.ts}`)
    }
    out.push('}', '')
  }
  // SaveRequest
  if (ctx.requestSchema?.schema?.properties) {
    out.push(`/** ${ctx.nameZh}新增/修改入参 */`, `export interface ${ctx.saveRequestName} {`)
    for (const [key, sub] of Object.entries(ctx.requestSchema.schema.properties)) {
      const specField = ctx.fields.find(f => f.name === key)
      const meta = fieldType(sub, ctx, specField?.money)
      if (meta.comment) {
        out.push(`  ${meta.comment}`)
      }
      out.push(`  /** ${sub.description || key} */`)
      out.push(`  ${key}${ctx.requestSchema.schema.required?.includes(key) ? '' : '?'}: ${meta.ts}`)
    }
    out.push('}', '')
  }
  // 动作请求体(POST /{id}/<verb> 带 inline 属性体)
  for (const bt of ctx.actionBodyTypes) {
    out.push(`/** ${ctx.nameZh}动作请求体 */`, `export interface ${bt.tsName} {`)
    for (const [key, sub] of Object.entries(bt.schema.properties)) {
      const meta = fieldType(sub, ctx, false)
      if (meta.comment) {
        out.push(`  ${meta.comment}`)
      }
      out.push(`  /** ${sub.description || key} */`)
      out.push(`  ${key}${bt.schema.required?.includes(key) ? '' : '?'}: ${meta.ts}`)
    }
    out.push('}', '')
  }
  // Query
  if (ctx.queryFields.length) {
    out.push(`/** ${ctx.nameZh}分页查询入参(不含分页参数) */`, `export interface ${ctx.queryName} {`)
    for (const qf of ctx.queryFields) {
      const specField = ctx.fields.find(f => f.name === qf.name)
      const meta = fieldType(qf.schema, ctx, false)
      if (meta.comment) {
        out.push(`  ${meta.comment}`)
      }
      out.push(`  /** ${qf.description || specField?.label || qf.name} */`)
      out.push(`  ${qf.name}?: ${meta.ts}`)
    }
    out.push('}', '')
  }
  // 嵌套 interface(引用型子结构,如子表明细行)
  for (const nested of ctx.nested.values()) {
    out.push(`/** 嵌套结构(引用自 openapi schema ${nested.name}) */`, `export interface ${nested.name} {`, nested.body, '}', '')
  }
  return out.join('\n')
}

/* ---------------------------------- ② api ---------------------------------- */

export function renderApi(ctx) {
  const lines = ["import http from '@/utils/request'"]
  const typeImports = []
  if (ctx.endpoints.page) {
    lines.push("import type { PageQuery, PageResult } from '@/api/interface'")
  }
  if (ctx.responseSchema && (ctx.endpoints.page || ctx.endpoints.detail)) {
    typeImports.push(ctx.responseName)
  }
  if (ctx.requestSchema) {
    typeImports.push(ctx.saveRequestName)
  }
  if (ctx.queryFields.length) {
    typeImports.push(ctx.queryName)
  }
  for (const bt of ctx.actionBodyTypes) {
    typeImports.push(bt.tsName)
  }
  if (typeImports.length) {
    lines.push(`import type { ${typeImports.join(', ')} } from '@/api/interface/${ctx.module}/${ctx.domain}'`)
  }

  lines.push('')
  lines.push('/**')
  lines.push(` * ${ctx.nameZh}(${ctx.basePath},由 gen:page 生成)`)
  lines.push(' * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面')
  lines.push(' */')
  lines.push(`export const ${ctx.entityCamel}Api = {`)

  if (ctx.endpoints.page) {
    const queryTs = ctx.queryFields.length ? `${ctx.queryName} & PageQuery` : 'PageQuery'
    lines.push(
      `  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */`,
      `  page: (params: ${queryTs}) =>`,
      `    http.get<PageResult<${ctx.responseName}>>('${ctx.basePath}', params).then(page => ({ list: page.records, total: page.total })),`
    )
  }
  if (ctx.endpoints.detail) {
    lines.push(`  /** 详情 */`, `  detail: (id: number) => http.get<${ctx.responseName}>(\`${ctx.basePath}/\${id}\`),`)
  }
  if (!ctx.readonly && ctx.endpoints.create) {
    const rt = dataReturnType(ctx.endpoints.create.op, ctx)
    lines.push(
      `  /** 新增(后端返回主键) */`,
      `  create: (data: ${ctx.saveRequestName}) => http.post<${rt}>(\`${ctx.basePath}\`, data),`
    )
  }
  if (!ctx.readonly && ctx.endpoints.update) {
    lines.push(
      `  /** 修改 */`,
      `  update: (id: number, data: ${ctx.saveRequestName}) => http.put<boolean>(\`${ctx.basePath}/\${id}\`, data),`
    )
  }
  if (!ctx.readonly && ctx.endpoints.remove) {
    lines.push(`  /** 删除 */`, `  remove: (id: number) => http.delete<boolean>(\`${ctx.basePath}/\${id}\`),`)
  }
  // 状态机动作 / 扩展动作(任意方法 /{id}/<verb>)
  // 同域同名冲突(GET+PUT /{id}/roles)时加方法前缀消歧:getRoles / putRoles
  const baseCounts = new Map()
  for (const act of ctx.actionEndpoints) {
    const base = camel(act.verb)
    baseCounts.set(base, (baseCounts.get(base) || 0) + 1)
  }
  const actName = act => {
    const base = camel(act.verb)
    return baseCounts.get(base) > 1 ? `${act.method}${pascal(base)}` : base
  }
  for (const act of ctx.actionEndpoints) {
    const body = unwrapBody(act, ctx.doc)
    const bodyTs = body?.schema?.properties ? `${ctx.entity}${pascal(act.verb)}Request` : ''
    const rt = dataReturnType(act.op, ctx)
    const bodyParam = bodyTs ? `, data: ${bodyTs}` : ''
    lines.push(
      `  /** ${act.op.summary || act.verb}(${act.method.toUpperCase()} ${act.path}) */`,
      `  ${actName(act)}: (id: number${bodyParam}) => http.${act.method}<${rt}>(\`${ctx.basePath}/\${id}/${act.verb}\`${bodyTs ? ', data' : ''}),`
    )
  }
  lines.push('}')

  if (ctx.otherExtra.length) {
    lines.push('', '// ⚠️ 以下端点未归类(gen:page 不生成,人工到本文件补齐并登记 TODO 编号):')
    for (const e of ctx.otherExtra) {
      lines.push(`//   ${e.method.toUpperCase()} ${e.path} — ${e.op.summary || ''}`)
    }
  }
  return lines.join('\n')
}

/** 动作/新增的响应 data 类型(primitive 直出;内联对象收敛 Record + 人工核对) */
function dataReturnType(op, ctx) {
  const data = unwrapData(op, ctx.doc)
  if (!data) {
    return 'void'
  }
  if (data.properties) {
    return 'Record<string, unknown>'
  }
  try {
    return schemaToTs(data, ctx.doc, {}).ts
  } catch {
    return 'unknown'
  }
}

/* -------------------------------- ③ index.vue -------------------------------- */

export function renderIndex(ctx) {
  const routeName = ctx.component.replace(/\//g, '-')
  const perm = ctx.permPrefix
  const hasForm = !ctx.readonly && Boolean(ctx.endpoints.create || ctx.endpoints.update)
  const hasDelete = !ctx.readonly && Boolean(ctx.endpoints.remove)
  const hasOperation = hasForm || hasDelete || ctx.actionEndpoints.length > 0

  // ---- 列配置 ----
  const colLines = [`  { type: 'index', label: '#', width: 55 },`]
  for (const f of ctx.fields.filter(f => (f.role === 'column' || f.role === 'all') && !f.hide)) {
    const parts = [`prop: '${f.name}'`, `label: '${f.label || f.name}'`]
    if (f.width) {
      parts.push(`width: ${f.width}`)
    }
    if (f.tag) {
      parts.push('tag: true')
    }
    if (f.enumMap) {
      parts.push(`enum: ${renderStaticEnum(f)}`)
    } else if (f.dict) {
      const conv = f.numeric
        ? '.map(item => ({ label: item.dictLabel, value: Number(item.dictValue) }))'
        : '.map(item => ({ label: item.dictLabel, value: item.dictValue }))'
      parts.push(`enum: () => useDictStore().getDict('${f.dict}').then(list => list${conv})`)
    }
    colLines.push(`  { ${parts.join(', ')} },`)
  }
  if (hasOperation) {
    colLines.push(`  { prop: 'operation', label: '操作', fixed: 'right', width: ${operationWidth(ctx)} }`)
  }

  // ---- template ----
  const tpl = [`<template>`, `  <div class="table-box">`]
  tpl.push(
    `    <ProTable ref="proTableRef" page-id="${ctx.path}" title="${ctx.nameZh}" :columns="columns" :request-api="${ctx.entityCamel}Api.page">`
  )
  if (hasForm) {
    tpl.push(
      `      <!-- 工具栏左:新增(按钮权限收口在页面侧 v-auth;toolbarLeft prop 的 auth 属性无效,禁用) -->`,
      `      <template #toolbarLeft>`,
      `        <el-button v-auth="'${perm}:add'" type="primary" :icon="CirclePlus" @click="openForm('add')">新增${ctx.nameZh}</el-button>`,
      `      </template>`,
      ``
    )
  }
  if (hasOperation) {
    tpl.push(`      <!-- 操作列(ProTable v2:type:'operation' 列必须提供本插槽) -->`)
    tpl.push(`      <template #operation="scope">`)
    if (hasForm) {
      tpl.push(
        `        <el-button v-auth="'${perm}:edit'" type="primary" link :icon="EditPen" @click="openForm('edit', scope.row)">编辑</el-button>`
      )
    }
    if (hasDelete) {
      tpl.push(
        `        <el-button v-auth="'${perm}:remove'" type="danger" link :icon="Delete" @click="handleDelete(scope.row)">删除</el-button>`
      )
    }
    for (const act of ctx.actionEndpoints) {
      const todo = ctx.todos.find(t => t.key === act.verb)
      tpl.push(
        `        <!-- TODO(#${ctx.todoId}) 动作按钮:${act.verb}(api 已生成 ${camel(act.verb)}())${todo ? ';' + todo.desc : ''} -->`,
        `        <!-- <el-button v-auth="'${perm}:${act.verb}'" type="warning" link @click="on${pascal(act.verb)}(scope.row)">${act.op.summary || act.verb}</el-button> -->`
      )
    }
    tpl.push(`      </template>`)
  }
  tpl.push(`    </ProTable>`)
  if (hasForm) {
    tpl.push(`    <${ctx.entity}Form ref="formRef" @saved="refreshTable" />`)
  }
  tpl.push(`  </div>`, `</template>`)

  // ---- script ----
  const scr = [`<script setup lang="ts">`]
  scr.push(`// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致`)
  scr.push(`defineOptions({ name: '${routeName}' })`)
  scr.push(`import { ref } from 'vue'`)
  const icons = [...(hasForm ? ['CirclePlus', 'EditPen'] : []), ...(hasDelete ? ['Delete'] : [])]
  if (icons.length) {
    scr.push(`import { ${icons.join(', ')} } from '@element-plus/icons-vue'`)
  }
  if (hasForm || hasDelete) {
    // 工具栏/操作列模板发 el-button 标签:element-plus 已改按需显式导入(main.ts 不再全局注册),漏导 = 运行时 Failed to resolve component
    scr.push(`import { ElButton, ElMessage, ElMessageBox } from 'element-plus'`)
  }
  scr.push(`import ProTable from '@/components/ProTable/index.vue'`)
  scr.push(`import type { ColumnProps } from '@/components/ProTable/interface'`)
  if (ctx.fields.some(f => f.dict)) {
    scr.push(`import { useDictStore } from '@/stores/modules/dict'`)
  }
  scr.push(`import { ${ctx.entityCamel}Api } from '@/api/apis/${ctx.module}/${ctx.domain}'`)
  if (ctx.responseSchema) {
    scr.push(`import type { ${ctx.responseName} } from '@/api/interface/${ctx.module}/${ctx.domain}'`)
  }
  if (hasForm) {
    scr.push(`import ${ctx.entity}Form from './components/${ctx.entity}Form.vue'`)
  }
  scr.push('')
  scr.push(`// ProTable 实例(getTableList 供刷新)`)
  scr.push(`const proTableRef = ref<InstanceType<typeof ProTable>>()`)
  if (hasForm) {
    scr.push(`const formRef = ref<InstanceType<typeof ${ctx.entity}Form>>()`)
  }
  scr.push('')
  scr.push(`// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉)`)
  scr.push(`const columns: ColumnProps<${ctx.responseSchema ? ctx.responseName : 'any'}>[] = [`)
  scr.push(...colLines)
  scr.push(`]`)
  scr.push('')
  if (hasForm) {
    scr.push(`const openForm = (mode: 'add' | 'edit', row?: ${ctx.responseSchema ? ctx.responseName : 'any'}) => {`)
    scr.push(`  formRef.value?.open(mode, row)`, `}`, '')
  }
  if (hasDelete) {
    scr.push(`const handleDelete = async (row: ${ctx.responseSchema ? ctx.responseName : 'any'}) => {`)
    scr.push(
      `  await ElMessageBox.confirm('确认删除该${ctx.nameZh}吗?', '提示', { type: 'warning' })`,
      `  await ${ctx.entityCamel}Api.remove(row.id)`,
      `  ElMessage.success('删除成功')`,
      `  refreshTable()`,
      `}`,
      ''
    )
  }
  for (const act of ctx.actionEndpoints) {
    const todo = ctx.todos.find(t => t.key === act.verb)
    scr.push(
      `// TODO(#${ctx.todoId}) 动作 ${act.verb}:${todo?.desc || act.op.summary || '状态机动作,人工补确认弹窗与调用'}`,
      `// const on${pascal(act.verb)} = async (row: ${ctx.responseSchema ? ctx.responseName : 'any'}) => { ... await ${ctx.entityCamel}Api.${camel(act.verb)}(row.id) ... }`,
      ''
    )
  }
  scr.push(`const refreshTable = () => proTableRef.value?.getTableList()`)
  scr.push(`</script>`)
  scr.push('')

  return [VUE_FILE_HEADER(), ...tpl, ...scr].join('\n')
}

function operationWidth(ctx) {
  const hasForm = !ctx.readonly && Boolean(ctx.endpoints.create || ctx.endpoints.update)
  const hasDelete = !ctx.readonly && Boolean(ctx.endpoints.remove)
  const n = (hasForm ? 1 : 0) + (hasDelete ? 1 : 0) + ctx.actionEndpoints.length
  return Math.max(120, n * 70)
}

function renderStaticEnum(f) {
  const items = f.enumMap.map((item, i) => {
    const tagType = item.tagType || (f.tag ? TAG_PALETTE[i % TAG_PALETTE.length] : null)
    return `{ label: '${item.label}', value: ${JSON.stringify(item.value)}${tagType ? `, tagType: '${tagType}'` : ''} }`
  })
  return `[${items.join(', ')}]`
}
const TAG_PALETTE = ['success', 'warning', 'danger', 'primary', 'info']

/**
 * 表单模板实际发出的 el-* 组件集合(与 formItem 控件分支一一对应,顺序即分支优先级):
 * dict → Dict 组件接管不产 el-*;enumMap → el-select/el-option;boolean → el-switch;
 * number 且非金额 → el-input-number;其余 → el-input。骨架 dialog/form/button 恒有。
 */
function elImportsFor(formFields) {
  const usesSelect = formFields.some(f => f.enumMap)
  const usesSwitch = formFields.some(f => !f.dict && !f.enumMap && f.ts === 'boolean')
  const usesInputNumber = formFields.some(f => !f.dict && !f.enumMap && f.ts === 'number' && !f.money)
  const usesInput = formFields.some(f => !f.dict && !f.enumMap && f.ts !== 'boolean' && !(f.ts === 'number' && !f.money))
  return [
    'ElButton',
    'ElDialog',
    'ElForm',
    'ElFormItem',
    usesInput && 'ElInput',
    usesInputNumber && 'ElInputNumber',
    'ElMessage',
    usesSelect && 'ElOption',
    usesSelect && 'ElSelect',
    usesSwitch && 'ElSwitch'
  ]
    .filter(Boolean)
    .sort()
}

/* ------------------------------- ④ XxxForm.vue ------------------------------- */

export function renderForm(ctx) {
  if (ctx.readonly || (!ctx.endpoints.create && !ctx.endpoints.update)) {
    return null
  }
  const formFields = ctx.fields.filter(f => f.role === 'form' || f.role === 'all')
  const required = formFields.filter(f => f.required)
  const saveName = ctx.saveRequestName
  const needSaveType = Boolean(ctx.requestSchema)
  const formType = needSaveType ? saveName : 'Record<string, unknown>'
  const rowType = ctx.responseSchema ? ctx.responseName : 'Record<string, unknown>'

  const formItem = f => {
    const label = f.label || f.name
    const pick = f.enumMap || f.dict
    const placeholder = `placeholder="请${pick ? '选择' : '输入'}${label}"`
    let control
    if (f.dict) {
      control = `<Dict v-model="formData.${f.name}" code="${f.dict}" type="select" />`
    } else if (f.enumMap) {
      const opts = f.enumMap.map(e => `          <el-option label="${e.label}" :value="${JSON.stringify(e.value)}" />`)
      control = [`<el-select v-model="formData.${f.name}" clearable ${placeholder}>`, ...opts, `        </el-select>`].join('\n')
    } else if (f.ts === 'boolean') {
      control = `<el-switch v-model="formData.${f.name}" />`
    } else if (f.ts === 'number' && !f.money) {
      control = `<el-input-number v-model="formData.${f.name}" :min="0" controls-position="right" class="!w-full" />`
    } else {
      control = `<el-input v-model="formData.${f.name}" ${placeholder} clearable />`
    }
    const lines = [`      <el-form-item label="${label}" prop="${f.name}">`, `        ${control}`]
    if (f.money) {
      lines.push(`        <!-- 金额按后端 DECIMAL(12,4) 字符串直存直显,禁 parseFloat/Number 参与计算 -->`)
    }
    lines.push(`      </el-form-item>`)
    return lines.join('\n')
  }

  const tpl = [
    `<template>`,
    `  <el-dialog v-model="visible" :title="title" width="560px" :close-on-click-modal="false" destroy-on-close>`,
    `    <el-form ref="formRef" :model="formData" :rules="rules" label-width="110px">`,
    ...formFields.map(formItem),
    `    </el-form>`,
    `    <template #footer>`,
    `      <el-button @click="visible = false">取消</el-button>`,
    `      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>`,
    `    </template>`,
    `  </el-dialog>`,
    `</template>`
  ]

  const scr = [
    `<script setup lang="ts">`,
    `import { ref } from 'vue'`,
    // 按需导入收口:模板发什么 el-* 标签就导什么组件(与下方 formItem 控件分支一一对应),漏导 = 运行时 Failed to resolve component
    `import { ${elImportsFor(formFields).join(', ')} } from 'element-plus'`,
    `import type { FormInstance, FormRules } from 'element-plus'`,
    ...(formFields.some(f => f.dict) ? [`import Dict from '@/components/Dict/index.vue'`] : []),
    `import { ${ctx.entityCamel}Api } from '@/api/apis/${ctx.module}/${ctx.domain}'`,
    ...(() => {
      const names = [...(needSaveType ? [saveName] : []), ...(ctx.responseSchema ? [ctx.responseName] : [])]
      return names.length ? [`import type { ${names.join(', ')} } from '@/api/interface/${ctx.module}/${ctx.domain}'`] : []
    })(),
    ``,
    `defineOptions({ name: '${ctx.entity}Form' })`,
    ``,
    `const emit = defineEmits<{ saved: [] }>()`,
    ``,
    `const visible = ref(false)`,
    `const submitting = ref(false)`,
    `const formRef = ref<FormInstance>()`,
    `const mode = ref<'add' | 'edit'>('add')`,
    `const editId = ref<number>()`,
    ...(needSaveType
      ? [`// 类型断言收敛在表单初始化(空表单起填,提交前 rules 校验 + 后端兜底校验)`]
      : [`// TODO(#${ctx.todoId}) openapi 未声明请求体 schema,人工补齐表单类型`]),
    `const formData = ref<${formType}>({} as ${formType})`,
    ``,
    `const rules: FormRules = {`,
    ...required.map(
      (f, i) =>
        `  ${f.name}: [{ required: true, message: '请${f.enumMap || f.dict ? '选择' : '输入'}${f.label || f.name}', trigger: '${f.enumMap || f.dict ? 'change' : 'blur'}' }]${i < required.length - 1 ? ',' : ''}`
    ),
    `}`,
    ``,
    `const title = ref('${ctx.nameZh}')`,
    ``,
    `/** 打开弹窗(mode:add/edit);edit 浅拷贝行数据,禁直接引用污染列表行 */`,
    `const open = (m: 'add' | 'edit', row?: ${rowType}) => {`,
    `  mode.value = m`,
    `  editId.value = row?.id`,
    `  title.value = (m === 'add' ? '新增' : '编辑') + '${ctx.nameZh}'`,
    `  formData.value = {} as ${formType}`,
    `  if (row) {`,
    `    Object.assign(formData.value, row)`,
    `  }`,
    `  visible.value = true`,
    `}`,
    ``,
    `const handleSubmit = async () => {`,
    `  await formRef.value?.validate()`,
    `  submitting.value = true`,
    `  try {`,
    `    if (mode.value === 'add') {`,
    `      await ${ctx.entityCamel}Api.create(formData.value)`,
    `    } else {`,
    `      await ${ctx.entityCamel}Api.update(editId.value!, formData.value)`,
    `    }`,
    `    ElMessage.success('保存成功')`,
    `    emit('saved')`,
    `    visible.value = false`,
    `  } finally {`,
    `    submitting.value = false`,
    `  }`,
    `}`,
    ``,
    `defineExpose({ open })`,
    `</script>`,
    ''
  ]
  return [FILE_HEADER(), ...tpl, ...scr].join('\n')
}

/* --------------------------------- ⑤ 菜单 SQL --------------------------------- */

/**
 * 菜单 SQL(menuId 缺省时输出注释模板,人工分配 id 后执行;核对完回写 docs/sql/01_schema_init.sql)
 */
export function renderMenuSql(ctx) {
  const buttonRows = ctx.readonly
    ? []
    : [
        { perm: 'add', name: `新增${ctx.nameZh}`, sort: 1 },
        { perm: 'edit', name: `编辑${ctx.nameZh}`, sort: 2 },
        { perm: 'remove', name: `删除${ctx.nameZh}`, sort: 3 }
      ]
  const values = rows =>
    rows
      .map(
        r =>
          `(${r.id}, ${r.parent}, '${r.name}', ${r.type}, ${r.perm ? `'${r.perm}'` : 'NULL'}, ${r.path ? `'${r.path}'` : 'NULL'}, ${r.comp ? `'${r.comp}'` : 'NULL'}, ${r.icon ? `'${r.icon}'` : 'NULL'}, ${r.sort})`
      )
      .join(',\n')
  const insert = rows =>
    `INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, perm_key, path, component, icon, sort) VALUES\n` +
    values(rows) +
    ';'
  const menuRow = id => ({
    id,
    parent: ctx.menuParent,
    name: ctx.nameZh,
    type: 2,
    perm: null,
    path: ctx.path,
    comp: ctx.component,
    icon: ctx.icon,
    sort: 1
  })
  const btnRow = (menuId, b) => ({
    id: menuId * 100 + b.sort,
    parent: menuId,
    name: b.name,
    type: 3,
    perm: `${ctx.permPrefix}:${b.perm}`,
    path: null,
    comp: null,
    icon: null,
    sort: b.sort
  })

  if (ctx.menuId) {
    const menuId = Number(ctx.menuId)
    const blocks = [`-- ${ctx.nameZh} 菜单(gen:page 生成;menuId=${menuId},人工核对 id 未占用后执行,并回写 docs/sql/01_schema_init.sql)`, insert([menuRow(menuId)])]
    if (buttonRows.length) {
      blocks.push('', `-- 按钮权限(menuType=3),id 段 = menuId*100+n,如冲突人工调整`, insert(buttonRows.map(b => btnRow(menuId, b))))
    }
    return blocks.join('\n')
  }
  const tpl = [
    `-- ${ctx.nameZh} 菜单模板(gen:page 生成;spec 未声明 menuId=,人工分配未占用 id 后执行,并回写 docs/sql/01_schema_init.sql)`,
    `-- ${insert([menuRow('<menuId>')])}`
  ]
  for (const b of buttonRows) {
    tpl.push(
      `-- INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, perm_key, path, component, icon, sort) VALUES (<menuId*100+${b.sort}>, <menuId>, '${b.name}', 3, '${ctx.permPrefix}:${b.perm}', NULL, NULL, NULL, ${b.sort});`
    )
  }
  return tpl.join('\n')
}
