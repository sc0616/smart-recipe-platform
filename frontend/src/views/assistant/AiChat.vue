<template>
  <div class="ai-chat">
    <!-- ============ 对话区 ============ -->
    <div class="chat-main">
      <div class="chat-header">
        <div class="title">
          <span class="robot">🤖</span>
          <div>
            <div class="name">AI 美食智能体</div>
            <div class="sub">
              <el-tag size="small" :type="isOnline ? 'success' : 'warning'" effect="plain">
                {{ isOnline ? '在线 Agent 模式' : '离线规则模式' }}
              </el-tag>
              <span class="sub-text" v-if="health">
                {{ health.tools?.length || 0 }} 个工具 · 知识库 {{ health.knowledgeChunks || 0 }} 条 ·
                {{ health.retrievalMode === 'keyword' ? '关键词检索' : '语义检索' }}
              </span>
            </div>
          </div>
        </div>
        <div class="header-actions">
          <el-button size="small" @click="reindex" :loading="reindexing">
            <el-icon><Refresh /></el-icon> 重建知识库
          </el-button>
          <el-button size="small" type="primary" plain @click="newSession">
            <el-icon><Plus /></el-icon> 新建会话
          </el-button>
        </div>
      </div>

      <div class="chat-body" ref="bodyRef">
        <!-- 欢迎区 -->
        <div v-if="!messages.length" class="welcome">
          <div class="welcome-icon">🍳</div>
          <h3>你好，我是美食智能体</h3>
          <p>
            我能查询站内真实的菜谱、食材和分类数据，也能根据你手头的食材推荐菜谱。
            <template v-if="!isOnline">
              <br />
              当前未配置大模型 Key，运行在<strong>离线规则模式</strong>，仍可用站内检索回答。
            </template>
          </p>
          <div class="suggestions">
            <div
              class="suggestion"
              v-for="s in suggestions"
              :key="s"
              @click="send(s)"
            >
              {{ s }}
            </div>
          </div>
        </div>

        <!-- 消息列表 -->
        <div
          v-for="(m, i) in messages"
          :key="i"
          class="msg"
          :class="m.role"
        >
          <div class="avatar">{{ m.role === 'user' ? '我' : '🤖' }}</div>
          <div class="bubble">
            <!-- 工具调用可视化 -->
            <div v-if="m.role === 'assistant' && m.tools && m.tools.length" class="tools">
              <el-tag
                v-for="(t, ti) in m.tools"
                :key="ti"
                size="small"
                :type="t.done ? 'success' : 'warning'"
                effect="light"
              >
                <el-icon v-if="!t.done" class="spin"><Loading /></el-icon>
                <el-icon v-else><Select /></el-icon>
                {{ toolLabel(t.name) }}
              </el-tag>
            </div>

            <!-- 思考中 -->
            <div
              v-if="m.role === 'assistant' && !m.content"
              class="thinking"
            >
              {{ m.status || '正在思考' }}<span class="dots">…</span>
            </div>

            <!-- 正文 -->
            <div v-if="m.role === 'user'" class="plain">{{ m.content }}</div>
            <div
              v-else-if="m.content"
              class="md"
              v-html="renderMarkdown(m.content)"
            ></div>
          </div>
        </div>
      </div>

      <div class="chat-input">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="问我任何关于菜谱、食材的问题…（Enter 发送，Shift + Enter 换行）"
          @keydown.enter.exact.prevent="send()"
        />
        <el-button
          type="primary"
          class="send-btn"
          :loading="sending"
          :disabled="!input.trim()"
          @click="send()"
        >
          发送
        </el-button>
      </div>
    </div>

    <!-- ============ 能力面板 ============ -->
    <aside class="chat-side">
      <div class="panel">
        <div class="panel-title">运行状态</div>
        <div class="kv">
          <span>模式</span>
          <b :class="isOnline ? 'ok' : 'warn'">{{ isOnline ? '在线' : '离线' }}</b>
        </div>
        <div class="kv"><span>模型</span><b>{{ health?.model || '未配置' }}</b></div>
        <div class="kv"><span>推理轮数上限</span><b>{{ health?.maxIterations ?? '-' }}</b></div>
        <div class="kv"><span>温度</span><b>{{ health?.temperature ?? '-' }}</b></div>
        <div class="kv"><span>知识切片</span><b>{{ health?.knowledgeChunks ?? 0 }}</b></div>
        <div class="kv"><span>活跃会话</span><b>{{ health?.activeSessions ?? 0 }}</b></div>
      </div>

      <div class="panel">
        <div class="panel-title">已挂载工具</div>
        <div class="tool-list">
          <div class="tool-item" v-for="t in health?.tools || []" :key="t">
            <code>{{ t }}</code>
            <span>{{ toolLabel(t) }}</span>
          </div>
          <el-empty
            v-if="!health?.tools?.length"
            description="暂无工具"
            :image-size="60"
          />
        </div>
      </div>

      <div class="panel tip" v-if="!isOnline">
        <div class="panel-title">💡 启用在线 Agent</div>
        <div class="tip-body">
          在 <code>application.yml</code> 的 <code>ai</code> 段填入
          <code>base-url</code> / <code>api-key</code> / <code>model</code>，
          即可开启真正的工具调用推理。支持 DeepSeek、通义千问、OpenAI、Ollama。
        </div>
      </div>
    </aside>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { renderMarkdown } from '@/utils/markdown'
