/**
 * 方案 C：盘装造型插画生成脚本
 * ------------------------------------------------------------
 * 相比 gen-seed-images.mjs 的「图标 + 文字」，本脚本为每道菜绘制
 * 更接近实体菜的矢量插画：盘子/碗/杯 + 食材堆叠 + 汤汁/点缀。
 *
 * 输出：backend/uploads/ 下的 seed2-*.svg
 * 之后由 apply-seed-images.mjs 绑定到数据库。
 *
 * 用法：node sql/gen-dish-art.mjs
 */
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT = join(__dirname, '..', 'backend', 'uploads')

/* ================= 调色板 ================= */

const C = {
  红烧酱: '#7a3b18', 深酱: '#5c2a10', 肉褐: '#a9542a', 亮肉: '#c9703c',
  番茄红: '#d93b2b', 亮红: '#ec5a43', 辣红: '#b8231c',
  蛋黄: '#f5c542', 蛋白: '#fdf6e3', 葱绿: '#4f9d3a', 深绿: '#37702a',
  菜绿: '#77b84e', 浅菜: '#9ed46f',
  汤清: '#e8c98a', 汤白: '#f2e6cf', 面黄: '#eccf8f', 米白: '#fbf7ee',
  豆腐白: '#f7f2e6', 菇褐: '#8a6a45',
  盘白: '#ffffff', 盘边: '#e6e6e6', 盘影: 'rgba(0,0,0,0.13)',
  甜品粉: '#f7a8c4', 紫: '#9b6fc0',
}

/* ================= 通用部件 ================= */

/** 盘底阴影 */
const plateShadow = () => `<ellipse cx="200" cy="300" rx="150" ry="26" fill="${C.盘影}"/>`

/** 白色圆盘（俯视） */
const roundPlate = (cx = 200, cy = 210, r = 138) => `
  <ellipse cx="${cx}" cy="${cy + 8}" rx="${r}" ry="${r * 0.78}" fill="${C.盘影}"/>
  <ellipse cx="${cx}" cy="${cy}" rx="${r}" ry="${r * 0.78}" fill="${C.盘白}"/>
  <ellipse cx="${cx}" cy="${cy}" rx="${r - 16}" ry="${r * 0.78 - 13}" fill="#fafafa"/>
  <ellipse cx="${cx}" cy="${cy}" rx="${r - 30}" ry="${r * 0.78 - 24}" fill="#f4f4f4"/>`

/** 深色汤碗（侧视） */
const bowl = (soup, cx = 200, cy = 214, w = 132) => `
  <ellipse cx="${cx}" cy="${cy + 74}" rx="${w + 10}" ry="20" fill="${C.盘影}"/>
  <path d="M${cx - w} ${cy - 20} L${cx + w} ${cy - 20}
           C${cx + w} ${cy + 78} ${cx + 62} ${cy + 108} ${cx} ${cy + 108}
           C${cx - 62} ${cy + 108} ${cx - w} ${cy + 78} ${cx - w} ${cy - 20} Z"
        fill="#fbfbfb"/>
  <ellipse cx="${cx}" cy="${cy - 20}" rx="${w}" ry="26" fill="#ffffff"/>
  <ellipse cx="${cx}" cy="${cy - 18}" rx="${w - 12}" ry="20" fill="${soup}"/>`

/** 甜品杯 */
const cup = (fill, cx = 200) => `
  <ellipse cx="${cx}" cy="300" rx="76" ry="16" fill="${C.盘影}"/>
  <path d="M136 150 L264 150 L246 286 C244 294 236 298 228 298
           L172 298 C164 298 156 294 154 286 Z" fill="#fbfbfb"/>
  <path d="M142 176 L258 176 L246 286 C244 294 236 298 228 298
           L172 298 C164 298 156 294 154 286 Z" fill="${fill}"/>
  <ellipse cx="${cx}" cy="150" rx="64" ry="18" fill="#ffffff"/>`

/** 圆润色块（用于食材堆叠） */
const blob = (cx, cy, rx, ry, fill, rot = 0, op = 1) =>
  `<ellipse cx="${cx}" cy="${cy}" rx="${rx}" ry="${ry}" fill="${fill}" opacity="${op}" transform="rotate(${rot} ${cx} ${cy})"/>`

/** 亮面高光 */
const gloss = (cx, cy, rx, ry) =>
  `<ellipse cx="${cx}" cy="${cy}" rx="${rx}" ry="${ry}" fill="#ffffff" opacity="0.3"/>`

/* ================= 每道菜的画法 ================= */

/**
 * 每个绘制函数返回 SVG 片段。
 * 约定：画布 400x400，主体居中偏上，底部留出标题条（y>=304）。
 */
