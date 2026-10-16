/**
 * 种子数据配图生成脚本
 * ------------------------------------------------------------
 * 为 sql/init.sql 中的每道菜谱、每种食材生成一张 SVG 占位配图，
 * 输出到 backend/uploads/（该目录即 file.upload-path 指向的位置）。
 *
 * 为什么用 SVG：
 *   1. 纯文本、体积小（每张约 1KB），适合提交进 Git 仓库；
 *   2. 无需任何图片处理库即可生成，零依赖；
 *   3. 后端 NxSystemFileInfoController.guessContentType() 已支持
 *      svg -> image/svg+xml，浏览器 <img> 可直接渲染。
 *
 * 用法：node sql/gen-seed-images.mjs
 */
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT_DIR = join(__dirname, '..', 'backend', 'uploads')

/* ---------------- 配色：按分类/食材类型分组，视觉可区分 ---------------- */

// 菜谱：按 classifyId 决定主色（与 classify_info 一一对应）
const CLASSIFY_THEME = {
  1: { name: '家常菜', from: '#ff9a56', to: '#ff6b35' },
  2: { name: '汤羹', from: '#5ec8d8', to: '#2e9db3' },
  3: { name: '甜品', from: '#ff9ec4', to: '#f0629b' },
  4: { name: '川湘菜', from: '#ff6b5e', to: '#d62828' },
  5: { name: '面点主食', from: '#e8b45a', to: '#c98b2e' },
  6: { name: '凉菜', from: '#7dc96b', to: '#4a9e3f' },
}

// 食材：按主色分组，保证 28 种食材各有辨识度
const MATERIAL_THEME = {
  蔬菜: { from: '#8fd46a', to: '#4c9c3a' },
  肉禽: { from: '#f08a7a', to: '#c94f3d' },
  水产: { from: '#68c6e0', to: '#2b8fb0' },
  蛋豆: { from: '#f2cf72', to: '#d3a52f' },
  主食: { from: '#dcb887', to: '#b58a4d' },
  水果: { from: '#ffb14e', to: '#ef8b1f' },
  菌藻: { from: '#a98cd6', to: '#7b5cb8' },
}

/* ---------------- 数据清单（与 init.sql 保持一致） ---------------- */

const RECIPES = [
  // 家常菜 (1)
  ['可乐鸡翅', 1], ['红烧肉', 1], ['土豆炖牛肉', 1], ['番茄炒蛋', 1], ['青椒炒肉丝', 1],
  ['干煸豆角', 1], ['麻婆豆腐', 1], ['香菇滑鸡', 1], ['冬瓜排骨汤', 1],
  // 汤羹 (2)
  ['老鸭汤', 2], ['番茄鸡蛋汤', 2], ['莲藕排骨汤', 2], ['菌菇豆腐羹', 2],
  // 甜品 (3)
  ['芒果糯米饭', 3], ['双皮奶', 3], ['银耳莲子羹', 3],
  // 川湘菜 (4)
  ['水煮肉片', 4], ['剁椒鱼头', 4], ['辣子鸡', 4], ['回锅肉', 4],
  // 面点主食 (5)
  ['手擀面', 5], ['葱油饼', 5], ['扬州炒饭', 5], ['番茄牛腩面', 5],
  // 凉菜 (6)
  ['凉拌黄瓜', 6], ['凉拌木耳', 6], ['口水鸡', 6], ['蒜泥白肉', 6],
]

const MATERIALS = [
  ['土豆', '蔬菜'], ['大虾', '水产'], ['山药', '蔬菜'], ['牛肉', '肉禽'], ['白菜', '蔬菜'],
  ['羊肉', '肉禽'], ['茄子', '蔬菜'], ['鱼', '水产'], ['鲍鱼', '水产'], ['鸡翅', '肉禽'],
  ['五花肉', '肉禽'], ['番茄', '蔬菜'], ['鸡蛋', '蛋豆'], ['豆腐', '蛋豆'], ['青椒', '蔬菜'],
  ['豆角', '蔬菜'], ['香菇', '菌藻'], ['冬瓜', '蔬菜'], ['莲藕', '蔬菜'], ['排骨', '肉禽'],
  ['面粉', '主食'], ['糯米', '主食'], ['芒果', '水果'], ['牛奶', '蛋豆'], ['黄瓜', '蔬菜'],
  ['木耳', '菌藻'], ['辣椒', '蔬菜'], ['豆芽', '蔬菜'],
]

