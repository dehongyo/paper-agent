# Paper Agent 阶段三设计：写作工作台 + 综述生成 + 引用导出

## 摘要

阶段三的目标，是把阶段二已经具备的“本地文献库、外部论文检索、可追溯 RAG 问答”继续推进成一个可演示、可使用的学术写作闭环。

本阶段采用推荐方案 A：先做一个轻量但完整的“写作工作台 MVP”。系统不急着引入复杂任务队列、完整富文本协同编辑或长期多智能体工作流，而是先让用户可以选择本地文献，生成综述大纲和草稿，查看每段内容对应的证据来源，自动生成参考文献，并导出 Markdown 或 LaTeX。

阶段三完成后，Paper Agent 应能支撑一个典型流程：用户在本地文献库中准备若干论文，进入写作页面输入综述主题，系统基于文献证据生成结构化大纲、综述草稿、来源证据和参考文献，用户可在页面中继续编辑并导出。

## 目标

- 增加写作工作台页面，作为阶段三主要入口。
- 支持从本地文献库选择论文作为写作语料。
- 支持输入主题、写作类型、语言、目标篇幅和引用格式。
- 基于本地证据生成综述大纲。
- 基于大纲和证据生成综述草稿。
- 草稿中使用稳定的来源编号，例如 `[1]`、`[2]`。
- 页面侧边展示证据片段，让用户能看到内容来自哪篇论文、哪个 chunk。
- 生成参考文献列表，支持 `GB/T 7714`、`APA`、`IEEE` 三种格式。
- 支持导出 Markdown 和 LaTeX。
- 后端新增轻量 Orchestrator/写作服务，但先保持同步 API，避免过早引入 RabbitMQ 或长任务系统。
- 保持阶段一、阶段二已有上传、文献库、对话、检索功能可用。

## 非目标

- 不实现多人协作编辑。
- 不实现完整 Tiptap 富文本编辑器，第一版先使用结构化文本编辑区。
- 不实现 Word/PDF 直接导出。
- 不自动下载外部检索结果。
- 不把外部检索结果自动加入本地文献库。
- 不实现复杂持久化写作项目版本树。
- 不实现真正长期运行的异步多智能体任务队列。
- 不承诺生成结果可直接作为最终论文提交，界面需要保留人工审阅和证据核验的空间。

## 当前基础

阶段二已经具备：

- 后端 Spring Boot 服务。
- 前端 React/Vite 单页应用。
- 本地文献上传、解析、摘要和向量化。
- `papers` 和 `paper_chunks` 数据表。
- 本地文献库管理接口。
- 跨文献库证据检索能力。
- `POST /api/chat/rag` 可追溯问答接口。
- `GET /api/discovery/search` 轻量外部论文检索接口。
- 前端已有对话、文献库和论文检索页面。
- 当前视觉方向已经调整为浅色玻璃质感、低饱和蓝色、柔和阴影和系统字体。

阶段三应复用这些能力，而不是重写 RAG 或文献库逻辑。

## 用户体验设计

### 写作工作台入口

侧边栏新增“写作”入口，位于“文献库”和“论文检索”之间或之后。页面整体继续沿用当前 Apple-like 浅色玻璃风格，避免做成宣传页。第一屏应该直接是可操作工作台。

页面分为三块：

1. 左侧或顶部配置区：主题、写作类型、语言、篇幅、引用格式、文献选择。
2. 中央写作区：大纲、草稿、参考文献、导出结果。
3. 右侧证据区：展示生成内容使用到的来源片段。

在窄屏下，配置区、写作区、证据区上下排列。输入框、按钮、卡片层级和阶段二 UI 保持一致。

### 写作配置

用户可设置：

- 综述主题，例如“检索增强生成在医学影像中的应用”。
- 写作类型，第一版支持“文献综述”和“研究背景”。
- 输出语言，第一版支持中文和英文。
- 目标篇幅，第一版使用枚举：短篇、标准、详细。
- 引用格式：`GB/T 7714`、`APA`、`IEEE`。
- 文献范围：全部本地文献，或手动勾选若干论文。

