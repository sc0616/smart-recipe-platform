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

