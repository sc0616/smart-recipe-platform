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

