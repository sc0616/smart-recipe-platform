/**
 * 依据实际生成的 SVG 文件，重写 init.sql 中：
 *   1. nx_system_file_info 的图片记录（替换旧的 13 条已失效记录）
 *   2. foods_menu_info / foods_material_info 的 fileName + fileId
 * 保证「数据库 fileId -> 文件名 -> 磁盘文件」三者严格一致。
 *
 * 用法：node sql/apply-seed-images.mjs
 */
import { readFileSync, writeFileSync, readdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = join(__dirname, '..')
const UP = join(ROOT, 'backend', 'uploads')
const SQL_PATH = join(__dirname, 'init.sql')

const slug = (s) => s.replace(/[\\/:*?"<>|\s]/g, '_')
let sql = readFileSync(SQL_PATH, 'utf8')

/* ---------- 1. 解析现有数据行，取出 name 顺序 ---------- */
function names(table, labelKey) {
  const m = sql.match(new RegExp('INSERT INTO `' + table + '`[\\s\\S]*?;'))
  const out = []
  for (const line of m[0].split('\n')) {
    const t = line.trim()
    if (!t.startsWith('(')) continue
    const first = t.match(/^\('([^']+)'/)
    if (first) out.push(first[1])
  }
  return out
}

const recipeNames = names('foods_menu_info')
const materialNames = names('foods_material_info')

/* ---------- 2. 校验磁盘文件齐备 ---------- */
const onDisk = new Set(readdirSync(UP).filter((f) => f.endsWith('.svg')))
const problems = []
for (const n of recipeNames) if (!onDisk.has(`seed-recipe-${slug(n)}.svg`)) problems.push(`菜谱「${n}」缺图`)
for (const n of materialNames) if (!onDisk.has(`seed-material-${slug(n)}.svg`)) problems.push(`食材「${n}」缺图`)
if (problems.length) {
  console.error('磁盘图片不全，已中止：\n' + problems.map((p) => ' - ' + p).join('\n'))
  process.exit(1)
}

/* ---------- 3. 分配 id：菜谱 1..N，食材 N+1..N+M ---------- */
const fileRows = []
const recipeIds = new Map()
const materialIds = new Map()
let id = 1
for (const n of recipeNames) {
  const fileName = `seed-recipe-${slug(n)}.svg`
  fileRows.push([id, `${n}.svg`, fileName])
  recipeIds.set(n, id++)
}
for (const n of materialNames) {
  const fileName = `seed-material-${slug(n)}.svg`
  fileRows.push([id, `${n}.svg`, fileName])
  materialIds.set(n, id++)
}

/* ---------- 4. 重写 nx_system_file_info ---------- */
const fileBlock =
  '-- 种子配图文件记录（与 backend/uploads/ 下的 SVG 一一对应）。\n' +
  '-- 这些图片由 sql/gen-seed-images.mjs 生成，随仓库一起分发，\n' +
  '-- 因此 clone 后无需上传任何文件即可看到完整图文效果。\n' +
  'INSERT INTO `nx_system_file_info` (`id`, `originName`, `fileName`) VALUES\n' +
  fileRows
    .map(([i, origin, fn], idx) => {
      const comma = idx === fileRows.length - 1 ? ';' : ','
      return `(${i}, '${origin}', '${fn}')${comma}`
    })
    .join('\n')

sql = sql.replace(
  /-- 种子配图文件记录[\s\S]*?;\n|INSERT INTO `nx_system_file_info`[\s\S]*?;/,
  fileBlock
)

/* ---------- 5. 为每道菜谱/食材写回 fileName 与 fileId ---------- */
function applyIds(table, ids) {
  const re = new RegExp('(INSERT INTO `' + table + '`[\\s\\S]*?;)')
  const block = sql.match(re)[1]
  const lines = block.split('\n').map((line) => {
    const t = line.trim()
    if (!t.startsWith('(')) return line
    const m = t.match(/^\('([^']+)'/)
    if (!m) return line
    const name = m[1]
    const fid = ids.get(name)
    if (fid == null) return line
    const fname = `seed-${table === 'foods_menu_info' ? 'recipe' : 'material'}-${slug(name)}.svg`
    // 替换第 5、6 列：..., tips, classifyId, fileName, fileId, userName, ...
    // 原占位形式为 "NULL, NULL, 'admin'"，因此用定位替换更稳妥
    const replaced = t.replace(
      /,\s*(NULL|'[^']*')\s*,\s*(NULL|\d+)\s*,\s*'admin'/,
      `, '${fname}', ${fid}, 'admin'`
    )
    if (replaced === t) return line
    const indent = line.slice(0, line.length - line.trimStart().length)
    const trailing = t.endsWith(';') ? ';' : t.endsWith(',') ? ',' : ''
    return indent + replaced.replace(/[;,]$/, '') + trailing
  })
  sql = sql.replace(re, lines.join('\n'))
}

applyIds('foods_menu_info', recipeIds)
applyIds('foods_material_info', materialIds)

/* ---------- 6. 更新注释中的数量描述 ---------- */
sql = sql.replace(/-- 菜谱种子数据（共 \d+ 道，覆盖 \d+ 个分类）。[\s\S]*?-- classifyId 对应/,
  `-- 菜谱种子数据（共 ${recipeNames.length} 道，覆盖 6 个分类）。\n` +
  '-- 每条数据的 fileName/fileId 均指向 nx_system_file_info 中真实存在的配图，\n' +
  '-- 图片位于 backend/uploads/，随仓库分发。\n' +
  '-- classifyId 对应')
sql = sql.replace(/-- 食材种子数据：fileId 置为 NULL[\s\S]*?-- 未上传图片时不会渲染空 <img>，因此无需预置图片文件即可正常展示。/,
  `-- 食材种子数据（共 ${materialNames.length} 种），fileId 指向真实配图。`)

writeFileSync(SQL_PATH, sql, 'utf8')

console.log(`已写回 init.sql`)
console.log(`  nx_system_file_info : ${fileRows.length} 条（id 1..${fileRows.length}）`)
console.log(`  菜谱 fileId         : ${recipeNames.length} 条`)
console.log(`  食材 fileId         : ${materialNames.length} 条`)
