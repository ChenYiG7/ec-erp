/**
 * openapi 快照加载 / $ref 解析 / 域识别 / CRUD 端点分类
 *
 * 分域纪律:按 /api/<seg> 前缀切分;后端有 6 个基路径无模块段,
 * 例外映射表硬编码于 MODULE_BY_SEG —— 表外新例外必须改代码登记,禁运行时猜(docs/09 §11)
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const TOOLS_DIR = path.dirname(path.dirname(fileURLToPath(import.meta.url)))
export const OPENAPI_PATH = path.join(TOOLS_DIR, 'openapi.json')

/** 快照路径:默认 tools/openapi.json;GEN_OPENAPI_PATH 仅供生成器冒烟测试覆盖,业务禁用 */
export function openapiPath() {
  return process.env.GEN_OPENAPI_PATH || OPENAPI_PATH
}

/** 路径首段 -> 后端模块(例外映射:首段 ≠ 模块名) */
export const MODULE_BY_SEG = {
  shops: 'shop',
  'pull-logs': 'shop',
  'shop-products': 'shop',
  'shop-product-skus': 'shop',
  orders: 'order',
  auth: 'system'
}

export function loadOpenapi() {
  const snapshotPath = openapiPath()
  if (!fs.existsSync(snapshotPath)) {
    fail(`openapi 快照不存在: ${snapshotPath}\n先起后端并执行: pnpm api:sync`)
  }
  const doc = JSON.parse(fs.readFileSync(snapshotPath, 'utf8'))
  if (!doc.openapi || !doc.paths) {
    fail('openapi.json 不是合法的 OpenAPI 文档(缺 openapi/paths 字段),请重跑 pnpm api:sync')
  }
  return doc
}

export function resolveRef(doc, ref) {
  if (!ref || !ref.startsWith('#/')) {
    return null
  }
  let node = doc
  for (const seg of ref.slice(2).split('/')) {
    node = node?.[seg.replaceAll('~1', '/').replaceAll('~0', '~')]
    if (!node) {
      return null
    }
  }
  return node
}

/** $ref 名(components.schemas 键) */
export const refName = ref => (ref?.startsWith('#/') ? ref.split('/').pop() : null)

export function fail(message) {
  console.error(`[gen:page] ${message}`)
  process.exit(1)
}

/**
 * chat 模式端点守卫:chatBase 五端点必须都在快照中,缺一报错退出(禁静默降级)
 * chatBase 的 {role} 字面量与快照路径形态一致(如 /api/ai/agents/{role}/sessions)
 */
export function verifyChatEndpoints(doc, chatBase) {
  const required = [
    ['GET', '/sessions'],
    ['POST', '/sessions'],
    ['GET', '/sessions/{sessionId}/messages'],
    ['POST', '/sessions/{sessionId}/chat'],
    ['POST', '/sessions/{sessionId}/chat-sync']
  ]
  const missing = required.filter(([method, suffix]) => !doc.paths?.[chatBase + suffix]?.[method.toLowerCase()])
  if (missing.length) {
    fail(
      `快照缺 chat 端点(先 pnpm api:sync 刷新,或核对 chatBase= 是否与后端 Controller 一致):` +
        missing.map(([method, suffix]) => `${method} ${chatBase}${suffix}`).join('、')
    )
  }
}

/**
 * 域的基路径探测:显式 spec.base > 与 domain 同名段 > 与 module 同名段 > 报错
 * 支持多段嵌套形态(如 base=system/users → /api/system/users),按前缀匹配
 * @returns {string} 如 "shops"/"system/users"(拼 /api/<base>)
 */
export function detectBaseSeg(doc, module, domain, explicitBase) {
  const candidates = explicitBase ? [explicitBase] : [domain, module]
  for (const seg of candidates) {
    const base = `/api/${seg}`
    if (Object.keys(doc.paths).some(p => p === base || p.startsWith(base + '/'))) {
      return seg
    }
  }
  fail(
    `未找到域基路径:尝试过 [${candidates.join(', ')}],均不存在于 openapi 快照。\n` +
      `请在 spec 里显式声明 base=<路径段>(如 base=shops 对应 /api/shops)`
  )
}

/**
 * 端点分类(对齐后端 RESTful 惯例)
 * @returns {{page?,detail?,create?,update?,remove?,actions:[],extra:[]}}
 */
