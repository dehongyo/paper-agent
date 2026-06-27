# Paper Review Agent — 设计文档

> 2026-06-27 | 状态：设计完成

## 概述

为 Paper Agent 新增论文深度审读智能体。用户可选择评审模板（内置或上传 PDF/DOC），对已上传论文进行多维度评审，AI 严格基于原文给出带有片段引用的评审意见，支持追问。

## 评审流程

```
选择论文 → 选择评审模板 → 开始评审 → AI 逐项评审 → 查看结果（带原文引用） → 追问
```

核心约束：
- 评审严格基于论文原文，不可编造
- 每项评审意见必须附带原文片段引用
- 不确定处标注"当前文本无法判断"
- 中文输出，专业、建设性

## 评审模板

### 内置模板（2 个）

1. **学位论文评审** — 结构规范、文献综述、研究方法、论证逻辑、创新性、写作质量、工作量
2. **IEEE/期刊论文评审** — 创新性、技术正确性、实验设计、可复现性、文献综述、表述质量

### 自定义模板

用户上传 PDF 或 DOC 评审规范文件，系统解析后作为模板使用。上传后立即可选。

## 数据库

### review_template

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL | PK |
| name | VARCHAR(255) | 模板名 |
| type | VARCHAR(50) | `builtin` / `custom` |
| source_filename | VARCHAR(255) | 原始文件名 |
| content_text | TEXT | 解析后全文 |
| created_at | TIMESTAMP | |

### review_session

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL | PK |
| paper_id | BIGINT FK → paper | |
| template_id | BIGINT FK → review_template | |
| result_text | TEXT | 评审结果全文 |
| status | VARCHAR(20) | `in_progress` / `completed` |
| created_at | TIMESTAMP | |

## API

### 模板管理

```
POST   /api/review/templates        上传模板(PDF/DOC multipart)
GET    /api/review/templates        模板列表
GET    /api/review/templates/{id}   模板详情
DELETE /api/review/templates/{id}   删除模板
```

### 评审执行

```
POST   /api/review/start             开始评审 {paperId, templateId}
POST   /api/review/continue           追问 {sessionId, message}
GET    /api/review/sessions           评审历史列表
GET    /api/review/sessions/{id}      评审详情
DELETE /api/review/sessions/{id}      删除评审
```

`POST /api/review/start` 支持流式响应 (SSE)，前端可逐步展示评审过程。

## 后端架构

### 新增文件

```
controller/ReviewController.java
service/ReviewService.java
service/TemplateService.java
entity/ReviewTemplate.java
entity/ReviewSession.java
repository/ReviewTemplateRepository.java
repository/ReviewSessionRepository.java
dto/ReviewStartRequest.java
dto/ReviewContinueRequest.java
dto/ReviewResponse.java
dto/TemplateResponse.java
```

### 已有文件改动

```
config/AiConfig.java  — 无需改动
```

### 依赖新增

```
org.apache.poi:poi-ooxml   — DOC 解析
```

### ReviewService 核心逻辑

1. 加载 Paper 全文（`paper.fullText`）
2. 加载 Template 内容（`template.contentText`）
3. 构建 system prompt：
   - 嵌入模板规范
   - 强制引用原文规则
   - 中文输出要求
4. 调用 ChatClient 流式生成评审
5. 保存 session + 结果
6. 追问时加载历史评审上下文

### Prompt 约束

```
你是一位严谨的学术论文评审专家。请严格按照以下评审规范，对论文进行评审。

评审规范：
{template.contentText}

论文全文：
{paper.fullText}

规则：
1. 每一项评审意见必须附带论文原文片段引用，格式为【原文段落X：...】
2. 不确定或信息不足的地方标注"（当前文本无法判断）"
3. 正面和负面评价均需引用原文
4. 使用中文，保持专业、建设性语气
```

## 前端架构

### 侧边栏改动

`Sidebar.tsx`：导航项新增 `{ view: 'review', label: '论文评审', icon: ClipboardCheck }`

### View 类型扩展

`Layout.tsx`：`View` 新增 `'review'`

### 新增组件

```
components/review/
  ReviewPage.tsx       主页面：论文选择（下拉） + 模板选择（下拉+上传按钮） + 开始评审
  TemplateManager.tsx  模板管理弹窗：已上传模板列表 + 删除
  ReviewResult.tsx     评审结果展示区：按段落展示，原文引用高亮
  ReviewChat.tsx       追问输入框 + 追问消息列表
```

### 新增文件

```
api/review.ts          API 调用封装
types/review.ts        TS 类型定义
```

### 状态管理

Zustand store `reviewStore`：
- `selectedPaperId` / `selectedTemplateId`
- `currentSession`
- `result` (流式累积)
- `followUpMessages`

## 内置模板内容

### 学位论文评审模板

```
1. 论文结构与格式
   - 标题、摘要、关键词是否完整规范
   - 章节结构是否合理，标题层级是否清晰
   - 图表、公式编号和引用是否规范
   - 参考文献格式是否一致

2. 文献综述
   - 是否覆盖了关键相关文献
   - 文献的时效性和权威性
   - 对现有工作的总结和不足分析是否到位

3. 研究方法
   - 研究方法选择是否合理
   - 方法描述是否清晰可复现
   - 实验/数据设计是否严谨

4. 论证逻辑
   - 研究问题和结论的呼应程度
   - 论证链条是否完整无跳跃
   - 数据分析是否支撑结论

5. 创新性与贡献
   - 创新点是否明确
   - 与现有工作的对比分析
   - 研究贡献是否清晰陈述

6. 写作质量
   - 学术用语是否准确
   - 段落衔接是否流畅
   - 是否存在歧义或模糊表达
```

### IEEE/期刊论文评审模板

```
1. 创新性与贡献
   - 研究问题的原创性和价值
   - 与 state-of-the-art 的对比和差异
   - 对领域的潜在影响

2. 技术正确性
   - 理论推导是否正确
   - 算法设计是否合理
   - 假设条件是否明确

3. 实验设计
   - 数据集选择是否合理
   - 基线对比是否充分
   - 消融实验是否完整
   - 评估指标是否合适

4. 可复现性
   - 实验设置描述是否详细
   - 代码/数据是否可用
   - 参数设置是否公开

5. 文献综述
   - 相关工作覆盖是否全面
   - 对前沿工作的把握
   - 对比分析是否客观

6. 表述质量
   - 论文结构是否符合会议/期刊惯例
   - 图表是否清晰有效
   - 英文表达是否流畅（如为英文论文）
   - 术语使用是否一致
```

## 约束与边界

- 不修改已有服务和控制器，新增独立模块
- 评审模板解析失败时返回明确错误信息
- 流式响应超时设置 5 分钟（长篇论文评审可能较久）
- 自定义模板仅限制 PDF/DOC 格式，大小上限 10MB
- 删除模板时不级联删除已有评审记录
