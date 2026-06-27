# Autonomous Literature Review Agent — 设计文档

> 2026-06-27 | 状态：设计完成

## 概述

让写作工作台变得更自主。用户输入主题后，Agent 自动完成：主题分析 → 文献检索（Tool Calling）→ 论文导入 → 大纲生成 → 草稿生成 → AI 自审修订 → 导出。每个阶段完成后暂停，用户确认后方可进入下一步。

## 阶段流程

```
Phase 1: 主题分析 ──→ Phase 2: 文献检索 ──→ Phase 3: 导入论文 ──→ Phase 4: 大纲 ──→ Phase 5: 草稿 ──→ Phase 6: 审校导出
     编排驱动            Tool Calling         编排驱动         编排驱动       编排驱动        编排驱动
```

每个阶段产生状态事件 `PhaseEvent`（含 phase 名称、状态、数据 payload），前端通过 SSE 流式接收。

## API

```
POST   /api/writing/autonomous/start         开始自主综述（topic） → SSE 返回各阶段事件
POST   /api/writing/autonomous/confirm/{id}  确认当前阶段，进入下一步
POST   /api/writing/autonomous/revise/{id}   用户修改当前阶段产出后提交，重新生成该阶段
POST   /api/writing/autonomous/cancel/{id}   取消当前自主综述
GET    /api/writing/autonomous/{id}          获取当前状态
GET    /api/writing/autonomous/sessions      历史会话列表
DELETE /api/writing/autonomous/sessions/{id} 删除会话
```

### PhaseEvent SSE 格式

```json
{"phase": "topic_analysis", "status": "in_progress", "data": {...}}
{"phase": "topic_analysis", "status": "completed", "data": {"topic": "...", "keywords": [...], "subtopics": [...]}}
{"phase": "literature_search", "status": "in_progress", "data": {"searching": "..."}}
{"phase": "literature_search", "status": "completed", "data": {"papers": [...]}}
{"phase": "import_papers", "status": "in_progress", "data": {"total": 5, "completed": 2}}
{"phase": "import_papers", "status": "completed", "data": {"imported": [...]}}
{"phase": "outline", "status": "completed", "data": {"outline": "..."}}
{"phase": "draft", "status": "completed", "data": {"draft": "..."}}
{"phase": "review", "status": "completed", "data": {"revisions": "...", "finalDraft": "..."}}
{"phase": "done", "status": "completed", "data": null}
```

## 数据库

### autonomous_writing_session

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL | PK |
| topic | VARCHAR(500) | 综述主题 |
| current_phase | VARCHAR(30) | 当前阶段 |
| topic_analysis_json | TEXT | Phase 1 结果 |
| search_results_json | TEXT | Phase 2 搜索结果 |
| imported_paper_ids | TEXT | Phase 3 导入论文 ID 列表（逗号分隔） |
| outline | TEXT | Phase 4 大纲 |
| draft | TEXT | Phase 5 草稿 |
| final_draft | TEXT | Phase 6 终稿 |
| status | VARCHAR(20) | in_progress / completed / cancelled |
| created_at | TIMESTAMP | |

## Phase 1: 主题分析（编排驱动）

**输入**：用户输入的主题文本

**过程**：
1. 调用 ChatClient 分析主题，拆解子话题和搜索关键词
2. Prompt 要求输出结构化 JSON：`{topic, subtopics, keywords, searchStrategy}`

**产出**：子话题列表 + 搜索关键词列表

**用户交互**：可修改关键词、增删子话题

## Phase 2: 文献检索（Tool Calling）

**输入**：Phase 1 的分析结果

**Tools 注册给 ChatClient**：

```
searchPapers(query, source)  — 搜索 arXiv / Semantic Scholar / PubMed / DBLP
getPaperDetail(externalId, source) — 获取单篇详情含摘要
finishSearch(selectedIds) — AI 认为搜索充分后调用，触发暂停
```

**过程**：
1. ChatClient 获得上述 3 个 Tool，system prompt 要求：先用关键词广泛搜索，评估摘要后决定是否需要细化，覆盖所有子话题后调用 finishSearch
2. AI 自主决定搜索轮数、关键词、数据源
3. finishSearch 被调用后，搜索阶段完成，返回 AI 筛选的论文列表
4. 前端展示搜索结果，用户可：勾选/取消论文、手动追加搜索词、确认进入下一阶段

**关键约束**：
- 搜索上限 30 篇（防 API 滥用）
- Tool 调用次数上限 15 次（防死循环）
- AI 标注的 selected 论文自动高亮

## Phase 3: 论文导入（编排驱动）

**输入**：Phase 2 用户确认的论文列表

**过程**：
1. 调用 `POST /api/papers/import/batch` 批量导入
2. 每篇论文导入后轮询 `GET /api/papers/{id}/status` 直到 READY
3. 所有论文 READY 后自动进入下一阶段
4. SSE 推送导入进度

## Phase 4: 大纲生成（编排驱动）

**输入**：导入的论文 ID 列表 + 主题

**过程**：
1. 调用 EvidenceSearchService 从已导入论文中检索证据
2. 调用 WritingService.generateOutline() 生成大纲
3. 前端展示，用户可编辑大纲

## Phase 5: 草稿生成（编排驱动）

**输入**：用户确认的大纲 + 论文 ID 列表

**过程**：
1. 调用 EvidenceSearchService 检索证据
2. 调用 WritingService.generateDraft() 生成草稿
3. 草稿展示在编辑器区域

## Phase 6: AI 自审 + 导出（编排驱动）

**输入**：草稿 + 大纲 + 论文列表

**过程**：
1. 调用 ChatClient 对草稿进行自审：检查逻辑一致性、证据引用准确性、覆盖完整性
2. 输出修订建议和修订后的草稿
3. 调用 CitationService 生成参考文献
4. 调用 ExportService 生成 Markdown / LaTeX
5. 用户确认后保存

## 后端新增文件

```
service/AutonomousWritingService.java      — 阶段编排 + SSE 推送
service/AutonomousWritingToolConfig.java   — Tool Calling bean 定义
controller/AutonomousWritingController.java — REST 端点
entity/AutonomousWritingSession.java       — JPA 实体
repository/AutonomousWritingSessionRepository.java
dto/AutonomousWritingStartRequest.java
dto/AutonomousWritingPhaseEvent.java
dto/AutonomousWritingSessionResponse.java
```

## 已有文件改动

```
PaperAgentApplication.java — 无需改动
AiConfig.java — 无需改动
WritingOrchestratorService.java — 无需改动（复用）
WritingService.java — 无需改动（复用）
schema.sql — 新增 autonomous_writing_session 表
```

## 前端改动

在 `WritingPage.tsx` 中新增 "自主综述" 模式，显示：
- 顶部：阶段进度条（6 个阶段，当前阶段高亮，完成阶段打勾）
- 中间：当前阶段交互面板（主题输入 / 搜索结果列表 / 大纲编辑器 / 草稿预览 / 修订对比）
- 底部：确认进入下一步 / 修改后重试 按钮

状态管理：扩展现有 writingStore 或新建 autonomousWritingStore。

## 约束与边界

- Tool Calling 搜索上限 30 篇，Tool 调用上限 15 次
- 不修改现有 WritingController/Service，新增独立模块
- 导入论文过程可被用户中断
- 每个阶段完成后数据持久化到 session 表
- 前端支持刷新恢复当前会话状态
