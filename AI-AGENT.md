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

## 5. 快速开始

### 5.0 前置准备（两种方式都需要）

| 依赖 | 版本要求 | 校验命令 |
|---|---|---|
| JDK | 17+（Spring Boot 3.2.5 要求） | `java -version` |
| Maven | 3.6+ | `mvn -v` |
| Node.js | 18+（Vue 3 + Vite） | `node -v` |
| MySQL | 8.x，端口 3306 | `mysql --version` |

**第 1 步：建库**

```powershell
# 在项目根目录执行（PowerShell）
mysql -u root -p123456 -e "CREATE DATABASE IF NOT EXISTS delicious_foods DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -u root -p123456 delicious_foods < sql\init.sql
```

如果 `mysql` 不在 PATH 中，也可用全路径，例如：

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p123456 delicious_foods < sql\init.sql
```

验证建库结果（应输出 **8** 张表）：

```powershell
mysql -u root -p123456 delicious_foods -e "SHOW TABLES;"
```

预期表名：`admin_info`、`user_info`、`foods_menu_info`、`foods_material_info`、`classify_info`、`nx_system_file_info`、`praise_info`、`collect_info`。

> 若数据库账号密码不是 `root/123456`，用环境变量覆盖，无需改代码：
> ```powershell
> $env:DB_USERNAME="你的账号"
> $env:DB_PASSWORD="你的密码"
> ```

**默认账号**：

| 角色 | 账号 | 密码 | `level` | 登录后可见的 AI 入口 |
|---|---|---|---|---|
| 管理员 | `admin` | `123456` | 1 | `/admin/ai` |
| 普通用户 | `user` | `123456` | 2 | `/user/ai` |

---

### 5.1 方式一：离线规则模式（零配置，推荐先跑通）

**不需要任何 API Key**。`ai.base-url` / `ai.api-key` / `ai.model` 中 `api-key` 默认取 `${DEEPSEEK_API_KEY:}`，环境变量未设置时为空 → 自动进入离线规则模式，功能可用且不报错。

**启动后端（端口 8081）**

```powershell
cd backend
mvn spring-boot:run
```

看到类似日志即为成功：

```
Tomcat started on port 8081 (http) with context path ''
菜谱知识库已重建，共 N 条知识切片（检索模式：关键词）
```

> **注意端口是 8081**（不是 Spring Boot 默认的 8080），已在 `application.yml` 中显式配置，以避免与本机其他项目冲突。若改回 8080，必须同步修改 `frontend/vite.config.js` 中 proxy 的 `target`。

**启动前端（端口 5174）**

另开一个终端：

```powershell
cd frontend
npm install        # 首次运行需要
npm run dev
```

输出：

```
  ➜  Local:   http://localhost:5174/
```

> **注意端口是 5174**（不是 Vite 默认的 5173），已在 `vite.config.js` 中配置 `port: 5174`。

**访问**：浏览器打开 `http://localhost:5174/` → 用 `admin/123456` 登录 → 进入 **AI 助手**（`/admin/ai`）。

**离线模式下的预期表现**：

- 页面顶部状态徽标显示「离线规则模式」，工具数显示 7，知识库切片数显示实际条数
- 提问「站内一共有多少道菜谱？」→ 返回真实的统计数字
- 提问「有哪些菜谱分类？」→ 列出分类
- 提问「红烧肉怎么做？」→ 列出匹配的菜谱名（离线模式不生成做法，只做站内检索）

---

### 5.2 方式二：在线 Agent 模式（配置 API Key）

**目标**：开启真正的 Function Calling + ReAct 多轮推理 + （可选）语义检索。

#### 推荐做法：写入 `application-local.yml`

`application.yml` 中 `spring.profiles.active: local`，且 `application-local.yml` 已被 `.gitignore` 排除，因此**私有 Key 不会进仓库**。在 `backend/src/main/resources/` 下新建 `application-local.yml`：

```yaml
ai:
  base-url: https://api.deepseek.com
  api-key: sk-你的真实Key
  model: deepseek-chat
  embedding-model: ""      # DeepSeek 无 embeddings 接口，留空 → 关键词检索
```

#### 备选做法：环境变量

```powershell
# PowerShell（当前会话生效）
$env:DEEPSEEK_API_KEY = "sk-你的真实Key"
cd backend
mvn spring-boot:run
```

```cmd
:: CMD
set DEEPSEEK_API_KEY=sk-你的真实Key
cd backend
mvn spring-boot:run
```

#### 服务商配置示例

| 服务商 | `base-url` | `model` | `embedding-model` | 备注 |
|---|---|---|---|---|
| **DeepSeek** | `https://api.deepseek.com` | `deepseek-chat` | 留空 `""` | 暂无 embeddings 接口 → 自动关键词检索。也支持 `deepseek-reasoner`（R1，回复较慢） |
| **通义千问 DashScope** | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` | `text-embedding-v3` | 可完整开启「语义 + 关键词」双通道检索 |
| **OpenAI** | `https://api.openai.com/v1` | `gpt-4o-mini` | `text-embedding-3-small` | 同样支持双通道检索 |
| **Ollama（本地）** | `http://localhost:11434/v1` | `qwen2.5:7b` | 依模型而定，可留空 | `api-key` 填 `ollama`（占位即可，Ollama 不校验） |

> **路径拼接规则**：代码会把 `base-url` 去掉结尾 `/` 后拼 `/chat/completions` 与 `/embeddings`。例如 `https://api.deepseek.com` → `https://api.deepseek.com/chat/completions`；`https://api.openai.com/v1` → `https://api.openai.com/v1/chat/completions`。**`/v1` 这类版本前缀必须包含在 `base-url` 里**。

#### 配置生效验证

```powershell
# 重启后端后，health 中应看到 online: true、mode: online
curl.exe -s http://localhost:8081/agent/health -H "Authorization: Bearer <你的Token>"
```

---

### 5.3 全部配置项

配置位于 `backend/src/main/resources/application.yml` 的 `ai.*` 段。

```yaml
ai:
  enabled: true                            # Agent 总开关
  base-url: https://api.deepseek.com       # OpenAI 兼容服务地址，留空=离线模式
  api-key: ${DEEPSEEK_API_KEY:}            # API Key，留空=离线模式
  model: deepseek-chat                     # 对话模型
  embedding-model: ""                      # 向量模型，留空则用关键词检索
  embedding-base-url: ""                   # 向量服务地址，默认复用 base-url
  temperature: 0.4                         # 采样温度
  max-tokens: 1200                         # 单次最大 token
  max-iterations: 6                        # ReAct 最大轮数
  memory-window: 10                        # 会话记忆窗口条数
  memory-ttl-minutes: 30                   # 会话过期时间
  timeout-seconds: 60                      # 请求超时
  rag-top-k: 3                             # RAG 返回条数
```

