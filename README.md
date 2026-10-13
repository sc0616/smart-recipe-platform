# 智能美食菜谱平台

> 一个集成 **AI Agent** 的前后端分离美食菜谱平台 —— 用户可以直接说「我有土豆和牛肉，能做什么菜？」，
> 由大模型自主调用站内业务接口查询真实数据并给出推荐。

<p>
  <img src="https://img.shields.io/badge/Java-17-orange" alt="Java">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Vue-3.4-42b883" alt="Vue">
  <img src="https://img.shields.io/badge/MySQL-8.x-blue" alt="MySQL">
  <img src="https://img.shields.io/badge/AI%20Agent-ReAct-purple" alt="ReAct">
</p>

---

## 项目简介

传统的菜谱系统只能让用户「点按钮、填表单」，无法理解「我有土豆和牛肉」这类自然语言意图。
本项目在完整的菜谱业务系统之上，**自研了一套轻量 AI Agent 运行时**，让模型能够真正驱动业务数据：

- 用户提问 → 模型**自主决定**调用哪个业务工具 → 查询**站内真实数据** → 基于结果生成回答
- 不是「加个聊天框」，而是让模型**真正读得到、用得上**系统的业务能力

**核心特色**：AI Agent 部分**零第三方 AI 依赖**，仅使用 JDK 内置 `HttpClient` 与项目既有的 Jackson，
通过 **OpenAI 兼容协议**对接大模型（DeepSeek / 通义千问 / OpenAI / Ollama 均可）。

---

## 功能特性

### 传统业务功能

| 模块 | 功能 |
| --- | --- |
| 账号 | 注册、登录、修改密码（JWT 无状态认证） |
| 菜谱 | 浏览、搜索、分类筛选、详情查看、发布 / 编辑 / 删除 |
| 食材 | 浏览、搜索、发布 / 编辑 / 删除 |
| 互动 | 点赞、收藏 |
| 后台 | 数据看板（ECharts）、用户管理、分类管理、内容管理 |

### AI 智能体功能

| 能力 | 用户表达示例 | 模型行为 |
| --- | --- | --- |
| 按食材找菜 | 「我有土豆和牛肉，能做什么菜？」 | 调用 `recommend_recipes` 匹配 |
| 菜谱搜索 | 「有哪些汤类的菜？」 | 调用 `search_recipes` / `list_classifications` |
| 做法咨询 | 「红烧肉怎么做？」 | 搜索后调用 `get_recipe_detail` |
| 数据统计 | 「站内一共有多少道菜谱？」 | 调用 `get_site_stats` |
| 烹饪知识 | 「土豆和牛肉能一起吃吗？」 | 调用 `search_knowledge` 检索 |
| 多轮追问 | 「那第二道菜怎么做？」 | 基于会话记忆理解指代 |

**交互特性**：SSE 流式输出（逐字显示）、思考状态提示、工具调用过程实时可见。

---

## 技术栈

**后端**：Java 17 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.7 · MySQL 8 · JWT (jjwt) · Thumbnailator · Hutool

**前端**：Vue 3.4 · Vite 5 · Vue Router · Pinia · Axios · Element Plus · ECharts

**AI Agent**：ReAct 范式 · Function Calling · RAG 检索增强 · SSE 流式输出 · 多轮会话记忆

---

## 快速开始

### 环境要求

- JDK 17+
- MySQL 8.x
- Node.js 16+
- Maven

### 1. 初始化数据库

```bash
mysql -uroot -p < sql/init.sql
```

### 2. 启动后端（端口 8081）

```bash
cd backend
mvn clean spring-boot:run
```

> 数据库账号默认为 `root / 123456`，可通过环境变量 `DB_USERNAME` / `DB_PASSWORD` 覆盖。

### 3. 启动前端（端口 5174）

```bash
cd frontend
npm install
npm run dev
```

访问 **http://localhost:5174**

**默认账号**：管理员 `admin / 123456` · 普通用户 `user / 123456`

> 💡 **无需配置 API Key 即可运行** —— 系统会自动进入「离线规则模式」，
> 基于站内数据检索回答问题，所有功能均可演示。

---

## 启用完整 AI Agent（可选）

默认处于离线模式。配置任一 OpenAI 兼容服务即可升级为完整 Agent，**无需修改代码**：

```yaml
ai:
  enabled: true
  base-url: https://api.deepseek.com    # 或通义千问 / OpenAI / Ollama
  api-key: ${DEEPSEEK_API_KEY:}         # 推荐用环境变量，避免泄露
  model: deepseek-chat
  embedding-model: ""                   # 留空则 RAG 使用关键词检索
  max-iterations: 6                     # ReAct 最大轮数
  memory-window: 10                     # 会话记忆窗口
```

**常见服务商配置**

| 服务商 | base-url | model | embedding-model |
| --- | --- | --- | --- |
| DeepSeek | `https://api.deepseek.com` | `deepseek-chat` | 不支持，留空 |
| 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` | `text-embedding-v3` |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` | `text-embedding-3-small` |
| Ollama 本地 | `http://localhost:11434/v1` | `qwen2.5:7b` | 留空 |

> ⚠️ **请勿将真实 API Key 提交到仓库**。
> 建议放在 `application-local.yml`（已被 `.gitignore` 排除）或使用环境变量。

---

## 项目架构

