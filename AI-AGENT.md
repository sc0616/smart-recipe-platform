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

