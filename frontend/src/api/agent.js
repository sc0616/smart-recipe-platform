import request from '@/utils/request'

/** Agent 运行状态与能力探测 */
export const getAgentHealth = () => request.get('/agent/health')

/** 非流式对话（一次性返回完整回答） */
export const chatAgentOnce = (data) => request.post('/agent/chat/message', data)

/** 清空某个会话的记忆 */
export const clearAgentSession = (sessionId) =>
  request.post('/agent/session/clear', { sessionId })

/** 重建 RAG 知识库（菜谱/食材变更后调用） */
export const reindexAgentKnowledge = () => request.post('/agent/knowledge/reindex')

/**
 * SSE 流式对话。
 *
 * 之所以用 fetch 而不是 EventSource：EventSource 只支持 GET，
 * 而我们需要 POST 传消息体，并携带 Authorization 头。
 *
 * @param {Object}   options
 * @param {string}   options.sessionId 会话ID（多轮记忆的键）
 * @param {string}   options.message   用户消息
 * @param {Object}   options.handlers  { onStatus, onTool, onToken, onDone, onError }
 * @returns {Function} 调用即可中断本次流式请求
 */
export function streamAgentChat({ sessionId, message, handlers = {} }) {
  const controller = new AbortController()

  const run = async () => {
    try {
      const res = await fetch('/api/agent/chat', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: 'Bearer ' + (localStorage.getItem('token') || '')
        },
        body: JSON.stringify({ sessionId, message }),
        signal: controller.signal
      })

      if (res.status === 401) {
        handlers.onError?.('登录已过期，请重新登录')
        return
      }
      if (!res.ok || !res.body) {
        throw new Error('请求失败（HTTP ' + res.status + '）')
      }

      const reader = res.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      // SSE 以空行分帧
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        let sep
        while ((sep = buffer.indexOf('\n\n')) !== -1) {
          const block = buffer.slice(0, sep)
          buffer = buffer.slice(sep + 2)
          handleBlock(block, handlers)
        }
      }
      if (buffer.trim()) {
        handleBlock(buffer, handlers)
      }
    } catch (e) {
      if (e && e.name === 'AbortError') return
      handlers.onError?.(e?.message || '网络错误')
    }
  }

  run()
  return () => controller.abort()
}

/** 解析一个 SSE 帧并分发 */
function handleBlock(block, handlers) {
  const lines = block.split('\n')
  let event = 'message'
  const dataLines = []

  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''))
    }
  }
  const raw = dataLines.join('\n')

  switch (event) {
    case 'token':
      handlers.onToken?.(decodePayload(raw))
      break
    case 'status':
      handlers.onStatus?.(decodePayload(raw))
      break
    case 'tool':
      handlers.onTool?.(decodePayload(raw))
      break
    case 'error':
      handlers.onError?.(decodePayload(raw))
      break
    case 'done':
      handlers.onDone?.()
      break
    default:
      break
  }
}

/** 后端统一用 JSON 字符串编码载荷（避免换行破坏 SSE 分帧） */
function decodePayload(raw) {
  if (raw === undefined || raw === null || raw === '') return ''
  try {
    const parsed = JSON.parse(raw)
    return typeof parsed === 'string' ? parsed : parsed
  } catch (e) {
    return raw
  }
}