/* ---------------- 图标：为常见食材画简单剪影，增强辨识度 ---------------- */

const ICON = {
  // 圆润果实/根茎类
  round: (c) => `<circle cx="200" cy="180" r="78" fill="${c}" opacity="0.95"/>`,
  // 叶菜类
  leaf: (c) => `
    <path d="M200 108 C258 118 292 168 286 224 C280 274 244 300 200 300
             C156 300 120 274 114 224 C108 168 142 118 200 108 Z" fill="${c}" opacity="0.95"/>
    <path d="M200 120 L200 292" stroke="#ffffff" stroke-width="5" opacity="0.55"/>
    <path d="M200 170 L152 214 M200 210 L248 252" stroke="#ffffff" stroke-width="4" opacity="0.45"/>`,
  // 鱼/水产
  fish: (c) => `
    <path d="M116 200 C150 150 214 138 264 172 C286 187 296 200 296 200
             C296 200 286 213 264 228 C214 262 150 250 116 200 Z" fill="${c}" opacity="0.95"/>
    <path d="M116 200 L74 160 L82 200 L74 240 Z" fill="${c}" opacity="0.8"/>
    <circle cx="248" cy="186" r="7" fill="#ffffff" opacity="0.9"/>`,
  // 肉块
  meat: (c) => `
    <path d="M124 232 C110 190 140 142 196 134 C252 126 296 158 292 206
             C288 250 246 276 196 274 C156 272 134 260 124 232 Z" fill="${c}" opacity="0.95"/>
    <path d="M162 168 C190 156 236 162 254 186" stroke="#ffffff" stroke-width="6"
          fill="none" opacity="0.5" stroke-linecap="round"/>`,
  // 蛋
  egg: (c) => `<ellipse cx="200" cy="200" rx="70" ry="88" fill="${c}" opacity="0.95"/>
              <ellipse cx="176" cy="168" rx="20" ry="26" fill="#ffffff" opacity="0.45"/>`,
  // 主食/面点
  grain: (c) => `
    <path d="M116 236 C140 168 200 132 268 140 C288 142 296 158 288 176
             C262 232 196 268 132 262 C116 260 110 250 116 236 Z" fill="${c}" opacity="0.95"/>
    <path d="M150 226 C186 198 224 176 262 164" stroke="#ffffff" stroke-width="5"
          fill="none" opacity="0.5" stroke-linecap="round"/>`,
  // 菌菇
  mushroom: (c) => `
    <path d="M104 196 C104 140 148 106 200 106 C252 106 296 140 296 196 Z" fill="${c}" opacity="0.95"/>
    <rect x="176" y="196" width="48" height="82" rx="18" fill="#ffffff" opacity="0.75"/>`,
  // 水果
  fruit: (c) => `
    <circle cx="200" cy="212" r="76" fill="${c}" opacity="0.95"/>
    <path d="M200 138 C206 116 220 104 240 100" stroke="#4c9c3a" stroke-width="8"
          fill="none" stroke-linecap="round"/>
    <path d="M200 150 C184 128 156 126 142 138 C160 152 182 154 200 150 Z" fill="#4c9c3a" opacity="0.9"/>`,
  // 面条（菜谱：面类）
  noodle: (c) => `
    <path d="M110 172 C160 138 240 138 290 172" stroke="${c}" stroke-width="16"
          fill="none" stroke-linecap="round" opacity="0.95"/>
    <path d="M110 208 C160 174 240 174 290 208" stroke="${c}" stroke-width="16"
          fill="none" stroke-linecap="round" opacity="0.8"/>
    <path d="M110 244 C160 210 240 210 290 244" stroke="${c}" stroke-width="16"
          fill="none" stroke-linecap="round" opacity="0.65"/>`,
  // 汤碗
  soup: (c) => `
    <path d="M104 178 L296 178 C296 250 258 292 200 292 C142 292 104 250 104 178 Z" fill="${c}" opacity="0.95"/>
    <ellipse cx="200" cy="178" rx="96" ry="24" fill="#ffffff" opacity="0.35"/>
    <path d="M158 128 C150 108 166 100 160 82" stroke="#ffffff" stroke-width="6"
          fill="none" opacity="0.6" stroke-linecap="round"/>
    <path d="M200 124 C192 104 208 96 202 78" stroke="#ffffff" stroke-width="6"
          fill="none" opacity="0.6" stroke-linecap="round"/>
    <path d="M242 128 C234 108 250 100 244 82" stroke="#ffffff" stroke-width="6"
          fill="none" opacity="0.6" stroke-linecap="round"/>`,
  // 甜品杯
  dessert: (c) => `
    <path d="M132 160 L268 160 L246 284 C244 292 236 296 228 296 L172 296
             C164 296 156 292 154 284 Z" fill="${c}" opacity="0.95"/>
    <ellipse cx="200" cy="160" rx="68" ry="18" fill="#ffffff" opacity="0.4"/>
    <circle cx="182" cy="124" r="14" fill="#ffffff" opacity="0.75"/>
    <circle cx="218" cy="132" r="10" fill="#ffffff" opacity="0.6"/>`,
  // 辣椒（川湘菜）
  chili: (c) => `
    <path d="M150 132 C186 122 224 132 246 158 C274 192 276 240 250 264
             C228 284 196 282 180 258 C162 232 158 178 150 132 Z" fill="${c}" opacity="0.95"/>
    <path d="M150 132 C140 118 146 104 160 100" stroke="#4c9c3a" stroke-width="10"
          fill="none" stroke-linecap="round"/>`,
  // 饼
  pancake: (c) => `
    <ellipse cx="200" cy="204" rx="98" ry="72" fill="${c}" opacity="0.95"/>
    <ellipse cx="200" cy="190" rx="98" ry="72" fill="${c}"/>
    <ellipse cx="200" cy="184" rx="72" ry="48" fill="#ffffff" opacity="0.18"/>
    <circle cx="170" cy="176" r="6" fill="#4c9c3a" opacity="0.8"/>
    <circle cx="228" cy="200" r="6" fill="#4c9c3a" opacity="0.8"/>
    <circle cx="200" cy="152" r="5" fill="#4c9c3a" opacity="0.7"/>`,
  // 米饭/炒饭
  rice: (c) => `
    <path d="M112 200 C112 156 152 132 200 132 C248 132 288 156 288 200 Z" fill="${c}" opacity="0.95"/>
    <path d="M104 200 L296 200 C296 252 256 288 200 288 C144 288 104 252 104 200 Z"
          fill="#ffffff" opacity="0.85"/>
    <circle cx="176" cy="166" r="7" fill="#ff9a56" opacity="0.9"/>
    <circle cx="216" cy="158" r="6" fill="#7dc96b" opacity="0.9"/>
    <circle cx="242" cy="176" r="6" fill="#ff6b5e" opacity="0.9"/>`,
  // 凉拌盘
  salad: (c) => `
    <circle cx="200" cy="206" r="94" fill="#ffffff" opacity="0.9"/>
    <circle cx="200" cy="206" r="80" fill="${c}" opacity="0.3"/>
    <path d="M156 200 C176 178 208 174 232 190" stroke="${c}" stroke-width="12"
          fill="none" stroke-linecap="round" opacity="0.95"/>
    <path d="M172 228 C196 210 224 210 244 224" stroke="${c}" stroke-width="10"
          fill="none" stroke-linecap="round" opacity="0.75"/>
    <circle cx="238" cy="180" r="9" fill="#d62828" opacity="0.85"/>`,
}