export function classifyEndpoints(doc, baseSeg) {
  const base = `/api/${baseSeg}`
  const result = { page: null, detail: null, create: null, update: null, remove: null, actions: [], extra: [] }

  for (const [p, methods] of Object.entries(doc.paths)) {
    if (!p.startsWith(base + '/') && p !== base) {
      continue
    }
    for (const [method, op] of Object.entries(methods)) {
      if (!['get', 'post', 'put', 'delete'].includes(method)) {
        continue
      }
      const endpoint = { path: p, method, op }
      const rest = p === base ? '' : p.slice(base.length)
      const idAction = rest.match(/^\/\{id\}\/([^/]+)$/)

      if (p === base && method === 'get' && hasPageParams(op, doc)) {
        result.page = endpoint
      } else if (p === base && method === 'post') {
        result.create = endpoint
      } else if (rest === '/{id}' && method === 'get') {
        result.detail = endpoint
      } else if (rest === '/{id}' && method === 'put') {
        result.update = endpoint
      } else if (rest === '/{id}' && method === 'delete') {
        result.remove = endpoint
      } else if (idAction && method === 'post') {
        result.actions.push({ ...endpoint, verb: idAction[1] })
      } else {
        result.extra.push(endpoint)
      }
    }
  }
  return result
}

/** 分页参数判定:扁平 pageNo/pageSize,或对象形态(query 参数 $ref 到含分页键的 Query schema,springdoc 渲染) */
function hasPageParams(op, doc) {
  return pageParameterEntries(op, doc) !== null
}

/** 分页参数条目:{flat, fields};fields 已剔除分页键;非分页端点返回 null */
export function pageParameterEntries(op, doc) {
  const params = op.parameters || []
  const pageKeys = ['pageNo', 'pageSize', 'pageNum']
  // 混合形态(如 GET /system/dicts:PageQuery 对象 + 扁平 dictType)与扁平形态统一收集搜索字段
  const flatSearch = params.filter(pr => pr.in === 'query' && !pageKeys.includes(pr.name) && !pr.schema?.$ref)
  const flat = params.filter(pr => pr.in === 'query' && pageKeys.includes(pr.name))
  if (flat.length) {
    return { flat: true, fields: flatSearch.map(pr => ({ name: pr.name, description: pr.description || '', schema: pr.schema })) }
  }
  for (const pr of params) {
    if (pr.in !== 'query' || !pr.schema?.$ref) {
      continue
    }
    const schema = resolveRef(doc, pr.schema.$ref)
    if (schema?.properties && ('pageNo' in schema.properties || 'pageSize' in schema.properties)) {
      const fields = [
        ...flatSearch.map(fpr => ({ name: fpr.name, description: fpr.description || '', schema: fpr.schema })),
        ...Object.entries(schema.properties)
          .filter(([k]) => !pageKeys.includes(k))
          .map(([name, s]) => ({ name, description: schema.properties[name]?.description || '', schema: s }))
      ]
      return { flat: false, fields }
    }
  }
  return null
}

/** 200 响应的 content schema(无则 null) */
export function responseSchema(op, doc) {
  const schema = op?.responses?.['200']?.content?.['*/*']?.schema ?? op?.responses?.['200']?.content?.['application/json']?.schema
  return schema ? resolveRef(doc, schema.$ref) ?? schema : null
}

/** 解包 Result 包装(properties 含 code/msg/data)→ data schema;否则原样 */
export function unwrapResult(schema, doc) {
  if (schema && schema.properties && 'code' in schema.properties && 'data' in schema.properties) {
    const data = schema.properties.data
    return resolveRef(doc, data.$ref) ?? data
  }
  return schema
}

/** 判断是否 MP Page 形态(records/total) */
export function isPageSchema(schema) {
  return Boolean(schema?.properties && 'records' in schema.properties && 'total' in schema.properties)
}

/** 请求体 schema(解 $ref 到命名 schema,返回 {name, schema} 或 null) */
export function requestBodySchema(op, doc) {
  const content = op?.requestBody?.content?.['application/json']?.schema
  if (!content) {
    return null
  }
  const name = refName(content.$ref)
  const schema = resolveRef(doc, content.$ref) ?? content
  return { name, schema }
}

/** GET query 搜索字段(剔除分页;对象形态 Query $ref 展开) */
export function queryParameters(op, doc) {
  return pageParameterEntries(op, doc)?.fields ?? (op.parameters || [])
    .filter(pr => pr.in === 'query' && !['pageNo', 'pageSize', 'pageNum'].includes(pr.name) && !pr.schema?.$ref)
    .map(pr => ({ name: pr.name, description: pr.description || '', schema: pr.schema || { type: 'string' } }))
}