| 配置项 | 类型 | 默认值 | 作用 | 调优建议 |
|---|---|---|---|---|
| `ai.enabled` | boolean | `true` | Agent 总开关；`false` 时 `online()` 恒为 false | 想临时下线 AI 能力而不删配置时设为 `false` |
| `ai.base-url` | String | `https://api.deepseek.com` | OpenAI 兼容服务地址（**不含** `/chat/completions`） | 留空即离线模式；换服务商只改这里 |
| `ai.api-key` | String | `${DEEPSEEK_API_KEY:}` | API Key；支持环境变量注入 | 建议放 `application-local.yml` 或环境变量，不要提交 |
| `ai.model` | String | `deepseek-chat` | 对话模型名 | 留空即离线模式 |
| `ai.embedding-model` | String | `""` | 向量模型名；**留空则 RAG 用关键词检索** | DeepSeek 用户保持留空；通义/OpenAI 可填 |
| `ai.embedding-base-url` | String | `""` | 向量服务地址；留空复用 `base-url` | 对话与向量分开采购时使用 |
| `ai.temperature` | double | `0.4` | 采样温度 | 菜谱回答要求稳定，0.3~0.5 较合适 |
| `ai.max-tokens` | int | `1200` | 单次生成上限 | 回答被截断时调大 |
| `ai.max-iterations` | int | `6` | ReAct 最大轮数，防死循环 | 调大 = 可串联更多工具，但更慢更贵 |
| `ai.memory-window` | int | `10` | 每会话保留消息条数 | 10 条 ≈ 5 轮问答；调大增加上下文成本 |
| `ai.memory-ttl-minutes` | long | `30` | 记忆过期时间（分钟） | 演示场景可调小以便观察淘汰 |
| `ai.timeout-seconds` | int | `60` | 单次 LLM 请求超时 | 用 `deepseek-reasoner` 时建议调到 120 |
| `ai.rag-top-k` | int | `3` | RAG 返回条数 | 知识库大时调大，但会占用 prompt 长度 |

> **配置绑定类**：`agent/config/AgentProperties.java`，`@ConfigurationProperties(prefix = "ai")` + Lombok `@Data`，通过 `@EnableConfigurationProperties(AgentProperties.class)` 在 `AgentConfig` 中显式启用。

---

## 6. 接口清单

所有接口前缀 `/agent`，**均需有效 JWT**。响应统一包装在项目既有的 `Result<T>` 结构中：`{"code": "...", "msg": "...", "data": {...}}`。

| # | 方法 | 路径 | 用途 | 请求体 | 响应 |
|---|---|---|---|---|---|
| 1 | `POST` | `/agent/chat` | **SSE 流式对话**（打字机 + 工具事件） | `{sessionId, message}` | `text/event-stream` |
| 2 | `POST` | `/agent/chat/message` | **非流式对话**（脚本/接口测试友好） | `{sessionId, message}` | `Result<{sessionId, answer, mode}>` |
| 3 | `POST` | `/agent/session/clear` | **清空会话记忆** | `{sessionId}`（可空） | `Result<?>` |
| 4 | `GET` | `/agent/health` | **健康检查 / 能力探测** | 无 | `Result<{...12 个字段}>` |
| 5 | `POST` | `/agent/knowledge/reindex` | **重建 RAG 知识库** | 无 | `Result<{knowledgeChunks, retrievalMode}>` |

请求体结构与 `agent/model/AgentChatRequest.java` 一致：

```json
{
  "sessionId": "3f2a...-uuid",   // 可空；为空时后端自动生成 UUID。多轮记忆的键
  "message": "红烧肉怎么做？"     // 用户消息
}
```

### 6.1 SSE 事件协议

`POST /agent/chat` 返回 `Content-Type: text/event-stream`，事件共 **5 类**，**载荷一律为 JSON 字符串**。

| 事件名 | 载荷 | 触发时机 | 前端处理 |
|---|---|---|---|
| `status` | JSON 字符串，如 `"\"正在思考…\""` | 每轮开始前、离线模式提示 | 更新气泡上的状态文案 |
| `tool` | JSON 对象字符串：`{"name":"工具名","status":"start"\|"end"}` | 工具调用前后各一次 | 显示工具标签，`start` 时插入，`end` 时标记完成 |
| `token` | JSON 字符串，回答文本的一个分片（约 6 字符） | 模型给出最终答案时逐片下发 | 追加到气泡内容 |
| `error` | JSON 字符串，错误信息 | 无有效 choices / 执行异常 | 以 ⚠️ 前缀追加到内容中 |
| `done` | `{}` | 流程结束（`finally` 中必发） | 结束 loading 状态 |

**原始 SSE 报文示例**：

```
event:status
data:"离线规则模式：未配置大模型，使用站内检索作答"

event:tool
data:{"name":"search_recipes","status":"start"}

event:tool
data:{"name":"search_recipes","status":"end"}

event:token
data:"🧠 当前是「离线规则模式」"

event:token
data:"——还没有配置大模型"

event:done
data:{}
```

**出错时的报文示例**（`error` 事件，随后仍会补发 `done`）：

```
event:status
data:"正在思考…"

event:error
data:"大模型返回 401：{\"error\":{\"message\":\"Authentication Fails\"}}"

event:done
data:{}
```

