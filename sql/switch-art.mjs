/**
 * 迁移种子配图的文件名前缀。
 * ------------------------------------------------------------
 * 用法：
 *   node sql/switch-art.mjs seed2   # 统一为方案 C 盘装插画（当前采用）
 *   node sql/switch-art.mjs seed    # 迁移到方案 A 图标式前缀（需先有对应图片）
 *
 * 说明：当前仓库只保留方案 C 的插画（seed2-*.svg，共 56 张），
 *       方案 A 的 old 图已清理。本脚本保留用于「更换整套配图」的场景：
 *       把新图放进 backend/uploads/ 后，用本脚本统一改写 init.sql 中的引用。
 *
 * 原理：把 init.sql 中所有 seed[-2]-recipe-xxx.svg / seed[-2]-material-xxx.svg
 *       的前缀统一替换为目标前缀，并相应更新 nx_system_file_info。
 *       执行前会校验目标文件在磁盘上确实存在，缺失则中止。
 */
import { readFileSync, writeFileSync, readdirSync, existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = join(__dirname, '..')
const UP = join(ROOT, 'backend', 'uploads')
const SQL_PATH = join(__dirname, 'init.sql')

const target = (process.argv[2] || 'seed2').replace(/-$/, '')
const other = target === 'seed2' ? 'seed' : 'seed2'
if (!['seed', 'seed2'].includes(target)) {
  console.error('用法：node sql/switch-art.mjs [seed|seed2]')
  process.exit(1)
}

const prefix = `${target}-`

/* ---------- 1. 先确认目标风格的文件都在磁盘上 ---------- */
const disk = readdirSync(UP).filter((f) => f.endsWith('.svg'))
let sql = readFileSync(SQL_PATH, 'utf8')

// 收集当前 SQL 中引用的所有图片文件名
const referenced = [...sql.matchAll(/(seed2?-(?:recipe|material)-[^'"]+\.svg)/g)].map((m) => m[1])
const unique = [...new Set(referenced)]

const missing = []
for (const old of unique) {
  const bare = old.replace(/^seed2?-/, '')            // recipe-红烧肉.svg
  const next = `${target}-${bare}`
  if (!disk.includes(next)) missing.push(next)
}
if (missing.length) {
  console.error(`目标风格「${target}」缺少 ${missing.length} 个文件，已中止：`)
  missing.slice(0, 10).forEach((m) => console.error('  - ' + m))
  process.exit(1)
}

/* ---------- 2. 替换所有引用 ---------- */
// 交替替换，避免 seed->seed2 之后又被 seed2->seed 影响
const stages = [
  [new RegExp(`${other}-(recipe|material)-`, 'g'), `@@TMP@@$1-`],
  [new RegExp(`${target}-(recipe|material)-`, 'g'), `${target}-$1-`],
  [/@@TMP@@/g, `${target}-`],
]
let out = sql
for (const [re, to] of stages) out = out.replace(re, to)

writeFileSync(SQL_PATH, out, 'utf8')

/* ---------- 3. 报告 ---------- */
const after = [...new Set([...out.matchAll(/(seed2?-(?:recipe|material)-[^'"]+\.svg)/g)].map((m) => m[1]))]
const styleName = target === 'seed2' ? '方案 C：盘装造型插画' : '方案 A：图标式配图'
console.log(`已切换为「${styleName}」`)
console.log(`  init.sql 中的图片引用：${after.length} 个`)
console.log(`  目录中该风格文件    ：${disk.filter((f) => f.startsWith(prefix)).length} 个`)
console.log(`\n下一步：重新导入数据库以生效`)
console.log(`  mysql -uroot -p < sql/init.sql`)
