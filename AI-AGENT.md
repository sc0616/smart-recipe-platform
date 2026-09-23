# AI Agent 化改造说明

> 本文档面向**开发者**，说明「智能美食菜谱平台」在原有传统业务（菜谱 / 食材 / 分类管理、JWT 登录、点赞收藏、后台统计）之上，如何加入一套**完整的 AI Agent 运行时**：改造了什么、怎么用、怎么配置、怎么验证。
>
> **一句话概括**：AI Agent 部分**零第三方 AI 依赖**——不引入 LangChain4j、不引入 Spring AI，仅用 JDK 内置 `java.net.http.HttpClient` + Spring Boot 自带的 Jackson + Spring 的 `SseEmitter`，从零实现了 ReAct 循环、Function Calling、RAG 检索、多轮记忆与 SSE 流式输出。

---

## 目录

1. [改造总览](#1-改造总览)
2. [Agent 技术栈与架构](#2-agent-技术栈与架构)
3. [Agent 核心能力](#3-agent-核心能力)
4. [新增目录结构](#4-新增目录结构)
5. [快速开始](#5-快速开始)
6. [接口清单](#6-接口清单)
7. [工具清单](#7-工具清单)
8. [前端使用](#8-前端使用)
9. [验证方式](#9-验证方式)
10. [设计决策：为什么自研而不用 LangChain4j / Spring AI](#10-设计决策为什么自研而不用-langchain4j--spring-ai)
11. [设计亮点](#11-设计亮点)
12. [后续扩展路线](#12-后续扩展路线)

---

## 1. 改造总览

### 1.1 改造前后能力对比

| 维度 | 改造前（传统管理系统） | 改造后（AI Agent 化） |
|---|---|---|
| 交互方式 | 表单 + 列表 + 搜索框，用户必须自己想清楚点哪个菜单 | 自然语言对话：「我有土豆和牛肉，能做什么菜？」 |
| 检索能力 | 只能按关键词**精确包含**匹配（`LIKE`） | 语义检索（Embedding + 余弦相似度）+ 关键词检索双通道，带自动回退 |
| 决策能力 | 无。所有跳转由用户手动完成 | ReAct 循环自主推理：模型自己决定调哪个工具、传什么参数 |
| 业务复用 | Service 层仅被 Controller 调用 | Service 层同时被 **Agent 工具层** 复用，一处业务两处入口 |
| 多轮上下文 | 无（每次请求独立） | Chat Memory 会话记忆，窗口裁剪 + TTL 淘汰 |
| 输出形式 | 整页渲染 / JSON 全量返回 | SSE 流式打字机效果 + 工具调用过程可视化 |
| 数据变更感知 | 无 | 内存知识库 + `/agent/knowledge/reindex` 手动重建 |
| 运行依赖 | MySQL + JDK | MySQL + JDK（**AI 部分零新增依赖**） |
| 无 Key 时表现 | — | 自动降级为**离线规则模式**，基于站内数据作答，不报错、不白屏 |
| 服务商锁定 | — | 走 OpenAI 兼容协议，改配置即可切换 DeepSeek / 通义千问 / OpenAI / Ollama |

### 1.2 改造范围速览

| 层 | 新增内容 | 是否修改原有代码 |
|---|---|---|
| 后端 | `com.example.delicious.agent` 包，共 **10 个文件** | 仅**新增**，未改动原有 Controller / Service / Entity |
| 后端配置 | `application.yml` 新增 `ai.*` 段 | 复用原有 `server.port: 8081` |
| 前端 | `api/agent.js`、`utils/markdown.js`、`views/assistant/AiChat.vue` | 路由新增 `/admin/ai`、`/user/ai` 两条 |
| 依赖 | **无** | `pom.xml` 未新增任何依赖 |
| 数据库 | **无** | 沿用原有 8 张表 |

---

## 2. Agent 技术栈与架构

### 2.1 技术栈

| 能力 | 选型 | 说明 |
|---|---|---|
| 大模型通信 | JDK `java.net.http.HttpClient` | JDK 11+ 内置，无需 OkHttp / Retrofit |
| JSON 序列化 | Jackson（Spring Boot 自带） | `ObjectMapper` + `JsonNode` 解析响应 |
| 流式输出 | Spring `SseEmitter` | Spring MVC 内置，无需 WebFlux |
| 配置绑定 | `@ConfigurationProperties(prefix = "ai")` | Spring Boot 原生能力 |
| 异步执行 | `ExecutorService` 固定 4 线程守护池 | 避免 Agent 推理阻塞 Tomcat 请求线程 |
| 持久层复用 | MyBatis-Plus（项目原有） | Agent 工具直接调用既有 Service |
| 鉴权复用 | 项目原有 JWT 拦截器 | `/agent/**` 自动纳入鉴权 |
| 向量检索 | 自实现余弦相似度 | 不引入向量数据库 |
| 前端 Markdown | 自实现极简渲染器 | 先转义 HTML 再解析，无 XSS 风险 |

**关键点：以上全部为「框架自带」或「JDK 自带」能力，没有一个是为 AI 新引入的第三方库。**

### 2.2 整体架构图

```
┌──────────────────────────────────────────────────────────────────────┐
│                          浏览器 (Vue 3 + Element Plus)                 │
│                                                                        │
│   AiChat.vue  ──►  api/agent.js  ──►  fetch(POST /api/agent/chat)      │
│        │                 │              + Authorization: Bearer <JWT>   │
│        │                 └── ReadableStream 逐帧解析 SSE               │
│        └──►  utils/markdown.js（先转义 HTML，再解析 Markdown）           │
└───────────────────────────────┬────────────────────────────────────────┘
                                │  Vite Dev Server 5174  →  proxy → 8081
┌───────────────────────────────▼────────────────────────────────────────┐
│                    Spring Boot 3.2.5  后端 (:8081)                      │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ JWT 拦截器（原有）  白名单：/login /register /files/download/**   │  │
│  │                              /files/image/**                      │  │
│  │                     /agent/** 一律需要有效 JWT                     │  │
│  └──────────────────────────────┬───────────────────────────────────┘  │
│                                 ▼                                       │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │ AgentController  @RequestMapping("/agent")                      │    │
│  │   POST /chat   POST /chat/message   POST /session/clear         │    │
│  │   GET  /health POST /knowledge/reindex                          │    │
│  └────────────┬───────────────────────────────────────────────────┘    │
│               │ 提交任务到 agentExecutor（4 守护线程，线程名 agent-worker）│
│               ▼                                                         │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │ ReActAgent（核心循环，最多 ai.max-iterations = 6 轮）             │    │
│  │                                                                  │    │
│  │   System Prompt ──► LLM ──► 有 tool_calls? ──是──► 执行工具       │    │
│  │        ▲                        │                   │            │    │
│  │        │                       否                   ▼            │    │
│  │        │                        │            Observation 回灌     │    │
│  │        │                        ▼                   │            │    │
│  │        └──────── 否 ──── 拿到 Final Answer ◄────────┘            │    │
│  │                                                                  │    │
│  │   事件经 AgentEventSink 抽象输出：                                │    │
│  │     ├── SseEmitterSink      → SSE 流式（打字机 + 工具事件）        │    │
│  │     └── StringBuilderSink   → 非流式（累积为完整字符串）           │    │
│  └───────┬──────────────┬───────────────┬──────────────────────────┘    │
│          │              │               │                               │
│          ▼              ▼               ▼                               │
│  ┌─────────────┐ ┌─────────────┐ ┌──────────────────┐                   │
│  │ RecipeTools │ │ChatMemory   │ │RecipeKnowledge   │                   │
│  │ 7 个工具     │ │Store 会话记忆│ │Base RAG 知识库    │                   │
│  └──────┬──────┘ └─────────────┘ └────────┬─────────┘                   │
│         │                                  │                            │
│         │        ┌─────────────────────────┘                            │
│         ▼        ▼                                                       │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │ OpenAiCompatibleClient（LLM 网关，JDK HttpClient）               │    │
│  │   chat(messages, tools) → POST {base-url}/chat/completions      │    │
│  │   embed(texts)          → POST {base-url}/embeddings            │    │
│  └────────────────────────────┬───────────────────────────────────┘    │
│                               │                                         │
│  ┌────────────────────────────▼───────────────────────────────────┐    │
│  │ 原有 Service 层（FoodsMenuInfoService / ClassifyInfoService …） │    │
│  └────────────────────────────┬───────────────────────────────────┘    │
└───────────────────────────────┼─────────────────────────────────────────┘
                                ▼
                   MySQL 8  库名 delicious_foods（8 张表）
                                │
                                ▼
             外部 OpenAI 兼容服务（DeepSeek / 通义千问 / OpenAI / Ollama）
             —— 未配置时完全不出网，走离线规则模式
```

### 2.3 分层职责

| 层 | 文件 | 职责 | 关键约束 |
|---|---|---|---|
| 接口层 | `AgentController` | 暴露 5 个 HTTP 接口，创建 `SseEmitter`，把任务提交到 `agentExecutor` | 不含业务逻辑；会话 ID 为空时生成 UUID |
| 编排层 | `ReActAgent` | ReAct 主循环：组织消息、调用 LLM、分发工具、回灌 Observation、输出事件 | 最多 6 轮；异常统一转 `error` 事件 |
| 工具层 | `RecipeTools` | 7 个工具的**定义**（JSON Schema）与**分发执行** | 结果一律为 JSON 字符串；异常转 `{"error": "..."}` |
| 知识层 | `RecipeKnowledgeBase` | 建库切片、语义检索、关键词检索、索引重建 | 语义失败自动回退关键词 |
| 记忆层 | `ChatMemoryStore` | 以 sessionId 为键保存消息窗口，TTL 淘汰 | 窗口 + TTL 双重裁剪 |
| 网关层 | `OpenAiCompatibleClient` | 封装 chat / embeddings 两个 HTTP 调用 | 凭据不全则 `online()==false` |
| 配置层 | `AgentProperties` / `AgentConfig` | 绑定 `ai.*`，装配 `agentExecutor` 线程池 | 4 守护线程，线程名 `agent-worker` |
| 模型层 | `ToolDef` / `AgentChatRequest` | 工具定义 record、请求体 POJO | `ToolDef(name, description, parameters)` |

---

## 3. Agent 核心能力

本章按「**概念 → 本项目实现**」逐项对应。

### 3.1 ReAct 循环（Reason + Act）

| 项 | 内容 |
|---|---|
| 概念 | 模型交替进行「推理」与「行动」，用工具返回的真实结果约束下一步推理，而不是一次性凭记忆作答 |
| 本项目实现 | `core/ReActAgent.java` 的 `online()` 方法 |
| 最大轮数 | `ai.max-iterations`，默认 **6** |
| 循环终止条件 | ① 模型返回 `content`（Final Answer）② 工具未再被调用 ③ 达到轮数上限（输出兜底话术） |

**在线模式时序**（以「红烧肉怎么做？」为例）：

```
前端                      AgentController        ReActAgent            LLM               RecipeTools
 │                              │                    │                  │                    │
 │─ POST /agent/chat ──────────►│                    │                  │                    │
 │  {sessionId, message}        │                    │                  │                    │
 │◄─ 200 text/event-stream ─────│                    │                  │                    │
 │                              │─ submit ──────────►│                  │                    │
 │                              │   (agent-worker)   │                  │                    │
 │                              │                    │─ status「正在思考…」─►│  (SSE)             │
 │◄─ event:status ───────────────────────────────────│                  │                    │
 │                              │                    │                  │                    │
 │                              │                    │─ chat(system+记忆+user, tools=7个) ────►│
 │                              │                    │                  │                    │
 │                              │                    │◄─ tool_calls: search_recipes ──────────│
 │◄─ event:tool {"name":"search_recipes","status":"start"} ──────────────│                    │
 │                              │                    │──────────────────┼───────────────────►│
 │                              │                    │◄─ {"total":3,"items":[...]} ───────────│
 │◄─ event:tool {"name":"search_recipes","status":"end"} ────────────────│                    │
 │                              │                    │                  │                    │
 │                              │                    │─ 回灌 tool 消息，继续第 2 轮 ──────────►│
 │                              │                    │                  │                    │
 │                              │                    │◄─ tool_calls: get_recipe_detail ───────│
 │◄─ event:tool {"name":"get_recipe_detail","status":"start"} ───────────│                    │
 │                              │                    │──────────────────┼───────────────────►│
 │                              │                    │◄─ {"id":1,"description":"..."} ────────│
 │◄─ event:tool {"name":"get_recipe_detail","status":"end"} ─────────────│                    │
 │                              │                    │                  │                    │
 │                              │                    │─ 第 3 轮 ────────►│                    │
 │                              │                    │◄─ content: "## 红烧肉\n1. ..." ────────│
 │◄─ event:token "## 红烧" ──────────────────────────│                  │                    │
 │◄─ event:token "肉\n1..." ─────────────────────────│                  │                    │
 │                              │                    │─ 写入 ChatMemory  │                    │
 │◄─ event:done ─────────────────────────────────────│                  │                    │
```

**消息数组的构造顺序**（`online()` 方法）：

```
[0] system  ← buildSystemPrompt()（含 7 条工作准则）
[1..n]      ← memory.getMessages(sessionId, window)  多轮历史
[n+1] user  ← 本轮用户消息
--- 循环中追加 ---
      assistant（含 tool_calls 的原始 message，用 mapper.convertValue 原样回灌）
      tool（tool_call_id + name + JSON 结果）
```

> **关键工程细节**：模型返回的 assistant 消息必须**原样**回灌（含 `tool_calls` 字段），否则 OpenAI 协议会校验失败。代码中通过 `mapper.convertValue(msg, MAP_TYPE)` 实现整节点转换。

### 3.2 Function Calling / Tools

| 项 | 内容 |
|---|---|
| 概念 | 把本地函数以 JSON Schema 形式声明给模型，模型决定调用哪个、传什么参数 |
| 本项目实现 | `llm/ToolDef.java`（定义 record）+ `tools/RecipeTools.java`（`toolDefs()` 声明 + `dispatch()` 执行） |
| 工具数量 | **7 个** |
| 协议构造 | `OpenAiCompatibleClient.chat()` 中拼装 `tools[].function.{name,description,parameters}`，并设置 `tool_choice: "auto"` |
| Schema 构造 | `RecipeTools` 内 4 个私有辅助方法：`objectSchema` / `props` / `stringSchema` / `integerSchema` / `stringArraySchema` |

**工具执行的安全边界**（`dispatch()`）：

```java
public String dispatch(String name, Map<String, Object> args) {
    try {
        switch (name == null ? "" : name) {
            case "search_recipes": return searchRecipes(safeArgs);
            // ... 其余 6 个
            default: return jsonError("未知工具：" + name);
        }
    } catch (Exception e) {
        return jsonError("工具执行失败：" + e.getMessage());   // 绝不抛异常穿透
    }
}
```

工具失败时返回 `{"error": "..."}` 交给模型，模型会据此向用户解释，而不是整个对话崩掉。

### 3.3 RAG（检索增强生成）

| 项 | 内容 |
|---|---|
| 概念 | 先用检索从私有语料中找到相关片段，再把片段作为上下文交给模型生成，抑制幻觉 |
| 本项目实现 | `knowledge/RecipeKnowledgeBase.java` |
| 建库时机 | `@PostConstruct init()` 启动时自动建库；数据变更后调 `POST /agent/knowledge/reindex` 手动重建 |
| 切片来源 | ① 分类 → `菜谱分类【名称】：描述` ② 食材 → `食材【名称】：描述` ③ 菜谱 → `菜谱【名称】，小贴士：…，做法：…，发布者：…` |
| 存储 | 内存 `CopyOnWriteArrayList<String>`，规模与站内数据同阶 |
| 检索通道一 | **语义检索**：`client.embed()` 批量向量化 + 余弦相似度，**阈值 > 0.15** 才采纳 |
| 检索通道二 | **关键词检索**：整串命中 +6.0、汉字二元组（bigram）每个 +1.5、单字 +0.5、英文词（长度≥2）+2.0 |
| 选择逻辑 | `client.embeddingEnabled()` 为真走语义，否则走关键词 |
| 回退逻辑 | 语义检索抛异常 → `catch` 中 `log.warn` 并自动改用关键词检索 |
| 返回条数 | `ai.rag-top-k`，默认 **3** |
| 向量懒加载 | `ensureEmbeddings()` 双重检查锁（`volatile` + `synchronized`），首次检索时才批量向量化 |

### 3.4 Chat Memory（会话记忆）

| 项 | 内容 |
|---|---|
| 概念 | 保存多轮对话历史，让模型「记得」上文 |
| 本项目实现 | `memory/ChatMemoryStore.java` |
| 数据结构 | `ConcurrentHashMap<String, Deque<MemoryMessage>>` + `ArrayDeque` |
| 记忆消息 | `record MemoryMessage(String role, String content)` |
| 窗口裁剪 | `ai.memory-window`，默认 **10** 条；`addMessage` 时 `removeFirst()` 直到不超窗口 |
| TTL 淘汰 | `ai.memory-ttl-minutes`，默认 **30** 分钟；`touch(sessionId)` 时惰性判断并清理 |
| 并发安全 | Map 用 `ConcurrentHashMap`；对 `ArrayDeque` 的读写加 `synchronized (deque)` |
| 读取裁剪 | `getMessages()` 再次按窗口取末尾 N 条，可直接作为 LLM messages 前缀 |
| 主动清空 | `POST /agent/session/clear` |

### 3.5 SSE 流式输出

| 项 | 内容 |
|---|---|
| 概念 | Server-Sent Events，服务端单向持续推送，实现打字机效果 |
| 本项目实现 | `ReActAgent.SseEmitterSink` 内部类 + `AgentController.chat()` |
| 超时 | `SSE_TIMEOUT_MS = 30 * 60 * 1000L`（30 分钟） |
| 线程模型 | 请求线程创建 `SseEmitter` 后立即返回，推理任务提交到 `agentExecutor` 的 `agent-worker` 线程 |
| 打字机节奏 | `token()` 每次发送后 `Thread.sleep(8)` 毫秒 |
| 分片大小 | `tokenStream()` 默认每 **6 个字符**切一块 |
| 断连处理 | `send()` 的 `catch (Exception ignored)` 静默忽略；`run()` 的 `finally` 中 `emitter.complete()` 也包在 try 里 |

**为什么载荷要统一 JSON 编码**（本项目最关键的一个坑）：

```java
/**
 * 把文本编码为 JSON 字符串下发。
 * 必要性：SSE 以空行分帧，若 data 中直接包含换行符会破坏帧结构、导致前端丢字。
 * JSON 编码会把 \n 转义为 \\n，从根本上规避该问题。
 */
private String encode(String text) {
    try {
        return mapper.writeValueAsString(text == null ? "" : text);
    } catch (Exception e) {
        return "\"\"";
    }
}
```

SSE 规范用**空行**作为消息分隔符。模型输出的 Markdown 里必然有换行，一旦原样下发，一个回答会被切成一堆「半截帧」，前端表现为**丢字、断句、内容错乱**。统一 `mapper.writeValueAsString()` 后，`\n` 变成字面量 `\\n`，走 SSE 时仍在同一帧内，前端 `JSON.parse()` 后再还原。前端 `utils/markdown.js` 的 `decodePayload()` 与之对称。

### 3.6 Prompt Engineering

系统提示词硬编码在 `ReActAgent.buildSystemPrompt()`，用 Java 文本块书写，包含 **7 条工作准则**：

| # | 准则 | 工程目的 |
|---|---|---|
| 1 | 涉及站内数据必须先调工具，严禁凭空编造 | 抑制幻觉，这是 RAG/工具存在的意义 |
| 2 | 「我有 XX 和 YY」→ 调 `recommend_recipes` | 绑定典型意图到具体工具 |
| 3 | 浏览搜索 → `search_recipes`；要完整做法 → 再调 `get_recipe_detail` | 引导**两段式**调用，避免一次拉全量数据浪费 token |
| 4 | 烹饪常识 / 食材搭配 → `search_knowledge` | 区分「站内数据」与「知识库检索」两条路径 |
| 5 | 一次不够可连续调用多个工具 | 明确允许**多轮** ReAct，为 `max-iterations` 正名 |
| 6 | 简体中文、亲切简洁、用 Markdown 组织 | 输出风格约束，同时匹配前端的 Markdown 渲染器 |
| 7 | 工具无结果要如实告知 | 兜底诚实性，防止「编一道菜」 |

### 3.7 优雅降级（本项目最实用的设计）

| 降级触发条件 | 降级行为 | 用户感知 |
|---|---|---|
| `ai.enabled=false`，或 `base-url`/`api-key`/`model` 任一为空 | `online()==false` → **离线规则模式** | SSE 首帧提示「离线规则模式：未配置大模型，使用站内检索作答」，随后返回基于站内真实数据的回答 |
| 未配置 `embedding-model` | RAG 走**关键词检索** | 完全无感，`/health` 中 `retrievalMode: "keyword"` |
| 语义检索调用失败（网络/额度/格式） | `catch` 后**回退关键词检索** | 日志有 warn，用户无感 |
| 工具执行抛异常 | 返回 `{"error":"..."}` JSON | 模型据此解释，对话不中断 |
| 大模型返回非 200 | `IllegalStateException("大模型返回 4xx：…")` | 转为 SSE `error` 事件，前端显示 ⚠️ |
| 模型返回空 choices | `sink.error("大模型没有返回有效内容")` | 明确提示，不静默 |
| 达到最大轮数仍无最终答案 | 输出兜底话术并写入记忆 | 用户可换个说法继续问 |
| 用户消息为空 | `sink.status("请先输入你的问题～")` | 友好提示 |
| 前端主动断开 / 关闭页面 | `AbortController.abort()` + 后端 `catch` 静默忽略 | 无异常堆栈刷屏 |

**离线规则模式的具体逻辑**（`buildOfflineReply()`）——按顺序做站内检索并拼装：

```
固定开头 → 「🧠 当前是「离线规则模式」……」
├─ 命中「多少/统计/数量/几个/总数」  → humanSiteStats()        站点统计
├─ 命中「分类/类别/种类」            → humanListClassifications() 分类清单
├─ 始终执行                          → humanSearchRecipes(q, 3)  相关菜谱（最多 3 条）
├─ 始终执行                          → humanSearchMaterials(q, 3) 相关食材（最多 3 条）
└─ 若以上全空                        → 输出「没找到，你可以这样问我」+ 4 条示例问题
固定结尾 → 「💡 在 application.yml 中配置 ai.base-url / ai.api-key / ai.model ……」
```

离线模式下仍会**正常写入 ChatMemory**，因此会话记忆功能在两种模式下行为一致。

---

## 4. 新增目录结构

```
美食网站管理系统源码/
├── AI-AGENT.md                          ← 本文档
├── README.md
├── sql/
│   └── init.sql                         建库脚本（8 张表 + 种子数据）
│
├── backend/
│   ├── pom.xml                          ★ 未新增任何 AI 依赖
│   └── src/main/
│       ├── java/com/example/delicious/
│       │   ├── agent/                   ★★★ 本次改造新增，共 10 个文件
│       │   │   ├── config/
│       │   │   │   ├── AgentProperties.java    @ConfigurationProperties(prefix="ai")，
│       │   │   │   │                            绑定 ai.* 全部 13 个配置项
│       │   │   │   └── AgentConfig.java        装配 @Bean("agentExecutor")：
│       │   │   │                               固定 4 线程守护池，线程名 agent-worker
│       │   │   ├── llm/
│       │   │   │   ├── ToolDef.java            record ToolDef(String name,
│       │   │   │   │                            String description, Map<String,Object> parameters)
│       │   │   │   └── OpenAiCompatibleClient.java  LLM 网关（JDK HttpClient）：
│       │   │   │                                online() / embeddingEnabled()
│       │   │   │                                chat(messages, tools) → JsonNode
│       │   │   │                                embed(texts) → List<float[]>
│       │   │   ├── tools/
│       │   │   │   └── RecipeTools.java        7 个工具的注册(toolDefs)与分发(dispatch)，
│       │   │   │                               复用既有 Service 层；另含 4 个
│       │   │   │                               human*() 方法供离线模式生成自然语言摘要
│       │   │   ├── knowledge/
│       │   │   │   └── RecipeKnowledgeBase.java RAG：建库切片 / 语义检索(余弦,阈值0.15)
│       │   │   │                               / 关键词检索(汉字二元组+英文词) / reindex
│       │   │   ├── memory/
│       │   │   │   └── ChatMemoryStore.java    会话记忆：ConcurrentHashMap + ArrayDeque，
│       │   │   │                               窗口裁剪 + TTL 淘汰
│       │   │   ├── core/
│       │   │   │   └── ReActAgent.java         ReAct 核心循环（最多 6 轮）
│       │   │   │                               + AgentEventSink 事件抽象
│       │   │   │                               + SseEmitterSink / StringBuilderSink 两个实现
│       │   │   ├── model/
│       │   │   │   └── AgentChatRequest.java   请求体 {sessionId, message}
│       │   │   └── controller/
│       │   │       └── AgentController.java    @RequestMapping("/agent")，5 个接口
│       │   ├── controller/                     原有业务 Controller
│       │   ├── service/                        原有 Service（被 RecipeTools 复用）
│       │   ├── entity/                         原有实体
│       │   └── common/                         Result / JwtUtil / 异常 / WebConfig
│       └── resources/
│           ├── application.yml          ★ 新增 `ai.*` 配置段（含 4 家服务商示例注释）
│           └── application-local.yml    ★ 私有 Key（已被 .gitignore 排除，可选）
│
└── frontend/
    ├── vite.config.js                   port: 5174；proxy target: http://localhost:8081
    └── src/
        ├── api/
        │   └── agent.js                 ★ 新增：Agent 接口封装 + SSE 流读取
        │                                  (fetch + ReadableStream；含 AbortController 中断)
        ├── utils/
        │   └── markdown.js              ★ 新增：安全 Markdown 渲染（先转义 HTML 再解析）
        ├── views/
        │   └── assistant/
        │       └── AiChat.vue           ★ 新增：AI 对话页面（同时服务管理端与用户端）
        └── router/
            └── *.js                     ★ 新增两条路由：
                                           /admin/ai → AiChat.vue
                                           /user/ai  → AiChat.vue
```

### 4.1 新增文件清单（可核对）

| # | 文件 | 行数规模 | 一句话作用 |
|---|---|---|---|
| 1 | `agent/config/AgentProperties.java` | ~60 | 绑定 `ai.*` 全部配置 |
| 2 | `agent/config/AgentConfig.java` | ~29 | 装配 Agent 专用线程池 |
| 3 | `agent/llm/ToolDef.java` | ~13 | 工具定义 record |
| 4 | `agent/llm/OpenAiCompatibleClient.java` | ~164 | LLM 网关（chat + embeddings） |
| 5 | `agent/tools/RecipeTools.java` | ~486 | 7 工具声明与执行 |
| 6 | `agent/knowledge/RecipeKnowledgeBase.java` | ~251 | RAG 知识库 |
| 7 | `agent/memory/ChatMemoryStore.java` | ~99 | 会话记忆 |
| 8 | `agent/core/ReActAgent.java` | ~405 | ReAct 循环 + 事件抽象 |
| 9 | `agent/model/AgentChatRequest.java` | ~16 | 请求体 |
| 10 | `agent/controller/AgentController.java` | ~133 | 5 个 REST 接口 |

---

