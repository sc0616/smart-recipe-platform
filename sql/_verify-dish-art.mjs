/**
 * 校验方案 C 插画：结构合法性 + 常见绘制错误。
 * 重点捕捉我手写 SVG 时容易犯的错：
 *   1. 引用未定义的 JS 变量（会渲染成 "undefined"）
 *   2. NaN / 空属性值
 *   3. 颜色值非法
 *   4. 标签未配平
 *   5. 主体超出 viewBox（被裁切）
 */
import { readFileSync, readdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const UP = join(__dirname, '..', 'backend', 'uploads')

const files = readdirSync(UP).filter((f) => f.startsWith('seed2-') && f.endsWith('.svg'))
const errors = []
let totalPaths = 0

for (const f of files) {
  const s = readFileSync(join(UP, f), 'utf8')

  // 1. undefined / NaN
  if (/undefined/.test(s)) errors.push(`${f}: 含 "undefined"（引用了未定义变量）`)
  if (/NaN/.test(s)) errors.push(`${f}: 含 "NaN"（数值计算出错）`)
  if (/null/.test(s) && !/fill="none"/.test(s)) {
    // null 可能出现在合法文本里，仅在属性中报错
    if (/="null"/.test(s)) errors.push(`${f}: 属性值为 null`)
  }

  // 2. 空属性
  for (const m of s.matchAll(/(\w+)=""/g)) {
    if (!['fill', 'stroke'].includes(m[1])) errors.push(`${f}: 空属性 ${m[1]}=""`)
  }

  // 3. 颜色合法性
  for (const m of s.matchAll(/(?:fill|stroke)="([^"]+)"/g)) {
    const v = m[1]
    const ok = /^#[0-9a-fA-F]{3,8}$/.test(v) || /^rgba?\(/.test(v) || v === 'none' ||
               /^(white|black|currentColor)$/.test(v) || /^url\(#/.test(v)
    if (!ok) errors.push(`${f}: 可疑颜色值 "${v}"`)
  }

  // 4. 标签配平
  for (const tag of ['defs', 'linearGradient', 'text', 'svg']) {
    const o = (s.match(new RegExp('<' + tag + '[\\s>]', 'g')) || []).length
    const c = (s.match(new RegExp('</' + tag + '>', 'g')) || []).length
    if (o !== c) errors.push(`${f}: <${tag}> 未配平 open=${o} close=${c}`)
  }

  // 5. 坐标越界（粗查：path/rect/ellipse 的坐标应在 -20..420）
  for (const m of s.matchAll(/(?:x|cx)="(-?\d+(?:\.\d+)?)"/g)) {
    const v = Number(m[1])
    if (v < -30 || v > 430) errors.push(`${f}: x 坐标越界 ${v}`)
  }
  for (const m of s.matchAll(/cy="(-?\d+(?:\.\d+)?)"/g)) {
    const v = Number(m[1])
    if (v < -30 || v > 470) errors.push(`${f}: y 坐标越界 ${v}`)
  }

  // 6. 必须有可见主体（至少几个图元）
  const shapes = (s.match(/<(path|rect|circle|ellipse)/g) || []).length
  totalPaths += shapes
  if (shapes < 8) errors.push(`${f}: 图元过少（${shapes}），可能画法缺失`)

  // 7. 必须含标题文字
  const label = f.replace(/^seed2-(recipe|material)-/, '').replace(/\.svg$/, '')
  if (!s.includes(`>${label}<`) && !s.includes(label)) {
    errors.push(`${f}: 未包含菜名文字`)
  }
}

console.log(`检查文件      : ${files.length}`)
console.log(`图元总数      : ${totalPaths}（平均 ${(totalPaths / files.length).toFixed(1)}/张）`)
const sizes = files.map((f) => readFileSync(join(UP, f), 'utf8').length)
console.log(`单张体积      : ${Math.min(...sizes)} ~ ${Math.max(...sizes)} 字节`)

if (errors.length === 0) {
  console.log('\n✅ 结构校验通过：无 undefined/NaN，颜色合法，标签配平，坐标在界内。')
} else {
  const uniq = [...new Set(errors)]
  console.log(`\n❌ 发现 ${uniq.length} 类问题：`)
  uniq.slice(0, 40).forEach((e) => console.log('  - ' + e))
  process.exitCode = 1
}