> **注意**：`data:` 后面是**带引号的 JSON 字符串**（因为整体做了 JSON 编码），前端必须 `JSON.parse()` 一次才能拿到真实文本。这正是解决换行破坏分帧的关键设计，详见 [3.5 节](#35-sse-流式输出)。
>
> **`done` 一定会发**：它在 `ReActAgent.execute()` 的 `finally` 块中调用，因此无论走 `status`/`token`/`error` 哪条路径，前端都能收到结束标记并复位 loading 状态，不会卡死。

### 6.2 `/agent/health` 响应示例

```json
{
  "code": "200",
  "msg": "成功",
  "data": {
    "enabled": true,
    "online": true,
    "mode": "online",
    "model": "deepseek-chat",
    "baseUrl": "https://api.deepseek.com",
    "temperature": 0.4,
    "maxIterations": 6,
    "embeddingEnabled": false,
    "embeddingModel": "",
    "retrievalMode": "keyword",
    "knowledgeChunks": 42,
    "activeSessions": 1,
    "tools": [
      "search_recipes",
      "get_recipe_detail",
      "list_classifications",
      "search_materials",
      "recommend_recipes",
      "get_site_stats",
      "search_knowledge"
    ]
  }
}
```

**字段说明**：

| 字段 | 含义 | 排障价值 |
|---|---|---|
| `enabled` | `ai.enabled` 的值 | 为 false 说明总开关被关了 |
| `online` | 是否具备调用真实大模型的条件 | **为 false 时先查 `base-url`/`api-key`/`model` 三项是否齐全** |
| `mode` | `"online"` / `"offline"` | 前端据此显示状态徽标 |
| `model` | 当前对话模型 | 核对是否加载了预期的 profile |
| `baseUrl` | 当前服务地址 | 核对路径前缀（如 `/v1` 是否漏了） |
| `temperature` | 采样温度 | 确认配置绑定生效 |
| `maxIterations` | ReAct 最大轮数 | 确认配置绑定生效 |
| `embeddingEnabled` | 是否启用语义检索 | false 说明未配 `embedding-model` |
| `embeddingModel` | 向量模型名 | — |
| `retrievalMode` | `"semantic+keyword"` / `"keyword"` | 说明当前 RAG 走哪条通道 |
| `knowledgeChunks` | 知识库切片总数 | **为 0 说明知识库是空的**，需调 reindex 或检查库内数据 |
| `activeSessions` | 当前内存中的活跃会话数 | 验证记忆窗口/TTL 是否生效 |
| `tools` | 已注册的 7 个工具名 | 数量不足说明 Bean 装配有问题 |

### 6.3 `/agent/chat/message` 响应示例

```json
{
  "code": "200",
  "msg": "成功",
  "data": {
    "sessionId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "answer": "本站目前有 12 道菜谱、18 种食材、5 个分类……",
    "mode": "offline"
  }
}
```

| 字段 | 含义 |
|---|---|
| `sessionId` | 本次会话 ID。请求未传时由后端生成，**客户端应保存以延续多轮记忆** |
| `answer` | 完整回答文本（等同把所有 `token` 事件拼起来） |
| `mode` | `"online"` / `"offline"` |

---

## 7. 工具清单

7 个工具全部在 `tools/RecipeTools.java` 中注册（`toolDefs()`）与分发（`dispatch()`），**统一复用既有 Service 层**，不重复写 SQL。

| # | 工具名 | 用途 | 参数 | 必填 | 复用的能力 |
|---|---|---|---|---|---|
| 1 | `search_recipes` | 按关键词或分类搜索站内菜谱 | `keyword`(string)、`classifyId`(integer)、`pageNum`(integer,默认1)、`pageSize`(integer,默认5) | 无 | `FoodsMenuInfoService.findPage` |
| 2 | `get_recipe_detail` | 根据菜谱 ID 获取完整信息（含做法步骤与小贴士） | `recipeId`(integer) | ✅ `recipeId` | `FoodsMenuInfoService.findById` |
| 3 | `list_classifications` | 列出站内所有菜谱分类及其描述 | 无 | — | `ClassifyInfoService.findAll` |
| 4 | `search_materials` | 按关键词搜索站内食材库 | `keyword`(string) | 无 | `FoodsMaterialInfoService.findAll` + 内存过滤 |
| 5 | `recommend_recipes` | 根据用户手头食材推荐可做菜谱 | `ingredients`(array&lt;string&gt;) | ✅ `ingredients` | `FoodsMenuInfoService.findAll` + 匹配打分 |
| 6 | `get_site_stats` | 获取站内统计：菜谱数、食材数、分类数、管理员数、注册用户数 | 无 | — | 5 个 Service 的 `findAll().size()` |
| 7 | `search_knowledge` | 从知识库检索资料，回答烹饪常识/食材搭配 | `question`(string) | ✅ `question` | `RecipeKnowledgeBase.retrieve` |

### 7.1 工具返回结构

| 工具 | 返回 JSON 结构 |
|---|---|
| `search_recipes` | `{"total": 12, "items": [{"id","name","tips","classifyId","userName","description"(截断120字)}]}` |
| `get_recipe_detail` | `{"id","name","tips","classifyId","userName","description"(完整),"fileId"}` |
| `list_classifications` | `[{"id","name","description"}]` |
| `search_materials` | `{"total": 3, "items": [{"id","name","description","fileId"}]}` |
| `recommend_recipes` | `{"ingredients": [...], "total": 4, "items": [{"id","name","tips","matchedIngredients":[...],"matchCount","description"}]}`，按 `matchCount` 降序取前 5 |
| `get_site_stats` | `{"recipeCount","materialCount","classifyCount","adminCount","userCount"}` |
| `search_knowledge` | `["切片1","切片2","切片3"]` |
| **任意工具出错** | `{"error": "工具执行失败：..."}` 或 `{"error": "缺少 recipeId 参数"}` |

### 7.2 工具的 JSON Schema 构造

以 `recommend_recipes` 为例，`toolDefs()` 中实际生成的结构为：

```json
{
  "type": "function",
  "function": {
    "name": "recommend_recipes",
    "description": "根据用户手头已有的食材推荐可做的菜谱。当用户说「我有XX和YY，能做什么菜」时调用。",
    "parameters": {
      "type": "object",
      "properties": {
        "ingredients": {
          "type": "array",
          "description": "用户拥有的食材名称列表，例如 [\"土豆\",\"牛肉\"]",
          "items": { "type": "string", "description": "食材名称" }
        }
      },
      "required": ["ingredients"]
    }
  }
}
```

由 `objectSchema()` / `props()` / `stringSchema()` / `integerSchema()` / `stringArraySchema()` 五个私有辅助方法拼装，最终由 `OpenAiCompatibleClient.chat()` 包进 `tools[]` 数组，并设置 `tool_choice: "auto"`。

### 7.3 离线模式的自然语言摘要方法

`RecipeTools` 另提供 4 个 `human*()` 方法，专门供**离线规则模式**把结构化结果转成人类可读文本（在线模式下不使用）：

| 方法 | 输出形态 |
|---|---|
| `humanSearchRecipes(keyword, limit)` | `• 红烧肉（肥而不腻）` 逐行 |
| `humanSearchMaterials(keyword, limit)` | `• 土豆：富含淀粉…` 逐行（描述截断 40 字） |
| `humanListClassifications()` | `• 家常菜：适合日常…` 逐行 |
| `humanSiteStats()` | `菜谱 12 道、食材 18 种、分类 5 个、注册用户 3 人、管理员 1 人` |

---

## 8. 前端使用

### 8.1 页面与路由

| 路由 | 组件 | 入口 |
|---|---|---|
| `/admin/ai` | `views/assistant/AiChat.vue` | 管理端登录 `admin/123456` |
| `/user/ai` | `views/assistant/AiChat.vue` | 用户端登录 `user/123456` |

**同一个组件服务两个端**，区别仅在路由前缀与侧边栏菜单位置。

### 8.2 `AiChat.vue` 功能一览

| 功能区 | 说明 |
|---|---|
| 顶部状态条 | 显示在线/离线徽标、已注册工具数、知识库切片数（数据来自 `/agent/health`） |
| 重建知识库按钮 | 调 `reindexAgentKnowledge()`，成功后 `ElMessage.success("知识库已重建，共 N 条切片")` 并刷新 health |
| 新建会话按钮 | 中断当前流 → 清空后端记忆 → 换新 `sessionId` → 清空消息列表 |
| 工具能力展示 | 列出 7 个工具，用中文标签展示 |
| 消息列表 | 用户气泡 + 助手气泡；助手气泡用 `v-html="renderMarkdown(m.content)"` 渲染 |
| 工具调用可视化 | 助手气泡内的 `.tools` 区域，用 `el-tag` 展示本次调用过的工具（`start` 插入，`end` 标记 `done`） |
| 状态提示 | 气泡内显示 `status` 事件文案（如「正在思考…」） |
| 输入区 | 输入框 + 发送按钮；`sending` 期间禁用防止重复提交 |
| 快捷提问 | 4 个建议问题：站内一共有多少道菜谱？/ 有哪些菜谱分类？/ 红烧肉怎么做？/ 我有土豆和牛肉，能做什么菜？ |
| 自动滚动 | `scrollToBottom()` 用 `nextTick()` 后设置 `scrollTop` |
| 会话 ID | `crypto.randomUUID()` 生成，无该 API 时降级为 `'sess-' + Date.now() + '-' + 随机串` |

**工具中文标签映射**（`TOOL_LABELS`）：

| 工具名 | 显示标签 |
|---|---|
| `search_recipes` | 搜索菜谱 |
| `get_recipe_detail` | 查看菜谱详情 |
| `list_classifications` | 查询分类 |
| `search_materials` | 搜索食材 |
| `recommend_recipes` | 按食材推荐 |
| `get_site_stats` | 统计站内数据 |
| `search_knowledge` | 检索知识库 |

### 8.3 SSE 消费方式：为什么用 fetch 而不是 EventSource

`api/agent.js` 的 `streamAgentChat()` 使用 `fetch` + `ReadableStream`，**核心原因**：

> `EventSource` 只支持 **GET** 请求，而对话需要 **POST** 传消息体，并且需要携带 `Authorization` 头。

实现要点：

```js
const controller = new AbortController()

const res = await fetch('/api/agent/chat', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    Authorization: 'Bearer ' + (localStorage.getItem('token') || '')  // 手动带 JWT
  },
  body: JSON.stringify({ sessionId, message }),
  signal: controller.signal
})

if (res.status === 401) { handlers.onError?.('登录已过期，请重新登录'); return }

const reader = res.body.getReader()
const decoder = new TextDecoder('utf-8')
let buffer = ''

for (;;) {
  const { done, value } = await reader.read()
  if (done) break
  buffer += decoder.decode(value, { stream: true })

  let sep
  while ((sep = buffer.indexOf('\n\n')) !== -1) {   // SSE 以空行分帧
    const block = buffer.slice(0, sep)
    buffer = buffer.slice(sep + 2)
    handleBlock(block, handlers)
  }
}
```

| 工程细节 | 处理方式 |
|---|---|
| 分帧 | 以 `\n\n` 切分；`buffer` 保留不完整尾巴，跨 chunk 拼接 |
| 流结束残留 | `if (buffer.trim()) handleBlock(buffer, handlers)` 兜底处理 |
| 解码 | `TextDecoder('utf-8')` + `{ stream: true }`，避免多字节中文被截断成乱码 |
| 中断 | 返回 `() => controller.abort()`，调用方保存为 `abortFn`，新建会话/卸载时调用 |
| 中断异常 | `catch` 中判断 `e.name === 'AbortError'` 直接 return，不报错 |
| 载荷解码 | `decodePayload(raw)` 中 `JSON.parse()`；解析失败则原样返回（容错） |
| 401 处理 | 单独分支给出「登录已过期」提示 |
| 事件映射 | `token`→`onToken`、`status`→`onStatus`、`tool`→`onTool`、`error`→`onError`、`done`→`onDone` |

### 8.4 Markdown 渲染：`utils/markdown.js`

**零依赖的极简渲染器**，安全性来自**处理顺序**：

```
原文 → escapeHtml()（转义 & < > " '）→ 再解析 Markdown 语法 → 输出 HTML
```

因为 HTML 标签在解析前就已被转义，模型输出中的 `<script>` 等不会被浏览器执行。链接额外限制为**仅允许 `http/https`** 协议：

```js
s.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g,
          '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
```

**支持的语法**：

| 语法 | 输出 |
|---|---|
| ` ``` ` 围栏代码块 | `<pre class="md-pre"><code>…</code></pre>` |
| `` `code` `` | `<code class="md-code">` |
| `# ` ~ `#### ` | 标题**降级**为 `h3`~`h6`（`level = 井号数 + 2`，避免与页面标题抢层级） |
| `- ` / `* ` / `+ ` | 无序列表 `<ul><li>` |
| `1. ` / `1) ` | 有序列表 `<ol><li>` |
| `**粗体**` | `<strong>` |
| `*斜体*` | `<em>` |
| `[文字](https://…)` | 安全外链 |
| 连续文本行 | `<p>` + `<br>` |

渲染结合了 `<div class="md" v-html="renderMarkdown(m.content)">` 与组件内 `:deep()` 样式（如 `.md :deep(.md-pre)`）。

---

## 9. 验证方式

### 9.0 前置：登录获取 Token

`/login` 在 JWT 白名单内，无需鉴权。请求体需要 `name` / `password` / `level`（**`level` 必填**：1=管理员，2=普通用户），响应中 `data.token` 即 JWT。

**PowerShell（curl.exe 是 Windows 10+ 内置）**：

```powershell
# 管理员登录，把 token 存进变量
$login = curl.exe -s -X POST http://localhost:8081/login `
  -H "Content-Type: application/json" `
  -d '{\"name\":\"admin\",\"password\":\"123456\",\"level\":1}'

$login
$token = ($login | ConvertFrom-Json).data.token
$token
```

> **Windows PowerShell 转义提示**：`curl.exe` 是原生程序，JSON 中的双引号在 PowerShell 里必须写成 `\"`。若嫌麻烦，可用 `Invoke-RestMethod`：
> ```powershell
> $body = @{ name = 'admin'; password = '123456'; level = 1 } | ConvertTo-Json
> $login = Invoke-RestMethod -Uri 'http://localhost:8081/login' -Method Post `
>           -ContentType 'application/json' -Body $body
> $token = $login.data.token
> ```

**CMD（注意 JSON 引号写法）**：

```cmd
curl -s -X POST http://localhost:8081/login -H "Content-Type: application/json" -d "{\"name\":\"admin\",\"password\":\"123456\",\"level\":1}"
```

**Git Bash / WSL / Linux**：

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/login \
  -H "Content-Type: application/json" \
  -d '{"name":"admin","password":"123456","level":1}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
echo "$TOKEN"
```

预期响应：

```json
{
  "code": "200",
  "msg": "成功",
  "data": {
    "id": 1, "name": "admin", "nickName": "系统管理员", "level": 1,
    "token": "eyJhbGciOiJIUzI1NiJ9...."
  }
}
```

---

### 9.1 探活：`GET /agent/health`

```powershell
curl.exe -s http://localhost:8081/agent/health -H "Authorization: Bearer $token"
```

**判定标准**：

| 观察点 | 期望 | 不符时的排查方向 |
|---|---|---|
| HTTP 可达 | 返回 JSON | 后端是否在 8081 运行 |
| 鉴权 | 不带 Token 应返回 401 | `WebConfig` 白名单是否正确 |
| `online` | 配了 Key 为 `true`，否则 `false` | 检查 `base-url`/`api-key`/`model` 三项 |
| `tools` 数组 | **恰好 7 个** | Bean 装配问题 |
| `knowledgeChunks` | **> 0** | 为 0 说明库里没数据，或需 reindex |
| `retrievalMode` | 无向量模型时为 `"keyword"` | 符合预期即正常 |

---

### 9.2 非流式对话

```powershell
$body = '{\"sessionId\":\"test-001\",\"message\":\"站内一共有多少道菜谱？\"}'

curl.exe -s -X POST http://localhost:8081/agent/chat/message `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $token" `
  -d $body
```

预期（离线模式）：

```json
{
  "code": "200",
  "msg": "成功",
  "data": {
    "sessionId": "test-001",
    "answer": "🧠 当前是「离线规则模式」……📊 本站数据：菜谱 12 道、食材 18 种、分类 5 个……",
    "mode": "offline"
  }
}
```

预期（在线模式）：`mode` 为 `"online"`，`answer` 是模型组织过的自然语言回答。

**验证多轮记忆**（同一 `sessionId` 连续两问，第二问用代词指代第一问）：

```powershell
curl.exe -s -X POST http://localhost:8081/agent/chat/message -H "Content-Type: application/json" -H "Authorization: Bearer $token" -d '{\"sessionId\":\"mem-001\",\"message\":\"红烧肉怎么做？\"}'
curl.exe -s -X POST http://localhost:8081/agent/chat/message -H "Content-Type: application/json" -H "Authorization: Bearer $token" -d '{\"sessionId\":\"mem-001\",\"message\":\"它的食材有哪些？\"}'
```

在线模式下第二问能理解「它」指红烧肉，即说明 `ChatMemoryStore` 生效。

---

### 9.3 流式对话：观察 SSE 事件

`curl.exe` 默认会缓冲输出，**强烈建议加 `-N`**（`--no-buffer`）才能实时看到逐帧推送：

```powershell
curl.exe -N -X POST http://localhost:8081/agent/chat `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $token" `
  -d '{\"sessionId\":\"sse-001\",\"message\":\"红烧肉怎么做？\"}'
```

> **提示**：`-H "Accept: text/event-stream"` 可不加，因为该接口已在 `@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)` 中声明。

**预期输出（在线模式，逐帧实时到达）**：

```
event:status
data:"正在思考…"