默认推荐：

- 写作类型：文献综述。
- 输出语言：中文。
- 目标篇幅：标准。
- 引用格式：GB/T 7714。
- 文献范围：全部本地文献。

### 生成流程

第一版提供两个主要按钮：

- 生成大纲。
- 生成草稿。

如果用户尚未生成大纲，点击“生成草稿”时后端可以先生成大纲，再生成草稿。前端需要显示清晰加载状态，避免用户误以为页面无响应。

生成结果包括：

- `outline`：章节标题和要点。
- `draft`：综述正文，带来源编号。
- `references`：格式化后的参考文献列表。
- `evidence`：结构化证据列表。
- `exportMarkdown`：Markdown 文本。
- `exportLatex`：LaTeX 文本。

### 编辑体验

第一版使用轻量文本编辑：

- 大纲区域可编辑。
- 草稿区域可编辑。
- 参考文献区域只读或半只读，用户可重新生成引用格式。
- 导出区提供复制 Markdown、复制 LaTeX 两个操作。

不在阶段三第一版引入 Tiptap，是为了先保证写作闭环稳定。后续可以把草稿编辑区替换为 Tiptap，而不改变后端 API。

### 证据展示

证据区展示：

- 来源编号。
- 论文标题。
- chunk 序号。
- 相似度或排序。
- 原文片段。

用户点击证据时，前端可以高亮对应来源。第一版不要求跳转 PDF 原文页，因为当前系统还没有 PDF 定位阅读器。

## 后端设计

### 数据模型

第一版阶段三不强制新增持久化表。写作结果可由接口实时返回，前端保存在当前页面状态中。

保留未来扩展点：

- `writing_projects`：保存写作项目。
- `writing_sections`：保存章节。
- `writing_citations`：保存引用映射。
- `export_records`：保存导出记录。

这些表不在第一版创建，避免过早固化模型。

### DTO

新增写作相关 DTO：

- `WritingRequest`
  - `topic`
  - `paperIds`
  - `scope`
  - `writingType`
  - `language`
  - `length`
  - `citationStyle`
  - `outline`

- `WritingEvidence`
  - `index`
  - `chunkId`
  - `paperId`
  - `paperTitle`
  - `chunkIndex`
  - `content`
  - `similarity`

- `ReferenceItem`
  - `index`
  - `paperId`
  - `title`
  - `authors`
  - `year`
  - `doi`
  - `sourceUrl`
  - `formatted`

- `WritingResponse`
  - `topic`
  - `outline`
  - `draft`
  - `references`
  - `evidence`
  - `exportMarkdown`
  - `exportLatex`

### API

新增控制器 `WritingController`，路径前缀 `/api/writing`。

接口：

- `POST /api/writing/outline`
  - 基于主题和文献证据生成大纲。
  - 返回 `WritingResponse`，其中 `draft` 可以为空。

- `POST /api/writing/draft`
  - 基于主题、大纲和文献证据生成草稿。
  - 返回完整 `WritingResponse`。

- `POST /api/writing/references`
  - 基于论文列表和引用格式生成参考文献。
  - 返回 `ReferenceItem[]`。

- `POST /api/writing/export`
  - 基于已有大纲、草稿和参考文献生成 Markdown/LaTeX。
  - 返回 `WritingResponse` 中的导出字段。

为了第一版简洁，`outline` 和 `draft` 两个接口可以共用 `WritingService` 的内部方法。

### 服务划分

新增服务：

- `WritingOrchestratorService`
  - 阶段三的轻量编排层。
  - 负责决定先取证据、再生成大纲、再生成草稿、再生成引用和导出。
  - 第一版同步执行，不引入任务队列。

- `WritingService`
  - 负责构造写作 prompt，调用 `ChatClient` 生成大纲和草稿。
  - 必须要求模型只基于提供的证据写作。

