/**
 * 校验种子配图与 init.sql 的一致性（无外部依赖）。
 * 检查：SVG 结构合法、文件名可被解析、每道菜谱/食材都能找到对应图片。
 */
import { readFileSync, readdirSync, existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = join(__dirname, '..')
const UP = join(ROOT, 'backend', 'uploads')

const sql = readFileSync(join(__dirname, 'init.sql'), 'utf8')

/** 按列位置解析 INSERT 数据块（引号感知） */
function parseRows(table) {
  const m = sql.match(new RegExp('INSERT INTO `' + table + '`[\\s\\S]*?;'))
  if (!m) throw new Error('no INSERT for ' + table)
  const header = m[0].match(/\(([^)]*)\)\s*VALUES/)[1]
  const cols = header.split(',').map((s) => s.trim().replace(/`/g, ''))
  const rows = []
  for (const line of m[0].split('\n')) {
    const t = line.trim()
    if (!t.startsWith('(')) continue
    // 去掉行尾的 ")," / ");" / ")" 以及可选分号
    const body = t.replace(/\)\s*[,;]?\s*$/, '').slice(1)
    const parts = []
    let cur = '', q = false
    for (let i = 0; i < body.length; i++) {
      const ch = body[i]
      if (ch === "'") { q = !q; cur += ch }
      else if (ch === ',' && !q) { parts.push(cur.trim()); cur = '' }
      else cur += ch
    }
    parts.push(cur.trim())
    const obj = {}
    cols.forEach((c, i) => (obj[c] = parts[i]))
    rows.push(obj)
  }
  return rows
}

const unq = (s) => (s === 'NULL' || s == null ? null : s.replace(/^'|'$/g, '').replace(/''/g, "'"))
const slug = (s) => s.replace(/[\\/:*?"<>|\s]/g, '_')

const recipes = parseRows('foods_menu_info')
const materials = parseRows('foods_material_info')

let errors = []
let checked = 0

/* 1. SVG 结构合法性 */
const svgs = readdirSync(UP).filter((f) => f.endsWith('.svg'))
for (const f of svgs) {
  const s = readFileSync(join(UP, f), 'utf8')
  if (!s.startsWith('<svg')) errors.push(`${f}: 缺少 <svg 开头`)
  if (!s.trimEnd().endsWith('</svg>')) errors.push(`${f}: 缺少 </svg> 结尾`)
  const open = (s.match(/<svg/g) || []).length
  const close = (s.match(/<\/svg>/g) || []).length
  if (open !== 1 || close !== 1) errors.push(`${f}: svg 标签数异常 open=${open} close=${close}`)
  if (!/viewBox="0 0 400 400"/.test(s)) errors.push(`${f}: viewBox 缺失`)
  if (!/aria-label="[^"]+"/.test(s)) errors.push(`${f}: aria-label 缺失`)
  // 标签配平（粗略）
  for (const tag of ['defs', 'linearGradient', 'text', 'circle', 'rect', 'path', 'ellipse', 'g']) {
    const o = (s.match(new RegExp('<' + tag + '[\\s>]', 'g')) || []).length
    const c = (s.match(new RegExp('</' + tag + '>', 'g')) || []).length
    const selfClose = (s.match(new RegExp('<' + tag + '[^>]*/>', 'g')) || []).length
    if (o - selfClose !== c) errors.push(`${f}: <${tag}> 未配平 open=${o} self=${selfClose} close=${c}`)
  }
}

/* 2. 每个菜谱/食材都能找到图片，且 fileId 指向 nx_system_file_info 中正确的记录 */
const files = parseRows('nx_system_file_info')
const byId = new Map(files.map((r) => [Number(unq(r.id)), unq(r.fileName)]))

/* 当前启用的风格前缀：从 SQL 中实际引用的文件名推断 */
const sample = [...files.map((r) => unq(r.fileName))].find(Boolean) || 'seed-recipe-x.svg'
const prefix = sample.startsWith('seed2-') ? 'seed2' : 'seed'

function check(rows, kind, labelKey) {
  for (const r of rows) {
    checked++
    const name = unq(r[labelKey])
    const fileName = unq(r.fileName)
    const fileId = r.fileId === 'NULL' ? null : Number(r.fileId)
    const expected = `${prefix}-${kind}-${slug(name)}.svg`

    if (fileName !== expected) errors.push(`${kind} 「${name}」: fileName=${fileName} 期望=${expected}`)
    if (fileId == null) { errors.push(`${kind} 「${name}」: fileId 为 NULL`); continue }
    if (!byId.has(fileId)) { errors.push(`${kind} 「${name}」: fileId=${fileId} 在 nx_system_file_info 中不存在`); continue }
    if (byId.get(fileId) !== expected) errors.push(`${kind} 「${name}」: fileId=${fileId} 指向 ${byId.get(fileId)}，期望 ${expected}`)
    if (!existsSync(join(UP, expected))) errors.push(`${kind} 「${name}」: 磁盘缺少文件 ${expected}`)
  }
}

check(recipes, 'recipe', 'name')
check(materials, 'material', 'name')

/* 3. 无孤儿文件 / 无缺失文件（仅针对当前启用的风格前缀） */
const used = new Set([...recipes, ...materials].map((r) => unq(r.fileName)))
const activeSvgs = svgs.filter((f) => f.startsWith(prefix))
const otherSvgs = svgs.filter((f) => !f.startsWith(prefix) && /^seed2?-(recipe|material)-/.test(f))
const orphans = activeSvgs.filter((f) => !used.has(f))
const missing = [...used].filter((f) => !activeSvgs.includes(f))
if (orphans.length) errors.push(`孤儿图片（该风格未被引用）：${orphans.join(', ')}`)
if (missing.length) errors.push(`缺失图片：${missing.join(', ')}`)

console.log(`启用风格          : ${prefix}*`)
console.log(`该风格 SVG 文件数 : ${activeSvgs.length}`)
if (otherSvgs.length) console.log(`另一风格文件数    : ${otherSvgs.length}`)
console.log(`已核对数据行      : ${checked}（菜谱 ${recipes.length} + 食材 ${materials.length}）`)
console.log(`nx_system_file_info: ${files.length} 条`)
if (errors.length === 0) {
  console.log('\n✅ 全部校验通过：图片与数据一一对应，SVG 结构合法。')
} else {
  console.log(`\n❌ 发现 ${errors.length} 个问题：`)
  for (const e of errors) console.log('  - ' + e)
  process.exitCode = 1
}
