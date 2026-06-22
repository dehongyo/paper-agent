# Paper Agent

> 面向科研工作者的 AI 文献工作台：从论文上传、解析、检索、可追溯问答，到综述写作、引用管理和 Markdown/LaTeX 导出。

## 项目描述

Paper Agent 是一个基于 Spring Boot + React 的自动科研智能体项目，聚焦“文献管理 + RAG 问答 + 论文检索 + 综述写作”闭环。系统支持本地 PDF 论文上传与解析、向量化检索、带证据来源的学术问答、外部论文发现，以及基于本地文献证据生成综述大纲、草稿、参考文献和导出文本。

适合放在 GitHub About 的一句话描述：

```text
AI-powered research workspace for paper upload, RAG-based Q&A, literature discovery, review writing, citation formatting, and Markdown/LaTeX export.
```

## 功能概览

- **本地文献库**：上传 PDF，解析文本，生成摘要，维护标题、作者、DOI、来源链接、标签和笔记。
- **向量检索**：使用 pgvector 保存论文片段 embedding，支持跨文献库语义检索。
- **可追溯问答**：围绕单篇论文或整个本地文献库提问，回答附带来源证据片段。
- **论文检索**：对接 arXiv 和 Semantic Scholar，展示论文信息与直达链接，不自动下载、不自动入库。
- **写作工作台**：基于本地文献证据生成综述大纲、草稿、参考文献和 Markdown/LaTeX 导出。
- **引用格式**：支持 GB/T 7714、APA、IEEE 的简化参考文献格式。
- **浅色玻璃 UI**：React + TypeScript 前端，采用低饱和蓝色、系统字体和柔和阴影的工作台风格。

## 技术栈

### 后端

- Java 17
- Spring Boot 3.3
- Spring AI
- PostgreSQL 16
- pgvector
- Apache PDFBox
- JPA / JDBC
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
paper-agent-backend/       Spring Boot 后端服务
paper-agent-frontend/      React + Vite 前端应用
docs/superpowers/          阶段设计、实施计划和完成报告
```

## 快速开始

### 1. 准备数据库

确保本机有 PostgreSQL 和 pgvector，并创建数据库：

```sql
CREATE DATABASE paperagent;
```

数据库表结构见：

```text
paper-agent-backend/src/main/resources/schema.sql
```

### 2. 配置后端环境变量

不要把真实 API Key 写进 Git。

复制示例配置：

```powershell
Copy-Item paper-agent-backend\src\main\resources\application-dev.example.yml `
  paper-agent-backend\src\main\resources\application-dev.yml
```

设置环境变量：

```powershell
$env:DASHSCOPE_API_KEY="你的 DashScope API Key"
$env:PAPERAGENT_DB_PASSWORD="你的数据库密码"
```

`application-dev.yml` 已被 `.gitignore` 排除，避免误提交密钥。

### 3. 启动后端

```powershell
cd paper-agent-backend
.\mvnw.cmd spring-boot:run
```

默认服务地址：

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

前端开发代理会把 `/api` 请求转发到后端。

## 常用接口

```text
POST /api/papers/upload          上传并解析 PDF
GET  /api/papers                 获取本地文献列表
GET  /api/papers/{id}            获取论文详情
POST /api/search/semantic        语义检索证据片段
POST /api/chat/rag               可追溯 RAG 问答
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

## 安全说明

本仓库不会提交以下内容：

- `application-dev.yml`
- `.env` / `.env.*`
- API Key、Token、私钥文件
- 上传的 PDF 文件
- 日志文件
- `node_modules`
- `target`
- `dist`

如果需要本地运行，请通过环境变量注入密钥：

```powershell
$env:DASHSCOPE_API_KEY="..."
```

## 当前阶段

项目已完成前三个阶段的主要功能：

- **阶段一**：PDF 上传、解析、摘要、基础对话。
- **阶段二**：文献库管理、可追溯 RAG、外部论文检索。
- **阶段三**：写作工作台、综述生成、引用格式化、Markdown/LaTeX 导出。

详细设计和实施记录位于：

```text
docs/superpowers/
```

## 许可证

当前尚未声明开源许可证。正式公开使用前，请根据项目需求补充 `LICENSE` 文件。