const ART = {
  /* ---- 红烧肉：方块肉堆叠 + 酱汁 ---- */
  红烧肉: () => `
    ${plateShadow()}${roundPlate()}
    <ellipse cx="200" cy="212" rx="104" ry="66" fill="${C.深酱}" opacity="0.55"/>
    ${[
      [156, 196, -8], [200, 186, 4], [244, 198, -5],
      [176, 228, 6], [220, 226, -3], [198, 250, 2],
    ].map(([x, y, r], i) => `
      <rect x="${x - 30}" y="${y - 21}" width="60" height="42" rx="9"
            fill="${i % 2 ? C.肉褐 : C.亮肉}" transform="rotate(${r} ${x} ${y})"/>
      <rect x="${x - 22}" y="${y - 14}" width="34" height="13" rx="6"
            fill="${C.蛋白}" opacity="0.62" transform="rotate(${r} ${x} ${y})"/>`).join('')}
    ${blob(150, 168, 15, 8, C.葱绿, -20)}${blob(252, 172, 13, 7, C.葱绿, 15)}`,

  /* ---- 可乐鸡翅：成对鸡翅 + 光泽 ---- */
  可乐鸡翅: () => `
    ${plateShadow()}${roundPlate()}
    <ellipse cx="200" cy="214" rx="100" ry="62" fill="${C.深酱}" opacity="0.5"/>
    ${[[160, 200, -18], [236, 196, 16], [198, 246, 2], [168, 254, -10], [236, 250, 12]]
      .map(([x, y, r]) => `
      <path d="M${x - 34} ${y} C${x - 30} ${y - 26} ${x + 30} ${y - 26} ${x + 34} ${y}
               C${x + 34} ${y + 22} ${x - 34} ${y + 22} ${x - 34} ${y} Z"
            fill="${C.肉褐}" transform="rotate(${r} ${x} ${y})"/>
      <path d="M${x - 24} ${y - 8} C${x - 20} ${y - 18} ${x + 8} ${y - 18} ${x + 12} ${y - 8}"
            stroke="${C.亮肉}" stroke-width="7" fill="none" stroke-linecap="round"
            transform="rotate(${r} ${x} ${y})"/>`).join('')}
    ${gloss(178, 186, 40, 14)}`,

  /* ---- 土豆炖牛肉：肉块 + 土豆块 ---- */
  土豆炖牛肉: () => `
    ${plateShadow()}${roundPlate()}
    <ellipse cx="200" cy="216" rx="108" ry="64" fill="${C.深酱}" opacity="0.5"/>
    ${[[152, 200], [214, 190], [252, 220], [180, 244], [240, 254]]
      .map(([x, y]) => `<rect x="${x - 26}" y="${y - 19}" width="52" height="38" rx="8"
            fill="${C.肉褐}"/>`).join('')}
    ${[[186, 214], [226, 208], [160, 250], [212, 262]]
      .map(([x, y]) => `<rect x="${x - 22}" y="${y - 17}" width="44" height="34" rx="12"
            fill="${C.蛋黄}"/><rect x="${x - 15}" y="${y - 10}" width="26" height="10" rx="5"
            fill="#ffffff" opacity="0.45"/>`).join('')}
    ${blob(140, 176, 14, 7, C.葱绿, -15)}`,

  /* ---- 番茄炒蛋：红黄交错 ---- */
  番茄炒蛋: () => `
    ${plateShadow()}${roundPlate()}
    ${[[164, 196, -12], [238, 204, 18], [200, 244, 5]]
      .map(([x, y, r]) => `<path d="M${x - 40} ${y} C${x - 34} ${y - 30} ${x + 34} ${y - 30} ${x + 40} ${y}
        C${x + 40} ${y + 26} ${x - 40} ${y + 26} ${x - 40} ${y} Z"
        fill="${C.番茄红}" transform="rotate(${r} ${x} ${y})"/>`).join('')}
    ${[[200, 190], [166, 240], [244, 250], [206, 276]]
      .map(([x, y]) => `<ellipse cx="${x}" cy="${y}" rx="34" ry="24" fill="${C.蛋黄}"/>
        <ellipse cx="${x - 8}" cy="${y - 8}" rx="14" ry="8" fill="#ffffff" opacity="0.35"/>`).join('')}
    ${blob(152, 168, 16, 7, C.葱绿, -18)}${blob(250, 176, 13, 6, C.葱绿, 12)}`,

  /* ---- 青椒炒肉丝：绿色长条 + 肉丝 ---- */
  青椒炒肉丝: () => `
    ${plateShadow()}${roundPlate()}
    ${[[168, 194, -22], [232, 200, 18], [196, 238, -8], [172, 262, 20], [238, 254, -16]]
      .map(([x, y, r]) => `<rect x="${x - 46}" y="${y - 11}" width="92" height="22" rx="11"
        fill="${C.菜绿}" transform="rotate(${r} ${x} ${y})"/>
        <rect x="${x - 38}" y="${y - 6}" width="60" height="7" rx="3"
        fill="${C.浅菜}" transform="rotate(${r} ${x} ${y})"/>`).join('')}
    ${[[200, 214, 8], [156, 230, -14], [248, 232, 12]]
      .map(([x, y, r]) => `<rect x="${x - 40}" y="${y - 7}" width="80" height="14" rx="7"
        fill="${C.亮肉}" transform="rotate(${r} ${x} ${y})"/>`).join('')}`,

  /* ---- 干煸豆角：长豆角条 ---- */
  干煸豆角: () => `
    ${plateShadow()}${roundPlate()}
    ${[[160, 196, -30], [224, 190, 22], [196, 232, -6], [162, 258, 28], [238, 252, -20], [200, 276, 4]]
      .map(([x, y, r]) => `<rect x="${x - 50}" y="${y - 10}" width="100" height="20" rx="10"
        fill="${C.深绿}" transform="rotate(${r} ${x} ${y})"/>
        <rect x="${x - 42}" y="${y - 5}" width="62" height="7" rx="3"
        fill="${C.菜绿}" opacity="0.85" transform="rotate(${r} ${x} ${y})"/>`).join('')}
    ${[[176, 168], [236, 176], [206, 208]].map(([x, y]) =>
      `<circle cx="${x}" cy="${y}" r="6" fill="${C.辣红}"/>`).join('')}
    ${blob(216, 168, 12, 5, C.菇褐, 10)}`,

  /* ---- 麻婆豆腐：白豆腐块 + 红油 ---- */
  麻婆豆腐: () => `
    ${plateShadow()}${roundPlate()}
    <ellipse cx="200" cy="216" rx="104" ry="62" fill="${C.辣红}" opacity="0.55"/>
    ${[[152, 198, -6], [216, 190, 5], [252, 224, -8], [176, 242, 7], [232, 254, -4], [196, 266, 3]]
      .map(([x, y, r]) => `<rect x="${x - 25}" y="${y - 19}" width="50" height="38" rx="6"
        fill="${C.豆腐白}" transform="rotate(${r} ${x} ${y})"/>
        <rect x="${x - 17}" y="${y - 11}" width="30" height="11" rx="4"
        fill="#ffffff" opacity="0.6" transform="rotate(${r} ${x} ${y})"/>`).join('')}
    ${[[168, 176], [244, 190], [204, 172]].map(([x, y]) =>
      `<circle cx="${x}" cy="${y}" r="4" fill="${C.辣红}"/>`).join('')}
    ${blob(180, 290, 20, 5, C.葱绿, -8)}${blob(238, 286, 14, 4, C.葱绿, 10)}`,

  /* ---- 香菇滑鸡：鸡肉块 + 香菇 ---- */
  香菇滑鸡: () => `
    ${plateShadow()}${roundPlate()}
    <ellipse cx="200" cy="216" rx="102" ry="60" fill="${C.深酱}" opacity="0.42"/>
    ${[[162, 200], [232, 196], [196, 240], [166, 258], [240, 252]]
      .map(([x, y]) => `<path d="M${x - 30} ${y} C${x - 26} ${y - 24} ${x + 26} ${y - 24} ${x + 30} ${y}
        C${x + 30} ${y + 20} ${x - 30} ${y + 20} ${x - 30} ${y} Z" fill="${C.亮肉}"/>`).join('')}
    ${[[196, 190], [248, 226], [148, 234]]
      .map(([x, y]) => `<path d="M${x - 32} ${y} C${x - 32} ${y - 26} ${x + 32} ${y - 26} ${x + 32} ${y} Z"
        fill="${C.菇褐}"/><rect x="${x - 11}" y="${y}" width="22" height="24" rx="9"
        fill="${C.汤白}"/>`).join('')}
    ${blob(214, 288, 18, 5, C.葱绿, -6)}`,

  /* ---- 老鸭汤 / 冬瓜排骨汤 / 莲藕排骨汤 等：碗装汤 ---- */
  老鸭汤: () => `
    ${bowl(C.汤清)}
    ${blob(176, 196, 34, 20, C.肉褐, -12)}${blob(230, 204, 30, 18, C.肉褐, 14)}
    ${blob(200, 226, 26, 15, C.蛋黄, 4)}
    ${blob(158, 178, 20, 9, C.葱绿, -18)}${blob(246, 182, 16, 8, C.葱绿, 12)}
    <ellipse cx="200" cy="176" rx="86" ry="14" fill="#ffffff" opacity="0.22"/>`,

  冬瓜排骨汤: () => `
    ${bowl(C.汤白)}
    ${[[166, 198], [238, 202], [200, 224]]
      .map(([x, y]) => `<rect x="${x - 30}" y="${y - 20}" width="60" height="40" rx="10"
        fill="#eef7ea"/>`).join('')}
    ${[[196, 186], [152, 228], [248, 232]]
      .map(([x, y]) => `<rect x="${x - 28}" y="${y - 13}" width="56" height="26" rx="8"
        fill="${C.肉褐}"/>`).join('')}
    ${blob(214, 168, 22, 8, C.葱绿, -10)}`,

  番茄鸡蛋汤: () => `
    ${bowl(C.番茄红)}
    ${[[170, 200, -10], [236, 208, 12]].map(([x, y, r]) =>
      `<path d="M${x - 32} ${y} C${x - 26} ${y - 24} ${x + 26} ${y - 24} ${x + 32} ${y}
        C${x + 32} ${y + 20} ${x - 32} ${y + 20} ${x - 32} ${y} Z"
        fill="${C.亮红}" transform="rotate(${r} ${x} ${y})"/>`).join('')}
    ${[[200, 182], [206, 232], [166, 240]].map(([x, y]) =>
      `<ellipse cx="${x}" cy="${y}" rx="30" ry="16" fill="${C.蛋黄}" opacity="0.95"/>`).join('')}
    ${blob(154, 176, 18, 7, C.葱绿, -14)}`,

  莲藕排骨汤: () => `
    ${bowl(C.汤白)}
    ${[[172, 202], [234, 206], [202, 230]].map(([x, y]) =>
      `<circle cx="${x}" cy="${y}" r="26" fill="#f3e3cd"/>
       <circle cx="${x}" cy="${y}" r="9" fill="#e2c9a6"/>
       <circle cx="${x - 14}" cy="${y - 9}" r="6" fill="#e2c9a6"/>
       <circle cx="${x + 14}" cy="${y + 8}" r="6" fill="#e2c9a6"/>`).join('')}
    ${[[198, 184], [156, 232], [248, 234]].map(([x, y]) =>
      `<rect x="${x - 26}" y="${y - 12}" width="52" height="24" rx="8" fill="${C.肉褐}"/>`).join('')}`,

  菌菇豆腐羹: () => `
    ${bowl(C.汤白)}
    ${[[168, 200], [232, 206]].map(([x, y]) =>
      `<path d="M${x - 28} ${y} C${x - 28} ${y - 22} ${x + 28} ${y - 22} ${x + 28} ${y} Z" fill="${C.菇褐}"/>
       <rect x="${x - 9}" y="${y}" width="18" height="20" rx="8" fill="${C.汤白}"/>`).join('')}
    ${[[200, 224], [170, 240], [234, 238]].map(([x, y]) =>
      `<rect x="${x - 22}" y="${y - 15}" width="44" height="30" rx="6" fill="${C.豆腐白}"/>`).join('')}
    <ellipse cx="200" cy="176" rx="80" ry="12" fill="#ffffff" opacity="0.25"/>`,

  /* ---- 甜品 ---- */
  芒果糯米饭: () => `
    ${plateShadow()}${roundPlate(200, 214, 132)}
    <path d="M140 210 C140 172 190 158 214 176 C244 198 240 244 200 252
             C164 258 140 240 140 210 Z" fill="${C.米白}"/>
    <path d="M146 208 C146 178 188 166 208 180" stroke="#f0e8d8" stroke-width="5" fill="none"/>
    ${[[262, 186, 26], [286, 226, 22], [250, 246, 20]].map(([x, y, r]) =>
      `<ellipse cx="${x}" cy="${y}" rx="${r}" ry="${r * 0.8}" fill="${C.蛋黄}"/>
       <ellipse cx="${x - r * 0.3}" cy="${y - r * 0.3}" rx="${r * 0.4}" ry="${r * 0.3}"
        fill="#ffffff" opacity="0.4"/>`).join('')}
    <path d="M186 168 C196 152 216 148 228 158" stroke="${C.菜绿}" stroke-width="6"
          fill="none" stroke-linecap="round"/>`,

  双皮奶: () => `
    ${cup(C.蛋白)}
    <ellipse cx="200" cy="150" rx="64" ry="18" fill="#fffdf6"/>
    <ellipse cx="200" cy="150" rx="46" ry="12" fill="#f7efd9"/>
    ${blob(232, 134, 15, 9, C.甜品粉, -12)}${blob(176, 130, 11, 7, C.蛋黄, 8)}`,

  银耳莲子羹: () => `
    ${bowl('#f6ead6')}
    ${[[170, 198], [232, 204], [200, 224], [156, 228], [246, 230]].map(([x, y]) =>
      `<ellipse cx="${x}" cy="${y}" rx="24" ry="15" fill="#fdf8ee" opacity="0.95"/>
       <ellipse cx="${x - 6}" cy="${y - 5}" rx="10" ry="6" fill="#ffffff" opacity="0.7"/>`).join('')}
    ${[[186, 180], [240, 186], [200, 250]].map(([x, y]) =>
      `<ellipse cx="${x}" cy="${y}" rx="12" ry="9" fill="${C.蛋黄}"/>`).join('')}
    ${blob(158, 176, 14, 7, C.辣红, -10)}`,

  /* ---- 川湘菜 ---- */
  水煮肉片: () => `
    ${bowl(C.辣红, 200, 210, 138)}
    ${[[164, 194], [234, 200], [198, 220]].map(([x, y]) =>
      `<rect x="${x - 38}" y="${y - 14}" width="76" height="28" rx="8"
        fill="${C.肉褐}" opacity="0.95" transform="rotate(-6 ${x} ${y})"/>`).join('')}
    ${[[186, 176], [246, 182], [156, 180], [214, 196]].map(([x, y]) =>
      `<circle cx="${x}" cy="${y}" r="5" fill="${C.辣红}"/>`).join('')}
    ${blob(200, 168, 30, 7, C.辣红, -6)}`,

  剁椒鱼头: () => `
    ${plateShadow()}${roundPlate(200, 216, 140)}
    <path d="M108 226 C138 176 226 158 284 190 C306 202 314 218 314 226
             C314 234 306 250 284 262 C226 294 138 276 108 226 Z" fill="#dfe6ea"/>
    <path d="M108 226 L62 190 L72 226 L62 262 Z" fill="#cfd8de"/>
    <circle cx="272" cy="212" r="7" fill="#37474f"/>
    ${[[168, 200], [206, 196], [242, 206], [186, 226], [224, 224]].map(([x, y]) =>
      `<ellipse cx="${x}" cy="${y}" rx="18" ry="11" fill="${C.辣红}" opacity="0.92"/>`).join('')}
    ${blob(176, 256, 22, 6, C.葱绿, -8)}`,

  辣子鸡: () => `
    ${plateShadow()}${roundPlate(200, 214, 136)}
    ${[[158, 194], [230, 190], [196, 232], [160, 252], [240, 250], [200, 272]]
      .map(([x, y]) => `<rect x="${x - 22}" y="${y - 16}" width="44" height="32" rx="8"
        fill="${C.肉褐}"/>`).join('')}
    ${[[130, 210], [268, 206], [176, 168], [246, 166], [150, 274], [254, 272], [206, 158]]
      .map(([x, y]) => `<path d="M${x} ${y - 20} C${x + 8} ${y - 6} ${x + 8} ${y + 8} ${x} ${y + 22}
        C${x - 8} ${y + 8} ${x - 8} ${y - 6} ${x} ${y - 20} Z" fill="${C.辣红}"/>`).join('')}`,

  回锅肉: () => `
    ${plateShadow()}${roundPlate()}
    <ellipse cx="200" cy="216" rx="100" ry="60" fill="${C.辣红}" opacity="0.3"/>
    ${[[160, 198, -14], [232, 196, 12], [196, 236, 4], [164, 256, -8], [238, 252, 10]]
      .map(([x, y, r]) => `<path d="M${x - 40} ${y} C${x - 36} ${y - 22} ${x + 36} ${y - 22} ${x + 40} ${y}
        C${x + 40} ${y + 20} ${x - 40} ${y + 20} ${x - 40} ${y} Z"
        fill="${C.亮肉}" transform="rotate(${r} ${x} ${y})"/>
        <path d="M${x - 30} ${y - 4} C${x - 10} ${y - 12} ${x + 10} ${y - 12} ${x + 30} ${y - 4}"
        stroke="${C.蛋白}" stroke-width="5" fill="none" opacity="0.65"
        transform="rotate(${r} ${x} ${y})"/>`).join('')}
    ${blob(148, 176, 20, 8, C.深绿, -20)}${blob(252, 180, 16, 7, C.深绿, 14)}`,

  /* ---- 面点主食 ---- */
  手擀面: () => `
    ${bowl(C.汤清, 200, 208, 136)}
    ${[0, 1, 2, 3, 4].map((i) => {
      const y = 176 + i * 13
      return `<path d="M118 ${y} C160 ${y - 14} 240 ${y - 14} 282 ${y}" stroke="${C.面黄}"
        stroke-width="11" fill="none" stroke-linecap="round"/>`
    }).join('')}
    ${blob(160, 168, 22, 8, C.葱绿, -12)}${blob(246, 174, 18, 7, C.葱绿, 10)}`,

  葱油饼: () => `
    ${plateShadow()}${roundPlate(200, 216, 140)}
    <ellipse cx="200" cy="216" rx="112" ry="82" fill="#e0b46a"/>
    <ellipse cx="200" cy="208" rx="112" ry="82" fill="#eec479"/>
    ${[0, 1, 2, 3].map((i) => {
      const r = 96 - i * 26
      return `<ellipse cx="200" cy="208" rx="${r}" ry="${r * 0.73}" fill="none"
        stroke="#d9a650" stroke-width="4" opacity="0.8"/>`
    }).join('')}
    <ellipse cx="200" cy="204" rx="34" ry="26" fill="#ffffff" opacity="0.22"/>
    ${[[166, 186], [238, 202], [200, 156], [178, 240]]
      .map(([x, y]) => `<circle cx="${x}" cy="${y}" r="5" fill="${C.深绿}" opacity="0.85"/>`).join('')}`,

  扬州炒饭: () => `
    ${plateShadow()}
    <ellipse cx="200" cy="252" rx="132" ry="46" fill="#fbfbfb"/>
    <ellipse cx="200" cy="246" rx="132" ry="46" fill="#ffffff"/>
    <ellipse cx="200" cy="240" rx="112" ry="36" fill="#f7f7f7"/>
    <path d="M104 226 C104 178 156 152 200 152 C244 152 296 178 296 226 Z" fill="${C.蛋黄}"/>
    ${[[162, 196], [216, 188], [252, 204], [186, 220], [238, 224], [200, 172], [150, 216]]
      .map(([x, y], i) => {
        const cols = [C.菜绿, C.亮红, '#e8894a', C.菜绿, C.亮红, '#e8894a', C.菜绿]
        return `<ellipse cx="${x}" cy="${y}" rx="9" ry="6" fill="${cols[i % cols.length]}"
          transform="rotate(${(i * 37) % 360} ${x} ${y})"/>`
      }).join('')}`,

  番茄牛腩面: () => `
    ${bowl('#e07a5a', 200, 206, 136)}
    ${[0, 1, 2, 3].map((i) => {
      const y = 182 + i * 14
      return `<path d="M120 ${y} C162 ${y - 12} 238 ${y - 12} 280 ${y}" stroke="${C.面黄}"
        stroke-width="10" fill="none" stroke-linecap="round"/>`
    }).join('')}
    ${[[164, 176], [240, 180]].map(([x, y]) =>
      `<rect x="${x - 26}" y="${y - 13}" width="52" height="26" rx="7" fill="${C.肉褐}"/>`).join('')}
    ${blob(200, 168, 24, 8, C.亮红, -8)}${blob(150, 190, 18, 7, C.葱绿, -14)}`,

  /* ---- 凉菜 ---- */
  凉拌黄瓜: () => `
    ${plateShadow()}${roundPlate()}
    ${[[164, 198, -24], [238, 200, 20], [196, 240, -8], [166, 258, 26], [238, 254, -18]]
      .map(([x, y, r]) => `<ellipse cx="${x}" cy="${y}" rx="42" ry="27" fill="${C.浅菜}"
        transform="rotate(${r} ${x} ${y})"/>
        <ellipse cx="${x}" cy="${y}" rx="30" ry="17" fill="#f6f9e8"
        transform="rotate(${r} ${x} ${y})"/>
        <ellipse cx="${x - 6}" cy="${y - 4}" rx="8" ry="4" fill="${C.菜绿}" opacity="0.55"
        transform="rotate(${r} ${x} ${y})"/>`).join('')}
    ${[[182, 176], [246, 182]].map(([x, y]) =>
      `<circle cx="${x}" cy="${y}" r="6" fill="${C.辣红}"/>`).join('')}`,

  凉拌木耳: () => `
    ${plateShadow()}${roundPlate()}
    ${[[152, 198, 20], [214, 188, -16], [252, 216, 12], [176, 240, -18], [232, 252, 16], [196, 268, -8]]
      .map(([x, y, r]) => `<path d="M${x} ${y - 26} C${x + 26} ${y - 18} ${x + 28} ${y + 14} ${x + 4} ${y + 26}
        C${x - 22} ${y + 34} ${x - 34} ${y + 6} ${x - 20} ${y - 14} Z"
        fill="#4a3b35" transform="rotate(${r} ${x} ${y})"/>
        <path d="M${x - 4} ${y - 14} C${x + 10} ${y - 8} ${x + 12} ${y + 8} ${x + 2} ${y + 14}"
        stroke="#6d5a52" stroke-width="4" fill="none"
        transform="rotate(${r} ${x} ${y})"/>`).join('')}
    ${[[188, 172], [248, 194], [162, 226]].map(([x, y]) =>
      `<circle cx="${x}" cy="${y}" r="5" fill="${C.辣红}"/>`).join('')}
    ${blob(216, 286, 18, 5, C.葱绿, -6)}`,

  口水鸡: () => `
    ${plateShadow()}${roundPlate()}
    <ellipse cx="200" cy="218" rx="98" ry="58" fill="${C.辣红}" opacity="0.42"/>
    ${[[164, 200, -18], [236, 198, 14], [198, 240, 3], [166, 256, -10], [238, 254, 12]]
      .map(([x, y, r]) => `<path d="M${x - 38} ${y} C${x - 34} ${y - 24} ${x + 34} ${y - 24} ${x + 38} ${y}
        C${x + 38} ${y + 22} ${x - 38} ${y + 22} ${x - 38} ${y} Z"
        fill="${C.汤白}" transform="rotate(${r} ${x} ${y})"/>
        <path d="M${x - 26} ${y - 6} C${x - 6} ${y - 14} ${x + 10} ${y - 14} ${x + 26} ${y - 6}"
        stroke="${C.辣红}" stroke-width="5" fill="none" opacity="0.75"
        transform="rotate(${r} ${x} ${y})"/>`).join('')}
    ${[[178, 174], [242, 180], [206, 284]].map(([x, y]) =>
      `<circle cx="${x}" cy="${y}" r="5" fill="#c8a15a"/>`).join('')}
    ${blob(152, 288, 18, 5, C.葱绿, -8)}`,

  蒜泥白肉: () => `
    ${plateShadow()}${roundPlate()}
    ${[[172, 196, -6], [228, 202, 8], [200, 228, -4], [176, 252, 6], [230, 250, -8]]
      .map(([x, y, r]) => `<path d="M${x - 44} ${y} C${x - 40} ${y - 20} ${x + 40} ${y - 20} ${x + 44} ${y}
        C${x + 44} ${y + 18} ${x - 44} ${y + 18} ${x - 44} ${y} Z"
        fill="${C.汤白}" transform="rotate(${r} ${x} ${y})"/>
        <path d="M${x - 34} ${y + 6} C${x - 12} ${y + 1} ${x + 12} ${y + 1} ${x + 34} ${y + 6}"
        stroke="${C.肉褐}" stroke-width="4" fill="none" opacity="0.5"
        transform="rotate(${r} ${x} ${y})"/>`).join('')}
    <ellipse cx="200" cy="222" rx="86" ry="50" fill="${C.辣红}" opacity="0.3"/>
    ${blob(158, 172, 22, 7, C.辣红, -14)}${blob(246, 178, 16, 6, C.辣红, 12)}
    ${blob(200, 286, 26, 5, C.葱绿, -4)}`,
}

/* ================= 食材：保留简化画法，但加盘/托底 ================= */

const MAT = {
  叶菜: (c1, c2) => `
    ${plateShadow()}
    <ellipse cx="200" cy="268" rx="118" ry="34" fill="#ffffff"/>
    <path d="M200 108 C258 120 292 168 286 224 C280 274 244 300 200 300
             C156 300 120 274 114 224 C108 168 142 120 200 108 Z" fill="${c1}"/>
    <path d="M200 120 L200 292" stroke="#ffffff" stroke-width="6" opacity="0.5"/>
    <path d="M200 172 L150 216 M200 212 L250 254" stroke="#ffffff" stroke-width="5" opacity="0.42"/>
    <ellipse cx="172" cy="164" rx="26" ry="16" fill="#ffffff" opacity="0.22"/>`,
  肉禽: (c1, c2) => `
    ${plateShadow()}
    <ellipse cx="200" cy="270" rx="122" ry="34" fill="#ffffff"/>
    <path d="M118 232 C104 188 138 136 196 128 C254 120 300 154 296 204
             C292 252 248 280 196 278 C154 276 128 262 118 232 Z" fill="${c1}"/>
    <path d="M158 168 C188 154 236 160 256 186" stroke="#ffffff" stroke-width="7"
          fill="none" opacity="0.45" stroke-linecap="round"/>
    <ellipse cx="164" cy="188" rx="30" ry="18" fill="#ffffff" opacity="0.2"/>`,
  水产: (c1, c2) => `
    ${plateShadow()}
    <ellipse cx="200" cy="272" rx="128" ry="32" fill="#ffffff"/>
    <path d="M110 200 C146 148 214 136 266 172 C288 187 298 200 298 200
             C298 200 288 213 266 228 C214 264 146 252 110 200 Z" fill="${c1}"/>
    <path d="M110 200 L64 158 L74 200 L64 242 Z" fill="${c2}"/>
    <circle cx="250" cy="186" r="8" fill="#ffffff"/>
    <circle cx="250" cy="186" r="4" fill="#2b3a3f"/>
    <path d="M170 178 C186 172 200 172 214 178" stroke="#ffffff" stroke-width="4"
          fill="none" opacity="0.5"/>`,
  蛋豆: (c1, c2) => `
    ${plateShadow()}
    <ellipse cx="200" cy="272" rx="118" ry="32" fill="#ffffff"/>
    <ellipse cx="200" cy="196" rx="74" ry="92" fill="${c1}"/>
    <ellipse cx="176" cy="164" rx="22" ry="28" fill="#ffffff" opacity="0.45"/>
    <ellipse cx="200" cy="196" rx="52" ry="66" fill="#ffffff" opacity="0.12"/>`,
  主食: (c1, c2) => `
    ${plateShadow()}
    <ellipse cx="200" cy="272" rx="126" ry="34" fill="#ffffff"/>
    <path d="M110 238 C136 168 200 130 270 138 C292 140 302 158 292 178
             C264 236 194 272 128 266 C112 264 104 252 110 238 Z" fill="${c1}"/>
    <path d="M146 228 C184 198 224 176 264 164" stroke="#ffffff" stroke-width="6"
          fill="none" opacity="0.45" stroke-linecap="round"/>`,
  水果: (c1, c2) => `
    ${plateShadow()}
    <ellipse cx="200" cy="276" rx="112" ry="32" fill="#ffffff"/>
    <circle cx="200" cy="206" r="78" fill="${c1}"/>
    <ellipse cx="172" cy="178" rx="26" ry="18" fill="#ffffff" opacity="0.35"/>
    <path d="M200 130 C208 108 224 96 246 92" stroke="#4c9c3a" stroke-width="9"
          fill="none" stroke-linecap="round"/>
    <path d="M200 142 C184 120 154 118 140 130 C158 146 182 148 200 142 Z" fill="#4c9c3a"/>`,
  菌藻: (c1, c2) => `
    ${plateShadow()}
    <ellipse cx="200" cy="272" rx="118" ry="32" fill="#ffffff"/>
    <path d="M100 198 C100 138 146 104 200 104 C254 104 300 138 300 198 Z" fill="${c1}"/>
    <rect x="174" y="198" width="52" height="84" rx="20" fill="#ffffff" opacity="0.8"/>
    <ellipse cx="168" cy="152" rx="24" ry="14" fill="#ffffff" opacity="0.25"/>`,
}

/* ================= 菜名 → 画法 映射 ================= */

const RECIPES = [
  ['可乐鸡翅', 1], ['红烧肉', 1], ['土豆炖牛肉', 1], ['番茄炒蛋', 1], ['青椒炒肉丝', 1],
  ['干煸豆角', 1], ['麻婆豆腐', 1], ['香菇滑鸡', 1], ['冬瓜排骨汤', 1],
  ['老鸭汤', 2], ['番茄鸡蛋汤', 2], ['莲藕排骨汤', 2], ['菌菇豆腐羹', 2],
  ['芒果糯米饭', 3], ['双皮奶', 3], ['银耳莲子羹', 3],
  ['水煮肉片', 4], ['剁椒鱼头', 4], ['辣子鸡', 4], ['回锅肉', 4],
  ['手擀面', 5], ['葱油饼', 5], ['扬州炒饭', 5], ['番茄牛腩面', 5],
  ['凉拌黄瓜', 6], ['凉拌木耳', 6], ['口水鸡', 6], ['蒜泥白肉', 6],
]

const CLASSIFY_THEME = {
  1: { name: '家常菜', from: '#ff9a56', to: '#e8722f' },
  2: { name: '汤羹', from: '#63cddd', to: '#2b93ab' },
  3: { name: '甜品', from: '#ffa8c8', to: '#ec6199' },
  4: { name: '川湘菜', from: '#ff7060', to: '#c9201c' },
  5: { name: '面点主食', from: '#eec063', to: '#c8892a' },
  6: { name: '凉菜', from: '#8ed471', to: '#47993c' },
}

const MATERIALS = [
  ['土豆', '叶菜'], ['大虾', '水产'], ['山药', '叶菜'], ['牛肉', '肉禽'], ['白菜', '叶菜'],
  ['羊肉', '肉禽'], ['茄子', '叶菜'], ['鱼', '水产'], ['鲍鱼', '水产'], ['鸡翅', '肉禽'],
  ['五花肉', '肉禽'], ['番茄', '叶菜'], ['鸡蛋', '蛋豆'], ['豆腐', '蛋豆'], ['青椒', '叶菜'],
  ['豆角', '叶菜'], ['香菇', '菌藻'], ['冬瓜', '叶菜'], ['莲藕', '叶菜'], ['排骨', '肉禽'],
  ['面粉', '主食'], ['糯米', '主食'], ['芒果', '水果'], ['牛奶', '蛋豆'], ['黄瓜', '叶菜'],
  ['木耳', '菌藻'], ['辣椒', '叶菜'], ['豆芽', '叶菜'],
]

const MAT_THEME = {
  叶菜: ['#8fd46a', '#4c9c3a'],
  肉禽: ['#e8836f', '#bf4632'],
  水产: ['#6fc9e2', '#2b8fb0'],
  蛋豆: ['#f2cf72', '#cfa02c'],
  主食: ['#dcb887', '#b0824a'],
  水果: ['#ffb14e', '#e8820f'],
  菌藻: ['#a98cd6', '#7150b0'],
}

/* ================= 组装 SVG ================= */

const esc = (s) => String(s).replace(/[&<>"']/g, (c) =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;' })[c])

function makeSvg({ title, subtitle, from, to, body }) {
  let h = 0
  for (const ch of title) h = (h * 31 + ch.codePointAt(0)) >>> 0
  const dots = Array.from({ length: 5 }, (_, i) => {
    const a = ((h >> (i * 3)) % 360) * (Math.PI / 180)
    const r = 88 + ((h >> (i * 2)) % 62)
    return `<circle cx="${(200 + Math.cos(a) * r * 1.3).toFixed(1)}"
              cy="${(176 + Math.sin(a) * r * 0.8).toFixed(1)}"
              r="${4 + ((h >> i) % 6)}" fill="#ffffff" opacity="0.12"/>`
  }).join('')

  const len = [...title].length
  const fs = len <= 3 ? 36 : len <= 4 ? 32 : len <= 5 ? 29 : 25

  return `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400" viewBox="0 0 400 400" role="img" aria-label="${esc(title)}">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="${from}"/>
      <stop offset="100%" stop-color="${to}"/>
    </linearGradient>
  </defs>
  <rect width="400" height="400" fill="url(#bg)"/>
  ${dots}
  ${body}
  <rect x="0" y="306" width="400" height="94" fill="#000000" opacity="0.34"/>
  <text x="200" y="350" text-anchor="middle"
        font-family="'Microsoft YaHei','PingFang SC','Noto Sans CJK SC',sans-serif"
        font-size="${fs}" font-weight="700" fill="#ffffff">${esc(title)}</text>
  <text x="200" y="378" text-anchor="middle"
        font-family="'Microsoft YaHei','PingFang SC','Noto Sans CJK SC',sans-serif"
        font-size="14" fill="#ffffff" opacity="0.85">${esc(subtitle)}</text>
</svg>
`
}

const slug = (s) => s.replace(/[\\/:*?"<>|\s]/g, '_')

mkdirSync(OUT, { recursive: true })

let n = 0
for (const [name, cid] of RECIPES) {
  const t = CLASSIFY_THEME[cid]
  const draw = ART[name]
  if (!draw) { console.error(`缺少画法: ${name}`); process.exit(1) }
  writeFileSync(join(OUT, `seed2-recipe-${slug(name)}.svg`),
    makeSvg({ title: name, subtitle: t.name, from: t.from, to: t.to, body: draw() }), 'utf8')
  n++
}

for (const [name, g] of MATERIALS) {
  const [c1, c2] = MAT_THEME[g]
  writeFileSync(join(OUT, `seed2-material-${slug(name)}.svg`),
    makeSvg({ title: name, subtitle: `食材 · ${g}`, from: c1, to: c2, body: MAT[g](c1, c2) }), 'utf8')
  n++
}

console.log(`方案 C 插画生成完成：${n} 张（菜谱 ${RECIPES.length} + 食材 ${MATERIALS.length}）`)
console.log(`输出目录：${OUT}`)
