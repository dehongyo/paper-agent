# Paper Agent 阶段二完成报告

## 完成范围

阶段二已完成三个部分：

1. 本地文献库管理：支持论文元数据、标签、笔记、筛选、编辑和删除。
2. 可溯源 RAG：支持当前论文和全部文献库范围，并返回结构化来源证据。
3. 轻量外部论文检索：支持 arXiv 与 Semantic Scholar 检索，只展示论文信息和直达链接，不下载、不自动入库。

## 后端改动

- 扩展 `papers` 表和 `Paper` 实体，增加 DOI、来源链接、发表日期、笔记和标签。
- 新增论文更新、删除、筛选和标签接口。
- 新增语义证据检索接口 `/api/search/semantic`。
- 新增可溯源问答接口 `/api/chat/rag`。
- 新增外部论文检索接口 `/api/discovery/search`。
- 新增 arXiv 与 Semantic Scholar 轻量客户端。

## 前端改动

- 扩展阶段二 DTO 类型和 API client。
- 文献库页面新增关键词、状态、标签筛选。
- 文献卡片新增标签展示、来源链接、对话、编辑和删除操作。
- 新增论文编辑面板，支持维护元数据、摘要、笔记和标签。
- 聊天页新增“当前论文 / 全部文献库”问答范围切换。
- 助手回复新增来源证据展示，点击证据可切换到对应论文上下文。
- 新增论文检索页面，展示外部论文标题、作者、年份、摘要、页面链接、PDF 链接和 DOI。

## 验证结果

- 后端全量测试：通过。命令：`.\mvnw.cmd test`，结果：12 个测试，0 失败，0 错误。
- 后端编译：通过。命令：`.\mvnw.cmd compile`，结果：BUILD SUCCESS。
- 前端构建：通过。命令：`npm.cmd run build`，结果：TypeScript 与 Vite 构建成功。
- 浏览器冒烟验证：未完成。原因：当前 Codex 内置浏览器会话不可用；前台运行 Vite 可显示 ready，但后台 dev server 未能保持可访问状态，因此没有声明浏览器点击验证通过。

## 运行说明

后端：

```powershell
cd C:\paperagent\paper-agent-backend
$env:JAVA_HOME='C:\jdks\openjdk-17.0.2\openjdk-17.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd spring-boot:run
```

前端：

```powershell
cd C:\paperagent\paper-agent-frontend
npm.cmd run dev -- --host 127.0.0.1 --port 5173
```

然后访问 Vite 输出的本地地址，通常是 `http://127.0.0.1:5173/`。
