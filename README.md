# Paper Agent

> 面向科研工作者的 AI 文献工作台：支持论文上传解析、文献库管理、可追溯 RAG 问答、外部论文检索、综述写作、引用生成和 Markdown/LaTeX 导出。

Paper Agent 是一个基于 Spring Boot、React 和 PostgreSQL/pgvector 的科研辅助系统。它把本地论文管理、语义检索、证据追踪和综述写作放在同一个工作流里，帮助研究者从“收集论文”推进到“理解论文、追问证据、组织综述和导出文本”。

GitHub About 推荐描述：

```text
AI-powered research workspace for paper upload, RAG-based Q&A, literature discovery, review writing, citation formatting, and Markdown/LaTeX export.
```

## 核心能力

- **本地文献库**：上传 PDF，解析论文文本，维护标题、作者、DOI、来源链接、发表时间、标签、摘要和笔记。
- **论文状态管理**：覆盖上传、解析、向量化、就绪和错误等处理状态，便于追踪文献入库流程。
- **语义证据检索**：使用 PostgreSQL + pgvector 保存论文片段向量，支持按问题召回相关证据。
- **可追溯 RAG 问答**：支持围绕单篇论文或整个文献库提问，回答可附带证据片段和论文来源。
- **会话历史**：支持按当前论文或全局文献库创建、保存和恢复对话上下文。
- **外部论文发现**：对接 arXiv 和 Semantic Scholar，检索外部论文标题、作者、年份、摘要、DOI、落地页和 PDF 链接。
- **写作工作台**：基于本地文献证据生成综述大纲、草稿、参考文献列表，并支持继续编辑。
- **引用与导出**：支持 GB/T 7714、APA、IEEE 的简化引用格式，导出 Markdown 和 LaTeX 文本。
- **研究工作台 UI**：React + TypeScript + Tailwind CSS，提供对话、文献库、写作和论文检索四个主要视图。

## 使用场景

- 阅读一批 PDF 后，快速建立可检索的本地文献库。
- 围绕论文内容进行追问，并保留可回溯证据。
- 检索某个研究方向的外部论文，获取摘要和来源链接。
- 基于已有文献生成综述大纲、初稿和参考文献。
- 将生成内容导出为 Markdown 或 LaTeX，继续放入论文写作流程。

## 技术栈

### 后端

- Java 17
- Spring Boot 3.3
- Spring AI
- PostgreSQL 16
- pgvector
- Apache PDFBox
- Spring Data JPA / JDBC
- JUnit 5 / Mockito

### 前端

- React
- TypeScript
- Vite
- Zustand
- Tailwind CSS
- lucide-react

## 项目结构

```text
.
├── paper-agent-backend/       Spring Boot 后端服务
│   ├── src/main/java/         控制器、服务、实体、仓库和 DTO
│   ├── src/main/resources/    应用配置和数据库 schema
│   └── src/test/java/         后端单元测试与控制器测试
├── paper-agent-frontend/      React + Vite 前端应用
│   ├── src/api/               前端 API 客户端
│   ├── src/components/        对话、文献库、检索、写作和布局组件
│   ├── src/store/             Zustand 状态管理
│   └── src/types/             TypeScript 类型定义
├── docs/superpowers/          阶段设计、计划和完成记录
└── README.md                  项目说明文档
```

## 快速开始

### 1. 准备数据库

安装 PostgreSQL 和 pgvector，创建数据库：

```sql
CREATE DATABASE paperagent;
```

数据库表结构位于：

```text
paper-agent-backend/src/main/resources/schema.sql
```

### 2. 配置后端

复制开发环境配置示例：

```powershell
Copy-Item paper-agent-backend\src\main\resources\application-dev.example.yml `
  paper-agent-backend\src\main\resources\application-dev.yml
```

设置环境变量：

```powershell
$env:DASHSCOPE_API_KEY="你的 DashScope API Key"
$env:PAPERAGENT_DB_PASSWORD="你的数据库密码"
```

`application-dev.yml` 已被 `.gitignore` 排除，不要把真实 API Key、数据库密码或其他密钥提交到仓库。

### 3. 启动后端

```powershell
cd paper-agent-backend
.\mvnw.cmd spring-boot:run
```

默认后端地址：

```text
http://localhost:8080
```

### 4. 启动前端

```powershell
cd paper-agent-frontend
npm.cmd install
npm.cmd run dev
```

默认前端地址：

```text
http://localhost:5173
```

前端开发代理默认把 `/api` 转发到 `http://localhost:8080`。如需临时指向其他后端地址，可设置：

```powershell
$env:PAPER_AGENT_API_TARGET="http://localhost:18080"
npm.cmd run dev
```

## 常用接口

```text
POST /api/papers/upload          上传并解析 PDF
GET  /api/papers                 获取本地文献列表
GET  /api/papers/{id}            获取论文详情
PATCH /api/papers/{id}           更新论文元数据、标签和笔记
DELETE /api/papers/{id}          删除论文
GET  /api/papers/tags            获取标签列表
POST /api/search/semantic        语义检索证据片段
POST /api/chat/rag               可追溯 RAG 问答
POST /api/chat/rag/stream        流式可追溯 RAG 问答
GET  /api/chat/sessions          获取会话历史
POST /api/chat/sessions          创建会话
GET  /api/chat/sessions/{id}/messages  获取会话消息
GET  /api/discovery/search       外部论文检索
POST /api/writing/outline        生成综述大纲
POST /api/writing/draft          生成综述草稿
POST /api/writing/export         导出 Markdown/LaTeX
```

## 验证命令

后端：

```powershell
cd paper-agent-backend
.\mvnw.cmd test
.\mvnw.cmd compile
```

前端：

```powershell
cd paper-agent-frontend
npm.cmd run lint
npm.cmd run build
```

## 安全与提交说明

仓库默认不会提交以下内容：

- `application-dev.yml`
- `.env` / `.env.*`
- API Key、Token、证书和私钥文件
- 上传的 PDF 文件和本地运行数据
- 日志文件
- `node_modules`
- `target`
- `dist`

如果需要本地运行，请通过环境变量注入密钥，不要把密钥写入 Git。

## 当前阶段

项目已完成前三个阶段的主要能力：

- **阶段一**：PDF 上传、解析、摘要和基础对话。
- **阶段二**：文献库管理、可追溯 RAG、外部论文检索和会话历史。
- **阶段三**：写作工作台、综述生成、引用格式化、Markdown/LaTeX 导出和前端体验优化。

更多设计和实施记录位于：

```text
docs/superpowers/
```

## 许可证

当前尚未声明开源许可证。正式公开使用前，请根据项目需要补充 `LICENSE` 文件。