/* ---------------- 每张图选用哪个图标 ---------------- */

function iconForRecipe(name, classifyId) {
  if (/汤|羹/.test(name)) return 'soup'
  if (/面/.test(name)) return 'noodle'
  if (/炒饭|糯米饭/.test(name)) return 'rice'
  if (/饼/.test(name)) return 'pancake'
  if (/双皮奶|银耳/.test(name)) return 'dessert'
  if (/凉拌|口水鸡|蒜泥/.test(name)) return 'salad'
  if (/辣子鸡|剁椒|水煮/.test(name)) return 'chili'
  if (classifyId === 4) return 'chili'
  if (classifyId === 3) return 'dessert'
  if (classifyId === 6) return 'salad'
  return 'meat'
}

function iconForMaterial(name, group) {
  if (/鱼|虾|鲍/.test(name)) return 'fish'
  if (/香菇|木耳/.test(name)) return 'mushroom'
  if (/芒果/.test(name)) return 'fruit'
  if (/面粉|糯米/.test(name)) return 'grain'
  if (/牛奶/.test(name)) return 'dessert'
  if (/鸡蛋/.test(name)) return 'egg'
  if (/豆腐/.test(name)) return 'round'
  if (/白菜|青椒|豆角|豆芽|辣椒|黄瓜|茄子|番茄|冬瓜|莲藕|山药|土豆/.test(name)) return 'leaf'
  if (group === '肉禽') return 'meat'
  if (group === '水产') return 'fish'
  if (group === '主食') return 'grain'
  if (group === '水果') return 'fruit'
  return 'round'
}