event:tool
data:{"name":"search_recipes","status":"start"}

event:tool
data:{"name":"search_recipes","status":"end"}

event:status
data:"正在根据工具结果继续推理…"

event:tool
data:{"name":"get_recipe_detail","status":"start"}

event:tool
data:{"name":"get_recipe_detail","status":"end"}

event:token
data:"## 红烧肉"

event:token
data:"\n\n**食材**：五"

...

event:done
data:{}
```

**预期输出（离线模式）**：

```
event:status
data:"离线规则模式：未配置大模型，使用站内检索作答"

event:token
data:"🧠 当前是「离线规则模式」"

...

event:done
data:{}
```

**关键观察点**：

1. `tool` 事件**成对出现**（`start` 紧跟 `end`），说明工具调用闭环正常
2. 多个不同工具名出现，说明 ReAct **多轮循环**生效
3. `token` 事件的 `data:` 是**带引号的 JSON 字符串**，其中的 `\n` 是转义后的字面量——**这正是防丢字设计生效的证据**
4. 最后一帧必为 `done`，即使中途出错
5. 在线模式下，工作目录 `backend/` 的控制台应能看到 `Agent 调用工具：search_recipes 参数={"keyword":"红烧肉"}` 的 INFO 日志

**错误路径验证**（观察 `error` 事件）：

```powershell
# 空消息 → 应收到 status「请先输入你的问题～」后 done
curl.exe -N -X POST http://localhost:8081/agent/chat -H "Content-Type: application/json" -H "Authorization: Bearer $token" -d '{\"sessionId\":\"e1\",\"message\":\"\"}'