```
┌─────────────────────────────────────────────┐
│         浏览器（Vue 3 SPA）                  │
│  用户端 / 管理端 + AI 对话页                  │
└──────────────────┬──────────────────────────┘
                   │ HTTP + SSE（Vite 代理 /api）
┌──────────────────▼──────────────────────────┐
│      Spring Boot 后端（:8081）               │
│                                             │
│  ┌──────────────┐    ┌───────────────────┐  │
│  │  业务模块     │◄───┤   AI Agent 模块    │  │
│  │  Controller  │    │  ReActAgent       │  │
│  │  Service     │复用 │  RecipeTools(×7)  │  │
│  │  Mapper      │    │  KnowledgeBase    │  │
│  │  JWT 拦截器   │    │  ChatMemory       │  │
│  └──────────────┘    └────────┬──────────┘  │
└───────────────────────────────┼─────────────┘
                                │ OpenAI 兼容协议
                    ┌───────────▼───────────┐
                    │ DeepSeek / 通义 / ... │
                    └───────────────────────┘
```

**设计要点**：Agent 模块**单向依赖**既有 Service 层，把原有业务能力「工具化」，对业务代码**零侵入**。

---

## AI Agent 实现要点

### ReAct 循环

```
Thought（思考）→ Action（调用工具）→ Observation（观察结果）→ 再 Thought → Final Answer
```

实际执行示例：

```
用户：我有土豆和牛肉，能做什么菜？

第 1 轮  Action      : recommend_recipes({"ingredients":["土豆","牛肉"]})   ← 模型自主决定
         Observation : {"total":2,"items":[{"name":"土豆炖牛肉","matchCount":2}]}

第 2 轮  Final Answer: 推荐你做土豆炖牛肉，这道菜……
```

关键在于**调哪个工具、传什么参数由模型自主决定**，而非代码写死的 if-else。

### 7 个 Agent 工具

| 工具 | 用途 | 参数 |
| --- | --- | --- |
| `search_recipes` | 搜索菜谱 | `keyword?` `classifyId?` `pageNum?` `pageSize?` |
| `get_recipe_detail` | 获取完整做法 | `recipeId` |
| `list_classifications` | 列出分类 | — |
| `search_materials` | 搜索食材 | `keyword?` |
| `recommend_recipes` | 按食材推荐 | `ingredients` |
| `get_site_stats` | 站内统计 | — |
| `search_knowledge` | 知识库检索 | `question` |

### RAG 双路检索

| 模式 | 触发条件 | 实现 |
| --- | --- | --- |
| 语义检索 | 配置了向量模型 | Embedding + 余弦相似度 |
| 关键词检索 | 未配置向量模型 | 汉字二元组 + 英文词匹配 |

**为什么双路**：DeepSeek 等不提供 Embedding 接口，若强依赖向量检索则默认配置下不可用。
关键词检索作为零依赖兜底；语义检索失败时也会**自动回退**。

### 优雅降级

| 场景 | 降级行为 |
| --- | --- |
| 未配置 API Key | 离线规则模式，基于站内检索回答 |
| 未配置向量模型 | RAG 使用关键词检索 |
| 向量服务异常 | 回退关键词检索 |
| 工具执行失败 | 返回结构化 error，交模型处理 |

项目 clone 后**零配置即可演示**，配置后自动升级，全程无需改代码。

---

## 接口一览

### AI Agent 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/agent/chat` | SSE 流式对话 |
| POST | `/agent/chat/message` | 非流式对话 |
| POST | `/agent/session/clear` | 清空会话记忆 |
| GET | `/agent/health` | Agent 状态探测 |
| POST | `/agent/knowledge/reindex` | 重建 RAG 知识库 |

**SSE 事件协议**

| 事件 | 含义 |
| --- | --- |
| `status` | 思考状态 |
| `tool` | 工具调用开始 / 结束 |
| `token` | 回答文本片段 |
| `error` | 错误信息 |
| `done` | 本次回答结束 |

---

## 项目结构

```
smart-recipe-platform/
├── backend/                        Spring Boot 后端
│   └── src/main/java/com/example/delicious/
│       ├── common/                 通用模块（Result / JWT / 异常处理）
│       ├── controller/             业务接口
│       ├── service/                业务逻辑
│       ├── mapper/                 数据访问
│       ├── entity/                 实体类
│       └── agent/                  ★ AI Agent 模块
│           ├── core/ReActAgent           ReAct 核心循环
│           ├── tools/RecipeTools         7 个工具
│           ├── knowledge/                 RAG 检索
│           ├── memory/                    会话记忆
│           ├── llm/                       LLM 网关
│           └── controller/                SSE 接口
├── frontend/                       Vue 3 前端
│   └── src/
│       ├── api/agent.js            Agent 接口 + SSE 流解析
│       ├── utils/markdown.js       安全 Markdown 渲染
│       └── views/assistant/        AI 对话页
├── sql/init.sql                    建库建表 + 种子数据
└── 项目介绍.md                      详细项目文档（含面试问答准备）
```

---

## 技术难点

1. **SSE 分帧被换行符破坏** —— 模型输出含换行符时会破坏 SSE 的「空行分帧」结构导致丢字，
   通过下发前统一 JSON 编码解决。
2. **无 Embedding 接口时 RAG 不可用** —— 实现关键词检索作为零依赖兜底。
3. **上传路径三处不一致** —— 统一配置源，路径拼接改用 `File` 对象消除「结尾斜杠」陷阱。
4. **上下文膨胀** —— 消息窗口 + TTL 淘汰双重限制。

> 详细的问题排查过程见 [项目介绍.md](项目介绍.md) 第八章。

---

## 文档

- [项目介绍.md](项目介绍.md) —— 完整项目文档、架构说明、AI Agent 原理、**面试问答准备**
- [AI-AGENT.md](AI-AGENT.md) —— AI Agent 化改造说明、配置项、接口自测指南
- [sql/init.sql](sql/init.sql) —— 数据库初始化脚本

---

## License

本项目仅供学习交流使用。