/* ---------------- SVG 模板 ---------------- */

function makeSvg({ title, subtitle, from, to, icon, seed }) {
  // 用名称做种子，让小装饰的位置稳定可复现
  let h = 0
  for (const ch of title) h = (h * 31 + ch.codePointAt(0)) >>> 0
  const dots = Array.from({ length: 6 }, (_, i) => {
    const a = ((h >> (i * 3)) % 360) * (Math.PI / 180)
    const r = 70 + ((h >> (i * 2)) % 60)
    const x = 200 + Math.cos(a) * r * 1.35
    const y = 200 + Math.sin(a) * r * 0.9
    const rad = 5 + ((h >> i) % 7)
    return `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${rad}" fill="#ffffff" opacity="0.14"/>`
  }).join('')

  // 标题字号：长名字自动缩小，避免溢出
  const len = [...title].length
  const fs = len <= 3 ? 38 : len <= 4 ? 34 : len <= 5 ? 30 : 26

  return `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400" viewBox="0 0 400 400" role="img" aria-label="${esc(title)}">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="${from}"/>
      <stop offset="100%" stop-color="${to}"/>
    </linearGradient>
  </defs>
  <rect width="400" height="400" fill="url(#bg)"/>
  ${dots}
  <circle cx="200" cy="178" r="118" fill="#ffffff" opacity="0.13"/>
  ${ICON[icon](to)}
  <rect x="0" y="304" width="400" height="96" fill="#000000" opacity="0.32"/>
  <text x="200" y="352" text-anchor="middle" font-family="'Microsoft YaHei','PingFang SC','Noto Sans CJK SC',sans-serif"
        font-size="${fs}" font-weight="700" fill="#ffffff">${esc(title)}</text>
  <text x="200" y="380" text-anchor="middle" font-family="'Microsoft YaHei','PingFang SC','Noto Sans CJK SC',sans-serif"
        font-size="15" fill="#ffffff" opacity="0.8">${esc(subtitle)}</text>
</svg>
`
}

function esc(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;' })[c])
}

/* ---------------- 生成 ---------------- */

mkdirSync(OUT_DIR, { recursive: true })

const files = [] // { fileName, originName }

for (const [name, classifyId] of RECIPES) {
  const theme = CLASSIFY_THEME[classifyId]
  const fileName = `seed-recipe-${slug(name)}.svg`
  const svg = makeSvg({
    title: name,
    subtitle: theme.name,
    from: theme.from,
    to: theme.to,
    icon: iconForRecipe(name, classifyId),
    seed: name,
  })
  writeFileSync(join(OUT_DIR, fileName), svg, 'utf8')
  files.push({ fileName, originName: `${name}.svg`, kind: 'recipe', name })
}

for (const [name, group] of MATERIALS) {
  const theme = MATERIAL_THEME[group]
  const fileName = `seed-material-${slug(name)}.svg`
  const svg = makeSvg({
    title: name,
    subtitle: `食材 · ${group}`,
    from: theme.from,
    to: theme.to,
    icon: iconForMaterial(name, group),
    seed: name,
  })
  writeFileSync(join(OUT_DIR, fileName), svg, 'utf8')
  files.push({ fileName, originName: `${name}.svg`, kind: 'material', name })
}

/** 生成安全的文件名片段（保留中文，替换非法字符） */
function slug(s) {
  return s.replace(/[\\/:*?"<>|\s]/g, '_')
}

writeFileSync(join(OUT_DIR, '_manifest.json'), JSON.stringify(files, null, 2), 'utf8')

console.log(`生成完成：${files.length} 张（菜谱 ${RECIPES.length} + 食材 ${MATERIALS.length}）`)
console.log(`输出目录：${OUT_DIR}`)
console.log('\n下一步：node sql/apply-seed-images.mjs   # 把 fileId 写回 init.sql')