# 故意填错 Key 后重启 → 应收到 error 事件：大模型返回 401：...
curl.exe -N -X POST http://localhost:8081/agent/chat -H "Content-Type: application/json" -H "Authorization: Bearer $token" -d '{\"sessionId\":\"e2\",\"message\":\"你好\"}'
```

**无 Token 验证鉴权**（应返回 401）：

```powershell
curl.exe -i -X POST http://localhost:8081/agent/chat -H "Content-Type: application/json" -d '{\"sessionId\":\"x\",\"message\":\"hi\"}'
```

---

### 9.4 清空会话记忆

```powershell
curl.exe -s -X POST http://localhost:8081/agent/session/clear `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $token" `
  -d '{\"sessionId\":\"mem-001\"}'
```

清空后再用同一 `sessionId` 问「它的食材有哪些？」，应**不再知道「它」指什么**——即验证记忆确实被清除。

也可用 `health` 的 `activeSessions` 字段观察会话数变化。

---

### 9.5 重建 RAG 知识库

```powershell
curl.exe -s -X POST http://localhost:8081/agent/knowledge/reindex -H "Authorization: Bearer $token"
```

预期：

```json
{
  "code": "200",
  "msg": "成功",
  "data": { "knowledgeChunks": 42, "retrievalMode": "keyword" }
}
```

**使用时机**：通过后台新增/修改菜谱、食材、分类后，知识库不会自动刷新（它是内存态），需调用此接口或重启后端。重建后 `health` 的 `knowledgeChunks` 应同步变化。

**验证检索是否真的工作**：

```powershell
# 问一个知识性问题，在线模式下应触发 search_knowledge 工具
curl.exe -N -X POST http://localhost:8081/agent/chat `
  -H "Content-Type: application/json" -H "Authorization: Bearer $token" `
  -d '{\"sessionId\":\"kb-001\",\"message\":\"土豆和牛肉搭配有什么讲究？\"}'
```

在输出中应看到 `event:tool` + `data:{"name":"search_knowledge","status":"start"}`。

---

### 9.6 建议的回归测试问题清单

覆盖 7 个工具、两种模式、记忆、降级与异常路径。**「期望工具」列可用 SSE 的 `tool` 事件核对**。

| # | 测试问题 | 期望触发的工具 | 验证点 |
|---|---|---|---|
| 1 | 站内一共有多少道菜谱？ | `get_site_stats` | 工具调用 + 数字与数据库一致 |
| 2 | 有哪些菜谱分类？ | `list_classifications` | 分类名与后台一致 |
| 3 | 红烧肉怎么做？ | `search_recipes` → `get_recipe_detail` | **两段式多轮调用**，验证 ReAct 循环 |
| 4 | 搜索一下汤类的菜 | `search_recipes`（带 keyword） | 关键词参数传递 |
| 5 | 按分类看看家常菜有哪些 | `list_classifications` → `search_recipes`（带 classifyId） | **跨工具参数传递**（模型从上一个工具结果取 ID） |
| 6 | 土豆有什么营养？ | `search_materials` | 食材检索 |
| 7 | 我有土豆和牛肉，能做什么菜？ | `recommend_recipes`（带 ingredients 数组） | **数组参数**解析 |
| 8 | 土豆和牛肉搭配有什么讲究？ | `search_knowledge` | RAG 检索命中 |
| 9 | 红烧肉怎么做？→ 它的食材有哪些？ | 第 2 问应能解析「它」 | **Chat Memory 多轮记忆** |
| 10 | 你好 | 不调工具，直接回答 | 闲聊路径不误触工具 |
| 11 | 站内有佛跳墙吗？ | `search_recipes` 返回空后如实告知 | **不编造**（Prompt 准则 7） |
| 12 | 火星上的菜谱有哪些？ | 工具无结果 → 诚实说明 | 幻觉抑制 |
| 13 | 清空记忆后再问「它是什么」 | — | **记忆清除生效** |
| 14 | 不配置 Key 跑问题 1~3 | 离线模式，走 `human*()` 方法 | **优雅降级** |
| 15 | 把 `embedding-model` 设为错误值 | 语义检索失败 → 回退关键词 | **检索回退**（看日志 warn） |
| 16 | 把 `api-key` 设为无效值 | `error` 事件「大模型返回 401」 | 错误事件路径，前端不白屏 |
| 17 | 连续快速发送多条消息 | — | 线程池并发 + 会话隔离 |
| 18 | 发送途中关闭页面 | — | `AbortError` 静默处理，后端无异常堆栈 |
| 19 | 新增一道菜谱后调 reindex，再提问 | `search_recipes` | **知识库刷新生效** |
| 20 | 回答中包含 Markdown 列表/加粗 | — | 前端渲染正确，且 **`\n` 未被 SSE 分帧破坏**（无丢字） |

---

## 10. 设计决策：为什么自研而不用 LangChain4j / Spring AI

### 10.1 决策结论

**主动选择零第三方 AI 依赖**，仅用 JDK 内置 `java.net.http.HttpClient` + Spring Boot 自带的 Jackson + Spring 的 `SseEmitter` 实现完整 Agent 运行时。

### 10.2 原因

**原因一：Maven 环境受限，无法验证新依赖**

引入 LangChain4j / Spring AI 会带来一串传递依赖（各自的 HTTP 客户端、JSON 库、向量库适配、Reactor 等）。在当前的构建环境下无法可靠地完成依赖解析与版本兼容性验证，存在「引入即构建失败」的风险。而 `HttpClient` / Jackson / `SseEmitter` 都是**已经在 classpath 上**的东西——用它们，构建风险为零。