- `CitationService`
  - 负责把本地论文元数据格式化为 `GB/T 7714`、`APA`、`IEEE`。
  - 不依赖 LLM，避免参考文献格式被模型编造。

- `ExportService`
  - 负责生成 Markdown 和 LaTeX。
  - 不依赖 LLM，保证导出稳定。

复用服务：

- `EvidenceSearchService`
  - 根据主题在全库或指定论文集合中检索证据。

- `PaperService`
  - 根据 `paperIds` 获取论文元数据。

### Prompt 约束

写作 prompt 必须包含以下约束：

- 只使用提供的证据。
- 不编造作者、年份、实验结果、论文标题和 DOI。
- 证据不足时明确写出“当前本地文献证据不足”。
- 每个重要论断尽量使用 `[1]`、`[2]` 这类来源编号。
- 中文输出使用学术、克制、清晰的表达。
- 英文输出使用 academic but readable 风格。

证据编号由后端生成并传给模型，不能让模型自己决定来源编号和引用映射。

### 引用格式规则

第一版实现可接受的简化格式：

- GB/T 7714：
  - `[1] 作者. 标题[J/OL]. 年份. DOI/URL.`

- APA：
  - `作者. (年份). 标题. DOI/URL`

- IEEE：
  - `[1] 作者, "标题," 年份. DOI/URL.`

如果作者缺失，使用“未知作者”或 `Unknown author`。如果年份缺失，中文使用“日期不详”，英文使用 `n.d.`。

## 前端设计

### 类型与 API

扩展 `src/types/index.ts`：

- `CitationStyle`
- `WritingLength`
- `WritingType`
- `WritingRequest`
- `WritingEvidence`
- `ReferenceItem`
- `WritingResponse`

扩展 `src/api/client.ts`：

- `generateOutline`
- `generateDraft`
- `generateReferences`
- `exportWriting`

### 页面与组件

新增目录 `src/components/writing`。

组件：

- `WritingPage`
  - 写作页面容器。
  - 管理配置、结果、加载状态和错误状态。

- `WritingConfigPanel`
  - 主题、写作类型、语言、篇幅、引用格式、文献范围选择。

- `PaperPicker`
  - 复用 `getPapers` 获取本地文献列表。
  - 支持全选、清空、单篇勾选。

- `WritingEditor`
  - 展示并编辑大纲和草稿。

- `WritingEvidencePanel`
  - 展示证据来源。

- `ReferenceList`
  - 展示参考文献。

- `ExportPanel`
  - 展示 Markdown/LaTeX 文本，提供复制按钮。

### 导航

扩展 `Layout.tsx`：

- `View` 增加 `writing`。
- 当前视图为 `writing` 时渲染 `WritingPage`。

扩展 `Sidebar.tsx`：

- 新增“写作”导航项，使用 lucide 图标，例如 `PenLine` 或 `FilePenLine`。

移动端底部导航需要同步增加写作入口。由于当前移动端导航空间有限，可以使用四列布局。

### 视觉要求

写作页面继续遵守当前设计方向：

- 浅色玻璃质感。
- 系统字体。
- 低饱和蓝色主色。
- 柔和阴影。
- 统一按钮、输入框、卡片层级。
- 不做大面积深色背景。
- 不做营销式 hero。
- 不把所有内容挤到左侧，主要工作区需要居中并设置合理最大宽度。

## 数据流

### 生成大纲

1. 用户选择文献范围并输入主题。
2. 前端调用 `POST /api/writing/outline`。
3. 后端根据主题调用 `EvidenceSearchService` 检索证据。
4. 后端为证据生成稳定编号。
5. 后端调用 LLM 生成大纲。
6. 后端生成参考文献和导出字段。
7. 前端渲染大纲、证据和参考文献。

### 生成草稿

