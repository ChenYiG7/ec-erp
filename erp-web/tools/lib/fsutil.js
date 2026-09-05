/**
 * 文件写出工具:存在即跳过(默认),--force 才覆盖 —— 对齐后端 erp-codegen 理念
 * 输出统一 LF + UTF-8 无 BOM(Windows 防线,见根 .gitattributes)
 */
import fs from 'node:fs'
import path from 'node:path'

/** @returns {'GEN'|'SKIP'} */
export function writeIfAbsent(filePath, content, { force = false } = {}) {
  const exists = fs.existsSync(filePath)
  if (exists && !force) {
    return 'SKIP'
  }
  fs.mkdirSync(path.dirname(filePath), { recursive: true })
  fs.writeFileSync(filePath, content.replaceAll('\r\n', '\n'), 'utf8')
  return exists && force ? 'FORCE' : 'GEN'
}
