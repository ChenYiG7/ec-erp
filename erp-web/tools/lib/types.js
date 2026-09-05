/**
 * OpenAPI schema -> TS 类型映射
 * 守卫:无 type 且无 $ref 的 schema 报错退出,禁静默 any(docs/09 §11)
 */
import { resolveRef, refName } from './openapi.js'

/**
 * @param {object} schema
 * @param {object} doc openapi 文档
 * @param {object} opts { money: boolean } money=true 时 number -> string(金额 DECIMAL 禁浮点)
 * @returns {{ ts: string, comment?: string }}
 */
export function schemaToTs(schema, doc, { money = false } = {}) {
  if (!schema) {
    return { ts: 'unknown', comment: '// ⚠️ openapi 缺失 schema 定义,人工确认' }
  }
  if (schema.$ref) {
    const resolved = resolveRef(doc, schema.$ref)
    const name = refName(schema.$ref)
    // Result 包装在类型层不出现(http 层已解包)
    return { ts: name || 'unknown' }
  }
  if (schema.type === 'integer' || schema.type === 'number') {
    // 金额红线:DECIMAL(12,4) 序列化值按 string 处理,禁浮点运算(docs/09 §6)
    return money
      ? { ts: 'string', comment: '// 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算' }
      : { ts: 'number' }
  }
  if (schema.type === 'string') {
    return { ts: 'string' }
  }
  if (schema.type === 'boolean') {
    return { ts: 'boolean' }
  }
  if (schema.type === 'array') {
    const item = schemaToTs(schema.items, doc, { money })
    return { ts: `${item.ts}[]` }
  }
  if (schema.type === 'object' || schema.properties) {
    return { ts: 'Record<string, unknown>' }
  }
  if (Array.isArray(schema.oneOf) || Array.isArray(schema.anyOf)) {
    const variants = (schema.oneOf || schema.anyOf).map(s => schemaToTs(s, doc, { money }).ts)
    return { ts: variants.join(' | ') }
  }
  throw new Error(`未知 schema 形态: ${JSON.stringify(schema).slice(0, 120)} —— 禁静默 any,请在 spec 显式声明`)
}