1. 用户点击“生成草稿”。
2. 前端提交主题、当前大纲、文献范围和引用格式。
3. 后端检索证据。
4. 后端调用 LLM 生成带来源编号的草稿。
5. 后端用 `CitationService` 生成参考文献。
6. 后端用 `ExportService` 生成 Markdown 和 LaTeX。
7. 前端渲染草稿、证据、参考文献和导出内容。

### 导出

1. 用户编辑草稿。
2. 用户点击复制 Markdown 或复制 LaTeX。
3. 前端调用 `POST /api/writing/export` 或直接使用现有响应中的导出文本。
4. 第一版推荐由后端导出，保证 Markdown/LaTeX 规则集中维护。

## 错误处理

- 主题为空时返回 400。
- 本地文献库为空时返回可读错误：“请先上传或准备本地文献。”
- 指定 `paperIds` 中存在不存在的论文时返回 400。
- 检索不到证据时，返回空草稿和明确提示，不调用模型生成无来源内容。
- LLM 调用失败时返回 502 或 500，并在前端展示“生成失败，请稍后重试”。
- 引用格式不支持时返回 400。
- 导出时如果草稿为空，返回 400。

## 测试策略

后端测试：

- `CitationServiceTest`
  - 验证 GB/T 7714、APA、IEEE 格式。
  - 验证作者、年份、DOI 缺失时的降级输出。

- `ExportServiceTest`
  - 验证 Markdown 包含标题、大纲、正文和参考文献。
  - 验证 LaTeX 包含基本 document 结构。

- `WritingServiceTest`
  - 验证无证据时不调用模型。
  - 验证生成 prompt 包含证据编号和禁止编造约束。

- `WritingControllerTest`
  - 验证 `/api/writing/outline` 和 `/api/writing/draft` 接口。

前端验证：

- `npm.cmd run lint`
- `npm.cmd run build`
- 手动验证写作页面可打开。
- 手动验证本地文献可选择。
- 手动验证生成按钮有加载状态。
- 手动验证参考文献和导出区渲染正常。

## 验收标准

- 侧边栏出现“写作”入口。
- 写作页面默认就是可操作工作台。
- 用户可以输入主题并选择本地文献范围。
- 用户可以生成大纲。
- 用户可以生成带来源编号的综述草稿。
- 用户可以看到每个来源编号对应的论文和证据片段。
- 用户可以切换 GB/T 7714、APA、IEEE 引用格式。
- 用户可以得到 Markdown 和 LaTeX 导出文本。
- 空文献库、空主题、证据不足、模型失败都有可读反馈。
- 阶段一、阶段二已有页面继续可用。
- 后端测试通过。
- 前端 lint 和 build 通过。

## 推进顺序

1. 后端引用和导出纯函数服务。
2. 后端写作 DTO、Orchestrator 和 Controller。
3. 后端测试覆盖。
4. 前端类型和 API client。
5. 前端写作页面和组件。
6. 侧边栏和布局接入。
7. 视觉细节统一。
8. 前后端构建与本地服务验证。

## 风险与约束

- LLM 可能生成看似合理但证据不足的内容，因此 prompt 和 UI 都必须强调来源证据。
- 参考文献格式第一版是简化实现，适合作为草稿，不替代最终投稿前的人工校对。
- 同步生成草稿可能耗时较长，第一版需要清晰 loading；后续再升级为异步任务。
- 当前没有 PDF 原文定位阅读器，所以证据只能定位到 chunk，不定位到页码和高亮区域。
- 当前 `c:\paperagent` 不是 Git 仓库，无法按 Superpowers 流程提交设计文档 commit。

## 设计自检

- 未保留 TBD、TODO 或空白决策。
- 阶段三范围聚焦在可演示写作闭环，没有把阶段四绘图、部署和监控混入。
- 后端同步 API 与未来异步任务系统之间边界清晰。
- 前端第一版使用轻量编辑器，与后续 Tiptap 替换路径兼容。
- 引用格式由确定性服务生成，不交给模型自由编造。