import {
  getAgentHealth,
  reindexAgentKnowledge,
  clearAgentSession,
  streamAgentChat
} from '@/api/agent'

const TOOL_LABELS = {
  search_recipes: '搜索菜谱',
  get_recipe_detail: '查看菜谱详情',
  list_classifications: '查询分类',
  search_materials: '搜索食材',
  recommend_recipes: '按食材推荐',
  get_site_stats: '统计站内数据',
  search_knowledge: '检索知识库'
}

const suggestions = [
  '站内一共有多少道菜谱？',
  '有哪些菜谱分类？',
  '红烧肉怎么做？',
  '我有土豆和牛肉，能做什么菜？'
]

/** 生成会话ID：多轮对话记忆的键 */
const newSessionId = () =>
  (window.crypto && window.crypto.randomUUID)
    ? window.crypto.randomUUID()
    : 'sess-' + Date.now() + '-' + Math.random().toString(16).slice(2)

const messages = ref([])
const input = ref('')
const sending = ref(false)
const reindexing = ref(false)
const health = ref(null)
const bodyRef = ref(null)
const sessionId = ref(newSessionId())

let abortFn = null

const isOnline = computed(() => !!health.value?.online)

const toolLabel = (name) => TOOL_LABELS[name] || name

const scrollToBottom = async () => {
  await nextTick()
  const el = bodyRef.value
  if (el) el.scrollTop = el.scrollHeight
}

const loadHealth = async () => {
  try {
    const res = await getAgentHealth()
    health.value = res.data
  } catch (e) {
    // 拦截器已提示
  }
}

const newSession = () => {
  if (abortFn) abortFn()
  const old = sessionId.value
  if (messages.value.length) clearAgentSession(old).catch(() => {})
  sessionId.value = newSessionId()
  messages.value = []
  sending.value = false
  input.value = ''
}

const reindex = async () => {
  reindexing.value = true
  try {
    const res = await reindexAgentKnowledge()
    ElMessage.success(`知识库已重建，共 ${res.data.knowledgeChunks} 条切片`)
    await loadHealth()
  } catch (e) {
    // 拦截器已提示
  } finally {
    reindexing.value = false
  }
}

const send = (text) => {
  const content = (typeof text === 'string' ? text : input.value).trim()
  if (!content || sending.value) return

  messages.value.push({ role: 'user', content })
  const assistant = { role: 'assistant', content: '', tools: [], status: '正在思考' }
  messages.value.push(assistant)

  input.value = ''
  sending.value = true
  scrollToBottom()

  abortFn = streamAgentChat({
    sessionId: sessionId.value,
    message: content,
    handlers: {
      onStatus: (s) => {
        assistant.status = s || '正在思考'
      },
      onTool: (t) => {
        if (!t || !t.name) return
        if (t.status === 'start') {
          assistant.tools.push({ name: t.name, done: false })
        } else {
          const item = [...assistant.tools].reverse().find((x) => x.name === t.name && !x.done)
          if (item) item.done = true
        }
        scrollToBottom()
      },
      onToken: (chunk) => {
        assistant.content += chunk
        scrollToBottom()
      },
      onError: (msg) => {
        assistant.content += (assistant.content ? '\n\n' : '') + '⚠️ ' + msg
        scrollToBottom()
      },
      onDone: () => {
        sending.value = false
        assistant.status = ''
        scrollToBottom()
      }
    }
  })
}

onMounted(loadHealth)
</script>

<style scoped>
.ai-chat {
  display: flex;
  gap: 16px;
  height: calc(100vh - 190px);
  min-height: 520px;
}

