/**
 * 行式 spec 解析 —— spec 即拍板表文档化(对齐后端 StateMachineTestGenerator)
 * 纪律:缺头/未知键/未知 role/重名字段报错且带行号;缺 nameZh 打警告;todoId 必须已登记 TODO.md
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const WEB_ROOT = path.dirname(path.dirname(path.dirname(fileURLToPath(import.meta.url)))) // erp-web/
const REPO_ROOT = path.dirname(WEB_ROOT)

const HEAD_KEYS = ['module', 'domain', 'entity', 'nameZh', 'permPrefix', 'todoId', 'menuParent', 'menuId', 'path', 'component', 'base', 'icon', 'readonly']
const FIELD_ROLES = ['search', 'column', 'form', 'all']
const FIELD_KEYS = ['label', 'role', 'dict', 'enum', 'width', 'money', 'required', 'hide', 'tag']

/**
 * @param {string} specPath
 * @returns {{ spec: object, fields: object[], todos: object[] }}
 */
export function parseSpec(specPath) {
  if (!fs.existsSync(specPath)) {
    fail(`spec 文件不存在: ${specPath}`)
  }
  const lines = fs.readFileSync(specPath, 'utf8').split(/\r?\n/)

  const spec = { readonly: false, fields: [], todos: [] }
  /** @type {Map<string, object>} */
  const fieldByName = new Map()
  const warnings = []
  const headerSeen = new Set()

  lines.forEach((rawLine, idx) => {
    const lineNo = idx + 1
    const line = rawLine.trim()
    if (!line || line.startsWith('#')) {
      return
    }

    if (line.startsWith('field=')) {
      parseFieldLine(line, lineNo, fieldByName)
      return
    }
    if (line.startsWith('todo=')) {
      const rest = line.slice(5).trim()
      const sep = rest.indexOf(':')
      if (sep <= 0) {
        failAt(specPath, lineNo, `todo 行格式错误,应为 todo=<key>:<说明>`)
      }
      spec.todos.push({ key: rest.slice(0, sep).trim(), desc: rest.slice(sep + 1).trim(), lineNo })
      return
    }

    const eq = line.indexOf('=')
    if (eq <= 0) {
      failAt(specPath, lineNo, `无法识别的行(应为 key=value / field=... / todo=...): ${line}`)
    }
    const key = line.slice(0, eq).trim()
    const value = line.slice(eq + 1).trim()
    if (!HEAD_KEYS.includes(key)) {
      failAt(specPath, lineNo, `未知的 spec 头部键: ${key}(允许: ${HEAD_KEYS.join('/')})`)
    }
    if (headerSeen.has(key)) {
      failAt(specPath, lineNo, `头部键重复: ${key}`)
    }
    headerSeen.add(key)
    spec[key] = value
  })

  // 头段必填守卫
  for (const required of ['module', 'domain', 'entity', 'nameZh', 'permPrefix', 'todoId']) {
    if (!spec[required]) {
      fail(`spec 缺头部必填键: ${required}(${specPath})`)
    }
  }
  if (!/^\d+$/.test(String(spec.todoId))) {
    fail(`todoId 必须是纯数字编号: ${spec.todoId}`)
  }
  // TODO 编号登记守卫(禁裸 TODO 同款)
  const todoMd = fs.readFileSync(path.join(REPO_ROOT, 'TODO.md'), 'utf8')
  if (!todoMd.includes(`## #${spec.todoId} `)) {
    fail(`TODO.md 未登记 #${spec.todoId} 条目——先登记再生成(禁裸 TODO)`)
  }
  if (!spec.nameZh || /^[\x00-\x7F]*$/.test(spec.nameZh)) {
    warnings.push('nameZh 缺失或非中文,菜单/页面标题将不可读')
  }

  spec.fields = [...fieldByName.values()]
  spec.todos.forEach(td => {
    const known = spec.fields.some(f => f.name === td.key)
    if (!known) {
      warnings.push(`todo=<${td.key}> 未匹配到任何 field 行(字段级槽位)?确认是否页面级动作`)
    }
  })

  return { spec, warnings }
}

function parseFieldLine(line, lineNo, fieldByName) {
  const specPath = 'spec'
  const body = line.slice(6).trim()
  const tokens = body.split(/\s+/)
  const name = tokens[0]
  if (!/^[a-zA-Z][a-zA-Z0-9_]*$/.test(name)) {
    failAt(specPath, lineNo, `字段名非法: ${name}`)
  }
  if (fieldByName.has(name)) {
    failAt(specPath, lineNo, `字段重复定义: ${name}`)
  }

  /** @type {object} */
  const field = { name, role: null, label: null, dict: null, enumMap: null, width: null, money: false, required: false, hide: false, tag: false, lineNo }

  for (const token of tokens.slice(1)) {
    const eq = token.indexOf('=')
    if (eq === -1) {
      if (token === 'money') { field.money = true; continue }
      if (token === 'required') { field.required = true; continue }
      if (token === 'hide') { field.hide = true; continue }
      if (token === 'tag') { field.tag = true; continue }
      failAt(specPath, lineNo, `字段 ${name} 存在无法识别的标记: ${token}`)
    }
    const k = token.slice(0, eq)
    const v = token.slice(eq + 1)
    if (!FIELD_KEYS.includes(k)) {
      failAt(specPath, lineNo, `字段 ${name} 存在未知的键: ${k}(允许: ${FIELD_KEYS.join('/')})`)
    }
    if (k === 'role') {
      if (!FIELD_ROLES.includes(v)) {
        failAt(specPath, lineNo, `字段 ${name} 的 role 非法: ${v}(允许: ${FIELD_ROLES.join('/')})`)
      }
      field.role = v
    } else if (k === 'label') {
      field.label = v
    } else if (k === 'dict') {
      field.dict = v
    } else if (k === 'enum') {
      // 格式: 值:标签[:tagType],多值用 | 分隔;数字形态值收敛为 Number(ProTable filterEnum 走 === 严格相等)
      field.enumMap = v.split('|').map(pair => {
        const parts = pair.split(':')
        if (parts.length < 2 || !parts[0] || !parts[1]) {
          failAt(specPath, lineNo, `字段 ${name} 的 enum 格式非法: ${pair}(应为 值:标签[:tagType],如 1:启用:success)`)
        }
        const raw = parts[0].trim()
        const item = { value: /^\d+$/.test(raw) ? Number(raw) : raw, label: parts[1].trim() }
        if (parts[2]) {
          item.tagType = parts[2].trim()
        }
        return item
      })
    } else if (k === 'width') {
      if (!/^\d+$/.test(v)) {
        failAt(specPath, lineNo, `字段 ${name} 的 width 必须是数字: ${v}`)
      }
      field.width = Number(v)
    }
  }

  if (!field.role) {
    failAt(specPath, lineNo, `字段 ${name} 缺 role=(允许: ${FIELD_ROLES.join('/')})`)
  }
  if (!field.label) {
    console.warn(`[gen:page] spec:${lineNo} 字段 ${name} 缺 label=中文标注(列/表单标题将回落字段名)`)
  }
  if (field.dict && field.enumMap) {
    failAt(specPath, lineNo, `字段 ${name} 同时声明 dict= 与 enum=,二选一`)
  }
  fieldByName.set(name, field)
}

function failAt(_specPath, lineNo, message) {
  console.error(`[gen:page] spec:${lineNo} ${message}`)
  process.exit(1)
}

/** 非行级错误(文件级守卫) */
function fail(message) {
  console.error(`[gen:page] ${message}`)
  process.exit(1)
}