**原因二：主动想理解底层机制**

ReAct 循环、Function Calling 协议、RAG 检索、SSE 分帧这些机制，如果用框架只是一行注解（`@Tool`、`AiServices`），就很难讲清楚「模型返回的 assistant 消息为什么要原样回灌」「SSE 载荷为什么必须 JSON 编码」这类真正决定成败的细节。手写一遍之后：

- Function Calling 的本质就是 **OpenAI tools 协议的一段 JSON**，协议走通了，任何兼容服务都能接
- ReAct 的本质就是一个 **while 循环 + 消息数组累积**，轮数上限是防死循环的必要工程约束
- RAG 的本质就是 **检索 + 拼 prompt**，向量库不是必需品
- SSE 的本质就是 **`event:` / `data:` + 空行分帧**，踩过「换行破坏分帧」的坑才算真懂

**原因三：可迁移性强，无供应商锁定**

代码走的是 **OpenAI 兼容协议**，因此 DeepSeek / 通义千问 / OpenAI / Ollama 之间**只改 3 行配置**即可切换。如果绑定某个框架的原生 SDK，切换成本反而更高。

**原因四：契合项目体量**

Agent 部分仅 10 个文件，总规模约 1600 行。这个体量下，一个框架带来的抽象层级（`ChatLanguageModel` → `ChatClient` → `AiServices` → `ToolProvider`）反而是负资产——排查问题时要在多层封装之间跳转。

### 10.3 概念对照表

自研实现与两大主流框架的对应关系。**看完这张表可以确认：本项目没有缺失任何核心能力，只是换了实现方式。**

| 本项目 | LangChain4j | Spring AI |
|---|---|---|
| `OpenAiCompatibleClient` | `OpenAiChatModel` / `OpenAiEmbeddingModel` | `OpenAiChatModel` / `OpenAiEmbeddingModel` |
| `ReActAgent.online()` | `AiServices` | `ChatClient` |
| `ToolDef` + `RecipeTools` | `@Tool` 注解 | `@Tool` 注解 |
| `ChatMemoryStore` | `MessageWindowChatMemory` | `ChatMemory` |
| `RecipeKnowledgeBase` | `EmbeddingStore` + `EmbeddingStoreContentRetriever` | `VectorStore` + `QuestionAnswerAdvisor` |
| `SseEmitterSink` | `TokenStream` | `Flux<String>` |

补充对应关系：

| 本项目 | LangChain4j | Spring AI |
|---|---|---|
| `AgentProperties`（`ai.*`） | `ChatModel` 构造参数 / `application.properties` | `spring.ai.openai.*` |
| `RecipeTools.dispatch()` | `ToolProvider` / `@Tool` 方法反射调用 | `ToolCallback` |
| `RecipeKnowledgeBase.reindex()` | `EmbeddingStore.addAll()` + `DocumentSplitter` | `VectorStore.add()` + `TokenTextSplitter` |
| `RecipeKnowledgeBase.keywordRetrieve()`（关键词通道） | 无直接对应（依赖 EmbeddingStore） | 无直接对应（依赖 VectorStore） |
| 离线规则模式 | 无直接对应 | 无直接对应 |

> **注意最后两行**：**关键词检索通道**与**离线规则模式**是本项目自研带来的额外收益——主流框架默认假定「有模型可用」，没有 API Key 就直接不可用；本项目则保证了**无 Key 也能演示完整链路**。

### 10.4 如需切换到 LangChain4j 的迁移路径

如果后续条件允许（即 Maven 环境可正常解析依赖），迁移是一个**渐进式**过程，不需要推倒重来。

**第 1 步：引入依赖**

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.0.0</version>
</dependency>
```

**第 2 步：用 `@Tool` 重写 `RecipeTools`**

把现有的 `toolDefs()` 手写 Schema 换成注解声明——**方法体（真正的业务逻辑）可以原封不动保留**：

```java
// 迁移前：手写 JSON Schema
defs.add(new ToolDef("search_recipes",
    "按关键词或分类搜索站内菜谱…",
    objectSchema(props(
        "keyword", stringSchema("搜索关键词…"),
        "classifyId", integerSchema("分类ID…"),
        "pageNum", integerSchema("页码…"),
        "pageSize", integerSchema("每页条数…")
    ), List.of())));

// 迁移后：注解声明
@Tool("按关键词或分类搜索站内菜谱。当用户想找某道菜、某类菜时调用。")
public String searchRecipes(
        @P("搜索关键词，例如「红烧肉」「汤」") String keyword,
        @P("分类ID，取自 list_classifications") Long classifyId,
        @P("页码，从 1 开始") Integer pageNum,
        @P("每页条数") Integer pageSize) {
    // ... 原有的 Service 调用逻辑保持不变
    return mapper.writeValueAsString(result);
}
```

7 个工具逐一照此改造即可。`dispatch()` 整个 switch 可以删除（由框架反射调用）。

**第 3 步：用 `AiServices` 替代 `ReActAgent` 的手写循环**

```java
interface RecipeAssistant {
    String chat(@MemoryId String sessionId, @UserMessage String message);
}

RecipeAssistant assistant = AiServices.builder(RecipeAssistant.class)
        .chatLanguageModel(chatModel)
        .tools(new RecipeTools(...))
        .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(10))
        .build();
```

`ReActAgent.online()` 中的 `for (int round = 0; round < maxRounds; round++)` 循环、消息数组拼装、`tool_calls` 解析、Observation 回灌全部由框架接管。

**第 4 步：`RecipeKnowledgeBase` 迁移为 `EmbeddingStore`**

```java
EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
        .embeddingStore(store)          // InMemoryEmbeddingStore
        .embeddingModel(embeddingModel)
        .maxResults(props.getRagTopK())
        .minScore(0.15)                 // 对应本项目的余弦阈值
        .build();
