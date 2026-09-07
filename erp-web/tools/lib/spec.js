/**
 * 行式 spec 解析 —— spec 即拍板表文档化(对齐后端 StateMachineTestGenerator)
 * 纪律:缺头/未知键/未知 role/重名字段报错且带行号;缺 nameZh 打警告;todoId 必须已登记 TODO.md
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const WEB_ROOT = path.dirname(path.dirname(path.dirname(fileURLToPath(import.meta.url)))) // erp-web/
const REPO_ROOT = path.dirname(WEB_ROOT)

const HEAD_KEYS = ['module', 'domain', 'entity', 'nameZh', 'permPrefix', 'todoId', 'menuParent', 'menuId', 'menuSort', 'path', 'component', 'base', 'icon', 'readonly', 'pageType', 'chatBase', 'typesFrom', 'emptyText', 'placeholder']
const PAGE_TYPES = ['crud', 'chat']
const ROLE_KEYS = ['name', 'empty', 'placeholder']
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

  const spec = { readonly: false, pageType: 'crud', fields: [], todos: [], roles: [] }
  /** @type {Map<string, object>} */
  const fieldByName = new Map()
  /** @type {Set<string>} */
  const roleNames = new Set()
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
    if (line.startsWith('role=')) {
      parseRoleLine(line, lineNo, spec.roles, roleNames)
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
  if (spec.pageType && !PAGE_TYPES.includes(spec.pageType)) {
    fail(`pageType 非法: ${spec.pageType}(允许: ${PAGE_TYPES.join('/')})`)
  }
  // chat 模式守卫(会话页模板):端点前缀/类型复用源/角色行自洽
  if (spec.pageType === 'chat') {
    if (!spec.chatBase) {
      fail('chat 模式缺 chatBase=(端点前缀,如 /api/ai/agents/{role})')
    }
    if (!spec.chatBase.startsWith('/api/')) {
      fail(`chatBase 必须 /api/ 全路径: ${spec.chatBase}`)
    }
    if (!spec.typesFrom) {
      fail('chat 模式缺 typesFrom=(契约类型复用源 <module>/<domain>,如 ai/chat;模板不自产会话类型)')
    }
    if (spec.fields.length) {
      fail('chat 模式不支持 field= 字段行(会话页无列/表单拍板)')
    }
    if (spec.roles.length) {
      if (!spec.chatBase.includes('{role}')) {
        fail('声明了 role= 行则 chatBase 必须含 {role} 占位')
      }
      if (spec.emptyText || spec.placeholder) {
        warnings.push('chat 角色模式忽略 emptyText=/placeholder=(文案走 role= 行的 empty=/placeholder=)')
      }
    } else if (!spec.emptyText || !spec.placeholder) {
      warnings.push('chat 无角色模式未声明 emptyText=/placeholder=,生成页回落公共组件默认文案')
    }
  } else if (spec.roles.length) {
    fail('role= 行仅 chat 模式可用(补 pageType=chat)')
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

/**
 * role= 行(chat 模式):`role=<ENUM> name=<中文名> empty=<空态文案> placeholder=<输入提示>`
 * 文案可含空格,按 " 键=" 边界切段(值内空格不切)
 */
function parseRoleLine(line, lineNo, roles, roleNames) {
  const body = line.slice(5).trim()
  const parts = body.split(/\s+(?=[a-zA-Z]+=)/)
  const enumName = parts.shift()
  if (!/^[A-Z][A-Z0-9_]*$/.test(enumName)) {
    failAt('spec', lineNo, `角色枚举非法(须大写常量形态): ${enumName}`)
  }
  if (roleNames.has(enumName)) {
    failAt('spec', lineNo, `角色重复定义: ${enumName}`)
  }
  const role = { enum: enumName, name: null, empty: null, placeholder: null, lineNo }
  for (const part of parts) {
    const eq = part.indexOf('=')
    const k = part.slice(0, eq)
    if (!ROLE_KEYS.includes(k)) {
      failAt('spec', lineNo, `角色 ${enumName} 存在未知的键: ${k}(允许: ${ROLE_KEYS.join('/')})`)
    }
    role[k] = part.slice(eq + 1).trim()
  }
  for (const required of ROLE_KEYS) {
    if (!role[required]) {
      failAt('spec', lineNo, `角色 ${enumName} 缺 ${required}=(拍板文案不落代码)`)
    }
  }
  roleNames.add(enumName)
  roles.push(role)
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
