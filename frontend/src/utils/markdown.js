/**
 * 极简 Markdown 渲染器（面向 AI 对话气泡，无第三方依赖）。
 *
 * 安全性：先对原文做 HTML 转义，再解析 Markdown 语法，
 * 因此模型输出中的 HTML 标签不会被执行；链接也仅允许 http/https。
 */
const escapeHtml = (s) =>
  s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

/** 行内语法：行内代码 → 加粗 → 斜体 → 链接 */
function inline(text) {
  let s = text
  s = s.replace(/`([^`]+)`/g, '<code class="md-code">$1</code>')
  s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  s = s.replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>')
  s = s.replace(
    /\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g,
    '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>'
  )
  return s
}

/**
 * 把 Markdown 文本渲染为安全 HTML。
 * 支持：代码块、行内代码、标题、有序/无序列表、加粗、斜体、链接、换行。
 */
export function renderMarkdown(src) {
  if (!src) return ''

  const lines = escapeHtml(src).split('\n')
  const out = []
  let inCode = false
  let codeBuf = []
  let listType = null
  let para = []

  const flushPara = () => {
    if (para.length) {
      out.push(`<p>${para.join('<br>')}</p>`)
      para = []
    }
  }
  const closeList = () => {
    if (listType) {
      out.push(`</${listType}>`)
      listType = null
    }
  }

  for (const raw of lines) {
    const line = raw.replace(/\s+$/, '')

    // 代码块围栏
    if (/^```/.test(line.trim())) {
      if (inCode) {
        out.push(`<pre class="md-pre"><code>${codeBuf.join('\n')}</code></pre>`)
        codeBuf = []
        inCode = false
      } else {
        flushPara()
        closeList()
        inCode = true
      }
      continue
    }
    if (inCode) {
      codeBuf.push(raw)
      continue
    }

    // 空行：结束段落与列表
    if (!line.trim()) {
      flushPara()
      closeList()
      continue
    }

    // 标题（降级到 h3~h6，避免与页面标题抢层级）
    const heading = /^(#{1,4})\s+(.*)$/.exec(line)
    if (heading) {
      flushPara()
      closeList()
      const level = heading[1].length + 2
      out.push(`<h${level}>${inline(heading[2])}</h${level}>`)
      continue
    }

    // 无序列表
    const ul = /^\s*[-*+]\s+(.*)$/.exec(line)
    if (ul) {
      flushPara()
      if (listType !== 'ul') {
        closeList()
        out.push('<ul>')
        listType = 'ul'
      }
      out.push(`<li>${inline(ul[1])}</li>`)
      continue
    }

    // 有序列表
    const ol = /^\s*(\d+)[.)]\s+(.*)$/.exec(line)
    if (ol) {
      flushPara()
      if (listType !== 'ol') {
        closeList()
        out.push('<ol>')
        listType = 'ol'
      }
      out.push(`<li>${inline(ol[2])}</li>`)
      continue
    }

    para.push(inline(line))
  }

  if (inCode && codeBuf.length) {
    out.push(`<pre class="md-pre"><code>${codeBuf.join('\n')}</code></pre>`)
  }
  flushPara()
  closeList()
  return out.join('\n')
}