/* ---------- 主对话区 ---------- */
.chat-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.07);
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 20px;
  border-bottom: 1px solid #f0f0f0;
  background: linear-gradient(135deg, #fffaf3 0%, #fff5ec 100%);
}
.title { display: flex; align-items: center; gap: 12px; }
.robot { font-size: 28px; }
.name { font-size: 16px; font-weight: 700; color: #333; }
.sub { display: flex; align-items: center; gap: 8px; margin-top: 4px; }
.sub-text { font-size: 12px; color: #999; }
.header-actions { display: flex; gap: 8px; flex-shrink: 0; }

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #fafafa;
}

/* ---------- 欢迎区 ---------- */
.welcome { text-align: center; padding: 40px 20px; color: #666; }
.welcome-icon { font-size: 56px; margin-bottom: 12px; }
.welcome h3 { font-size: 20px; color: #333; margin-bottom: 10px; }
.welcome p { font-size: 14px; line-height: 1.8; margin-bottom: 24px; }
.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}
.suggestion {
  padding: 9px 16px;
  background: #fff;
  border: 1px solid #ffe0b2;
  border-radius: 20px;
  font-size: 13px;
  color: #e67e22;
  cursor: pointer;
  transition: all 0.2s;
}
.suggestion:hover {
  background: #f5a623;
  border-color: #f5a623;
  color: #fff;
  transform: translateY(-2px);
}

/* ---------- 消息 ---------- */
.msg { display: flex; gap: 10px; margin-bottom: 18px; }
.msg.user { flex-direction: row-reverse; }
.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  background: #f5a623;
  color: #fff;
}
.msg.assistant .avatar { background: #e74c3c; }

.bubble {
  max-width: 78%;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.75;
  background: #fff;
  color: #333;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  word-break: break-word;
}
.msg.user .bubble {
  background: linear-gradient(135deg, #f5a623 0%, #ef8f2b 100%);
  color: #fff;
}
.plain { white-space: pre-wrap; }

.tools { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; }
.tools .el-tag { display: inline-flex; align-items: center; gap: 4px; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.thinking { color: #999; font-size: 13px; }
.dots { animation: blink 1.2s infinite; }
@keyframes blink { 0%, 100% { opacity: 0.2; } 50% { opacity: 1; } }

/* ---------- Markdown 渲染（v-html 内容需用 :deep） ---------- */
.md :deep(p) { margin: 0 0 8px; }
.md :deep(p:last-child) { margin-bottom: 0; }
.md :deep(h3), .md :deep(h4), .md :deep(h5), .md :deep(h6) {
  margin: 12px 0 6px;
  font-size: 15px;
  color: #d35400;
}
.md :deep(ul), .md :deep(ol) { margin: 6px 0 8px; padding-left: 22px; }
.md :deep(li) { margin: 3px 0; }
.md :deep(strong) { color: #c0392b; }
.md :deep(a) { color: #e67e22; }
.md :deep(.md-code) {
  background: #f5f5f5;
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 13px;
  color: #c7254e;
}
.md :deep(.md-pre) {
  background: #2d2d2d;
  color: #f0f0f0;
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 8px 0;
}
.md :deep(.md-pre code) { font-size: 13px; }

/* ---------- 输入区 ---------- */
.chat-input {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 14px 20px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
}
.chat-input :deep(.el-textarea__inner) { border-radius: 10px; }
.send-btn { height: 54px; width: 84px; border-radius: 10px; }

/* ---------- 侧栏 ---------- */
.chat-side {
  width: 248px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
}
.panel {
  background: #fff;
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.07);
}
.panel-title {
  font-size: 13px;
  font-weight: 700;
  color: #333;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f5f5f5;
}
.kv {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  margin-bottom: 9px;
}
.kv span { color: #999; }
.kv b {
  color: #444;
  font-weight: 600;
  max-width: 130px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kv b.ok { color: #67c23a; }
.kv b.warn { color: #e6a23c; }

.tool-list { display: flex; flex-direction: column; gap: 8px; }
.tool-item { display: flex; flex-direction: column; gap: 2px; }
.tool-item code {
  font-size: 11px;
  color: #c7254e;
  background: #fdf0f3;
  padding: 2px 6px;
  border-radius: 4px;
  align-self: flex-start;
}
.tool-item span { font-size: 12px; color: #888; padding-left: 2px; }

.tip { background: #fffbf0; border: 1px solid #ffe9c2; }
.tip .panel-title { border-bottom-color: #ffe9c2; }
.tip-body { font-size: 12px; line-height: 1.8; color: #8a6d3b; }
.tip-body code {
  background: #fff3dd;
  padding: 1px 4px;
  border-radius: 3px;
  color: #b9770e;
}

@media (max-width: 1100px) {
  .chat-side { display: none; }
}
</style>