```

> 保留建议：**关键词检索通道可以保留为降级路径**，在 EmbeddingStore 不可用时兜底。

**第 5 步：Controller 与 SSE 协议保持不变**

这是本项目分层带来的最大好处——`AgentController` 的 5 个接口、SSE 的 5 类事件名与 JSON 载荷格式、前端的 `agent.js` **全部无需修改**。只需把 `ReActAgent` 内部的实现替换掉，`AgentEventSink` 抽象层继续作为 SSE 输出的出口。

**迁移工作量评估**：

| 文件 | 改动程度 | 说明 |
|---|---|---|
| `pom.xml` | 新增 2 个依赖 | — |
| `RecipeTools.java` | 方法签名加注解，`toolDefs()`/`dispatch()` 可删 | 业务逻辑不变 |
| `ReActAgent.java` | 循环体替换为 `AiServices` 调用 | `AgentEventSink` 保留 |
| `RecipeKnowledgeBase.java` | 检索部分替换为 Retriever | 关键词通道可保留 |
| `ChatMemoryStore.java` | 可替换为 `MessageWindowChatMemory` | — |
| `OpenAiCompatibleClient.java` | 可替换为 `OpenAiChatModel` | — |
| `AgentController.java` | **不变** | 分层解耦的红利 |
| 前端全部文件 | **不变** | 协议未变 |
| `application.yml` | `ai.*` 可映射到框架配置 | 建议保留自有配置项便于切换 |

---

## 11. 设计亮点

### 亮点 1：零第三方 AI 依赖，用 JDK + 框架自带能力实现完整 Agent

`pom.xml` 未新增任何依赖。LLM 通信、JSON 解析、流式输出全部使用已在 classpath 上的能力：`java.net.http.HttpClient`（JDK 11+ 内置）、Jackson（Spring Boot 自带）、`SseEmitter`（Spring MVC 内置）。**构建风险为零，部署包体积不增加**，同时把 Function Calling / ReAct / RAG 的实现细节完全暴露在可控的代码里。

### 亮点 2：SSE 载荷统一 JSON 编码，从根本上解决「换行破坏分帧」丢字问题

SSE 用空行分帧，而 Markdown 回答必然含换行——朴素实现会把一个回答切碎，前端表现为丢字错乱。本项目在 `SseEmitterSink.encode()` 中统一 `mapper.writeValueAsString()`，把 `\n` 转义为字面量 `\\n`，前端 `decodePayload()` 对称还原。**这是一个只有真正手写过 SSE 才会踩到并解决的坑**。

### 亮点 3：四层优雅降级，无 Key 也能完整演示

`online()` 判定凭据不全 → **离线规则模式**（基于站内数据作答）；无 `embedding-model` → **关键词检索**；语义检索异常 → **自动回退关键词**；工具异常 → 返回 `{"error":...}` 而非抛异常。这四层降级意味着：**评审者 clone 下来不配置任何 Key 就能跑通全流程**，不会出现白屏或报错，演示成本极低。

### 亮点 4：`AgentEventSink` 抽象，一套逻辑同时支撑流式与非流式

`ReActAgent` 不直接依赖 `SseEmitter`，而是面向 `AgentEventSink` 接口编程，提供两个实现：`SseEmitterSink`（推送 SSE）与 `StringBuilderSink`（累积字符串）。因此 `POST /agent/chat`（流式）与 `POST /agent/chat/message`（非流式）**复用同一份推理逻辑**，测试时可以先跑通非流式路径再调前端，排障效率显著提升。

### 亮点 5：工具层复用既有 Service，零重复 SQL

`RecipeTools` 的 7 个工具全部通过构造器注入复用 `FoodsMenuInfoService` / `FoodsMaterialInfoService` / `ClassifyInfoService` / `AdminInfoService` / `UserInfoService`，**没有一条新写的 SQL、没有一个新的 Mapper**。原有业务能力「一处实现，两处入口」（Controller + Agent Tool），改造对原有代码侵入为零。

### 亮点 6：关键词检索通道自带中文优化

在没有向量模型的情况下，`RecipeKnowledgeBase.keywordScore()` 实现了分层打分：整串命中 +6.0、**汉字二元组（bigram）** 每个 +1.5、单字 +0.5、英文词（长度 ≥2）+2.0。二元组是对中文的针对性优化——比单字精确、比整串宽松，无需分词库即可获得可用的召回质量。

### 亮点 7：OpenAI 兼容协议带来零锁定切换

`base-url` + `/chat/completions` 与 `/embeddings` 的标准化拼接，加上独立的 `embedding-base-url`（可指向与对话服务不同的厂商），使得 DeepSeek / 通义千问 / OpenAI / Ollama 之间切换**只需要改配置**。本地 Ollama 更是可以零成本离线跑通在线模式。

### 亮点 8：并发与资源安全的多重保障

- `agentExecutor` 用 **4 线程守护池**（`setDaemon(true)`），既不阻塞 Tomcat 请求线程，也不阻碍应用关闭
- `ChatMemoryStore` 用 `ConcurrentHashMap` + 对 `ArrayDeque` 加 `synchronized`，兼顾并发与一致性
- `RecipeKnowledgeBase.chunkEmbeddings` 用 `volatile` + 双重检查锁，避免并发重复向量化
- 记忆有**窗口 + TTL 双重淘汰**，防止内存无限增长
- 前端 `AbortController` + 后端 `catch` 静默，断连不产生异常堆栈

---

## 12. 后续扩展路线

| 方向 | 现状 | 可演进方案 | 涉及改动 |
|---|---|---|---|
| **记忆持久化** | 内存 `ConcurrentHashMap`，重启即失，多实例不共享 | 迁移到 **Redis**（`StringRedisTemplate` + List 或 Hash 存消息窗口，`EXPIRE` 实现 TTL）；或落库新建 `chat_memory` 表 | 只需重写 `ChatMemoryStore` 内部实现，对外接口不变 |
| **向量持久化** | 内存 `CopyOnWriteArrayList`，重启需重建 | 接入向量库（Redis Stack / Milvus / Qdrant / pgvector），或先用「向量落库 + 启动加载」的轻量方案 | 替换 `RecipeKnowledgeBase` 的存储与检索层 |
| **知识库自动刷新** | 需手动调 `/agent/knowledge/reindex` | 监听菜谱/食材/分类的增删改事件（Spring `ApplicationEvent`）后自动 reindex；或加定时任务 + 版本号比对 | 新增事件监听器，`RecipeTools` 侧发布事件 |
| **真流式输出** | 目前是「拿到完整答案后按 6 字符切片 + sleep 8ms」模拟打字机 | 接入 LLM 的 **stream=true** SSE 响应，边收边转发，首字延迟大幅降低 | 在 `OpenAiCompatibleClient` 增加流式方法（`BodyHandlers.ofLines()`），`SseEmitterSink` 改为直通 |
| **多模态** | 纯文本 | 图片上传（项目已有文件上传能力）→ 多模态模型识别食材照片 → 反查菜谱；或给菜谱生成配图 | 新增 `analyze_image` 工具 + 前端图片上传入口 |
| **工作流编排** | 单个 Agent 串行 ReAct | 引入多 Agent 协作（如「检索 Agent + 推荐 Agent + 营养分析 Agent」）或状态机编排（复杂流程拆成显式步骤） | 新增编排层，`ReActAgent` 作为可复用节点 |
| **可观测性** | `log.info` 打印工具调用 | 结构化埋点：每轮的 token 消耗、工具调用耗时、轮数分布、失败率；接入 Micrometer + Prometheus + Grafana；或落库做对话审计 | 在 `ReActAgent` 与 `AgentController` 增加指标埋点 |
| **成本与限流** | 无 | 按用户/会话限流；单日 token 配额；超限提示；缓存高频问题的答案 | 新增拦截器 + 计数存储 |
| **安全加固** | JWT + HTML 转义 | Prompt 注入防护（工具白名单、参数校验、敏感操作二次确认）；输出内容审核；工具调用审计日志 | 在 `RecipeTools.dispatch()` 与 `markdown.js` 增加校验层 |
| **评测体系** | 手工回归（本文档 9.6 节 20 条） | 建立自动化评测集：固定问题集 + 期望工具序列 + 期望关键字段，CI 中跑回归 | 新增测试用例与断言脚本 |
| **前端体验** | 基础对话 | 消息持久化到 localStorage；历史会话列表；回答内引用来源卡片（展示命中的知识切片）；一键复制 | 仅前端改动 |

---

## 附录 A：常见问题排查（FAQ）

| 现象 | 可能原因 | 排查方法 |
|---|---|---|
| `/agent/**` 返回 401 | 未带 Token 或 Token 过期 | 检查 `Authorization: Bearer <token>` 头；重新登录取 Token |
| `online: false`，一直是离线模式 | `base-url` / `api-key` / `model` 任一为空 | 调 `/agent/health` 查看这三项；检查 `DEEPSEEK_API_KEY` 环境变量是否生效；检查 `spring.profiles.active` 是否加载了 `application-local.yml` |
| 前端丢字、回答断句错乱 | SSE 载荷未做 JSON 编码 | 确认 `SseEmitterSink.encode()` 存在且被所有事件使用；前端 `decodePayload()` 是否调用 |
| `knowledgeChunks: 0` | 数据库为空，或知识库未建 | 确认 `init.sql` 已导入；调 `/agent/knowledge/reindex` 后重试 |
| `retrievalMode` 一直是 `keyword` | 未配置 `embedding-model`，或所用服务商不支持 embeddings | DeepSeek 属预期行为；通义/OpenAI 用户检查 `embedding-model` 拼写 |
| 语义检索报错但对话仍可用 | 触发了自动回退（设计如此） | 查看后端日志中的 `语义检索失败，回退关键词检索：...` |
| 工具没被调用 | 模型能力不足或 Prompt 未被遵循 | 确认 `tools` 数组有 7 项；查看日志是否有 `Agent 调用工具：...`；尝试更明确的提问 |
| `大模型返回 401：...` | API Key 无效或额度耗尽 | 核对 Key；确认 `base-url` 路径前缀（如 `/v1`）正确 |
| `大模型返回 404：...` | `base-url` 拼接错误 | 记住规则：代码会拼 `{base-url}/chat/completions`，所以 `base-url` **不能**以 `/chat/completions` 结尾 |
| 达到轮数上限仍无答案 | `max-iterations` 太小或问题过于复杂 | 调大 `ai.max-iterations`；或把问题拆细 |
| 回答被截断 | `max-tokens` 太小 | 调大 `ai.max-tokens` |
| 前端 404 / 接口连不上 | 端口不一致 | 后端须为 8081，`vite.config.js` 的 proxy `target` 须为 `http://localhost:8081`，前端端口 5174 |
| 多轮对话「失忆」 | 超过 `memory-window` 或超过 `memory-ttl-minutes` | 调大对应配置；确认前端每次请求携带**同一个** `sessionId` |
| 新增菜谱后 AI 查不到 | 知识库是内存态，未刷新 | 调 `POST /agent/knowledge/reindex` |
| 关闭页面后后端刷异常日志 | 客户端断开 | 本项目已静默处理；若仍有，检查是否走了未捕获的路径 |

---

## 附录 B：一键验证脚本（PowerShell）

保存为 `verify-agent.ps1` 放在项目根目录，按顺序跑完 5 个接口：

```powershell
# 需先启动后端（8081）。用法：powershell -ExecutionPolicy Bypass -File verify-agent.ps1
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8081'

Write-Host "`n[1/5] 登录获取 Token ..." -ForegroundColor Cyan
$body = @{ name = 'admin'; password = '123456'; level = 1 } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$base/login" -Method Post -ContentType 'application/json' -Body $body
$token = $login.data.token
if (-not $token) { throw '登录失败，未拿到 token' }
$headers = @{ Authorization = "Bearer $token" }
Write-Host "  Token 获取成功：$($token.Substring(0, 20))..." -ForegroundColor Green

Write-Host "`n[2/5] 健康检查 /agent/health ..." -ForegroundColor Cyan
$health = Invoke-RestMethod -Uri "$base/agent/health" -Headers $headers
$h = $health.data
Write-Host "  enabled        : $($h.enabled)"
Write-Host "  online         : $($h.online)"
Write-Host "  mode           : $($h.mode)"
Write-Host "  model          : $($h.model)"
Write-Host "  retrievalMode  : $($h.retrievalMode)"
Write-Host "  knowledgeChunks: $($h.knowledgeChunks)"
Write-Host "  tools ($($h.tools.Count))     : $($h.tools -join ', ')"

Write-Host "`n[3/5] 非流式对话 /agent/chat/message ..." -ForegroundColor Cyan
$chatBody = @{ sessionId = 'verify-001'; message = '站内一共有多少道菜谱？' } | ConvertTo-Json
$chat = Invoke-RestMethod -Uri "$base/agent/chat/message" -Method Post -ContentType 'application/json' -Headers $headers -Body $chatBody
Write-Host "  mode  : $($chat.data.mode)"
Write-Host "  answer: $($chat.data.answer.Substring(0, [Math]::Min(120, $chat.data.answer.Length)))..."

Write-Host "`n[4/5] 重建知识库 /agent/knowledge/reindex ..." -ForegroundColor Cyan
$re = Invoke-RestMethod -Uri "$base/agent/knowledge/reindex" -Method Post -Headers $headers
Write-Host "  knowledgeChunks: $($re.data.knowledgeChunks)"
Write-Host "  retrievalMode  : $($re.data.retrievalMode)"

Write-Host "`n[5/5] 流式对话 /agent/chat（只打印前 20 行 SSE 帧）..." -ForegroundColor Cyan
$tmp = [System.IO.Path]::GetTempFileName()
Start-Process -FilePath 'curl.exe' -ArgumentList @(
  '-N','-s','-X','POST',"$base/agent/chat",
  '-H','Content-Type: application/json',
  '-H',"Authorization: Bearer $token",
  '-d','{\"sessionId\":\"verify-sse\",\"message\":\"有哪些菜谱分类？\"}'
) -RedirectStandardOutput $tmp -NoNewWindow -Wait
Get-Content $tmp -TotalCount 20 | ForEach-Object { Write-Host "  $_" }
Remove-Item $tmp -ErrorAction SilentlyContinue

Write-Host "`n验证完成。" -ForegroundColor Green
```

---

## 附录 C：术语速查

| 术语 | 全称 / 含义 | 本项目对应 |
|---|---|---|
| **Agent** | 智能体：能自主推理并调用工具完成任务的系统 | `ReActAgent` |
| **ReAct** | Reason + Act：推理与行动交替的循环范式 | `ReActAgent.online()` 的 for 循环 |
| **Function Calling** | 模型以结构化方式请求调用本地函数 | `ToolDef` + `RecipeTools` |
| **Tools** | 暴露给模型的函数清单（OpenAI 协议） | `toolDefs()` 返回的 7 个 `ToolDef` |
| **Observation** | 工具执行结果，回灌给模型作为下一轮输入 | `dispatch()` 返回的 JSON 字符串 |
| **Final Answer** | 模型给出的最终回答，终止循环 | `msg.path("content")` |
| **RAG** | Retrieval-Augmented Generation，检索增强生成 | `RecipeKnowledgeBase` |
| **Embedding** | 文本向量化 | `OpenAiCompatibleClient.embed()` |
| **余弦相似度** | 两个向量的夹角余弦，衡量语义相近程度 | `RecipeKnowledgeBase.cosine()`，阈值 0.15 |
| **Chat Memory** | 多轮对话上下文记忆 | `ChatMemoryStore` |
| **SSE** | Server-Sent Events，服务端单向流式推送 | `SseEmitter` + `SseEmitterSink` |
| **打字机效果** | 文本分片渐进显示 | `tokenStream()` 每 6 字符一片 |
| **Prompt Engineering** | 提示词工程 | `buildSystemPrompt()` 的 7 条准则 |
| **优雅降级** | 依赖不可用时保持核心功能可用 | 离线规则模式 + 检索回退 |
| **JWT** | JSON Web Token，无状态鉴权 | 项目原有，`/agent/**` 复用 |

---

*本文档描述的全部技术细节均可在源码中核对。如发现文档与代码不一致，以代码为准。*
