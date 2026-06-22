# Paper Agent 阶段三实施计划

> **给 agentic workers 的说明：**执行本计划时使用 `superpowers:executing-plans`。步骤使用 checkbox（`- [ ]`）格式，执行时逐项更新。当前 `c:\paperagent` 不是 Git 仓库，因此提交步骤改为本地验证点。

**目标：**实现阶段三写作工作台 MVP，让用户基于本地文献生成综述大纲、草稿、证据来源、参考文献，并导出 Markdown/LaTeX。

**架构：**后端保持 Spring Boot 单体结构，新增写作 DTO、引用服务、导出服务、写作服务和轻量编排服务。前端保持 React SPA，新增写作页面和组件，并接入现有浅色玻璃设计系统。

**技术栈：**Java 17、Spring Boot 3.3、Spring AI ChatClient、Spring MVC、JUnit 5、Mockito、React、TypeScript、Vite、Tailwind CSS、lucide-react。

---

## 文件结构

### 后端新增或修改

- 新增：`paper-agent-backend/src/main/java/com/paperagent/dto/WritingRequest.java`
- 新增：`paper-agent-backend/src/main/java/com/paperagent/dto/WritingEvidence.java`
- 新增：`paper-agent-backend/src/main/java/com/paperagent/dto/ReferenceItem.java`
- 新增：`paper-agent-backend/src/main/java/com/paperagent/dto/WritingResponse.java`
- 新增：`paper-agent-backend/src/main/java/com/paperagent/service/CitationService.java`
- 新增：`paper-agent-backend/src/main/java/com/paperagent/service/ExportService.java`
- 新增：`paper-agent-backend/src/main/java/com/paperagent/service/WritingService.java`
- 新增：`paper-agent-backend/src/main/java/com/paperagent/service/WritingOrchestratorService.java`
- 新增：`paper-agent-backend/src/main/java/com/paperagent/controller/WritingController.java`
- 修改：`paper-agent-backend/src/main/java/com/paperagent/service/PaperService.java`
- 修改：`paper-agent-backend/src/main/java/com/paperagent/repository/PaperRepository.java`

### 后端测试

- 新增：`paper-agent-backend/src/test/java/com/paperagent/service/CitationServiceTest.java`
- 新增：`paper-agent-backend/src/test/java/com/paperagent/service/ExportServiceTest.java`
- 新增：`paper-agent-backend/src/test/java/com/paperagent/service/WritingOrchestratorServiceTest.java`
- 新增：`paper-agent-backend/src/test/java/com/paperagent/controller/WritingControllerTest.java`

### 前端新增或修改

- 修改：`paper-agent-frontend/src/types/index.ts`
- 修改：`paper-agent-frontend/src/api/client.ts`
- 修改：`paper-agent-frontend/src/components/layout/Layout.tsx`
- 修改：`paper-agent-frontend/src/components/layout/Sidebar.tsx`
- 新增：`paper-agent-frontend/src/components/writing/WritingPage.tsx`
- 新增：`paper-agent-frontend/src/components/writing/WritingConfigPanel.tsx`
- 新增：`paper-agent-frontend/src/components/writing/PaperPicker.tsx`
- 新增：`paper-agent-frontend/src/components/writing/WritingEditor.tsx`
- 新增：`paper-agent-frontend/src/components/writing/WritingEvidencePanel.tsx`
- 新增：`paper-agent-frontend/src/components/writing/ReferenceList.tsx`
- 新增：`paper-agent-frontend/src/components/writing/ExportPanel.tsx`

---

## 任务 1：后端引用和导出服务

**文件：**

- 新增：`CitationService.java`
- 新增：`ExportService.java`
- 新增：`ReferenceItem.java`
- 新增测试：`CitationServiceTest.java`
- 新增测试：`ExportServiceTest.java`

- [ ] **步骤 1：新增 `ReferenceItem` DTO**

```java
public record ReferenceItem(
        int index,
        Long paperId,
        String title,
        String authors,
        String year,
        String doi,
        String sourceUrl,
        String formatted
) {}
```

- [ ] **步骤 2：实现 `CitationService`**

实现 `formatReferences(List<Paper> papers, String citationStyle)`，支持 `gbt7714`、`apa`、`ieee`，缺失作者时使用“未知作者”，缺失年份时使用“日期不详”。

- [ ] **步骤 3：实现 `ExportService`**

实现：

```java
String toMarkdown(String topic, String outline, String draft, List<ReferenceItem> references)
String toLatex(String topic, String outline, String draft, List<ReferenceItem> references)
```

- [ ] **步骤 4：新增单元测试**

覆盖三种引用格式、缺失字段降级、Markdown/LaTeX 基本结构。

- [ ] **步骤 5：验证**

运行：

```powershell
cd paper-agent-backend
.\mvnw.cmd -Dtest=CitationServiceTest,ExportServiceTest test
```

预期：PASS。

---

## 任务 2：后端写作编排 API

**文件：**

- 新增：`WritingRequest.java`
- 新增：`WritingEvidence.java`
- 新增：`WritingResponse.java`
- 新增：`WritingService.java`
- 新增：`WritingOrchestratorService.java`
- 新增：`WritingController.java`
- 修改：`PaperRepository.java`
- 修改：`PaperService.java`
- 新增测试：`WritingOrchestratorServiceTest.java`
- 新增测试：`WritingControllerTest.java`

- [ ] **步骤 1：新增写作 DTO**

`WritingRequest` 包含主题、论文范围、写作类型、语言、篇幅、引用格式和可选大纲。`WritingResponse` 返回大纲、草稿、参考文献、证据和导出文本。

- [ ] **步骤 2：扩展论文查询能力**

在 `PaperRepository` 增加：

```java
List<Paper> findByIdIn(List<Long> ids);
```

在 `PaperService` 增加：

```java
List<Paper> getReadyPapersForWriting(List<Long> paperIds)
```

当 `paperIds` 为空时返回所有 READY 论文，否则返回指定 READY 论文。

- [ ] **步骤 3：实现 `WritingService`**

实现：

```java
String generateOutline(String topic, String writingType, String language, String length, List<WritingEvidence> evidence)
String generateDraft(String topic, String outline, String writingType, String language, String length, List<WritingEvidence> evidence)
```

prompt 必须包含证据编号、禁止编造约束和输出语言要求。

- [ ] **步骤 4：实现 `WritingOrchestratorService`**

职责：

- 校验主题。
- 读取论文范围。
- 使用 `EvidenceSearchService` 检索证据。
- 为证据生成稳定编号。
- 生成大纲或草稿。
- 生成参考文献。
- 生成 Markdown/LaTeX。

- [ ] **步骤 5：实现 `WritingController`**

接口：

- `POST /api/writing/outline`
- `POST /api/writing/draft`
- `POST /api/writing/export`

- [ ] **步骤 6：新增测试**

覆盖无文献、无证据、生成大纲、生成草稿、导出接口。

- [ ] **步骤 7：验证**

运行：

```powershell
cd paper-agent-backend
.\mvnw.cmd -Dtest=WritingOrchestratorServiceTest,WritingControllerTest test
.\mvnw.cmd compile
```

预期：PASS / BUILD SUCCESS。

---

## 任务 3：前端类型和 API

**文件：**

- 修改：`types/index.ts`
- 修改：`api/client.ts`

- [ ] **步骤 1：新增 TypeScript 类型**

新增 `CitationStyle`、`WritingLength`、`WritingType`、`WritingRequest`、`WritingEvidence`、`ReferenceItem`、`WritingResponse`。

- [ ] **步骤 2：新增 API 方法**

新增：

- `generateOutline`
- `generateDraft`
- `exportWriting`

- [ ] **步骤 3：验证**

运行：

```powershell
cd paper-agent-frontend
npm.cmd run build
```

预期：PASS。

---

## 任务 4：前端写作页面

**文件：**

- 新增：`WritingPage.tsx`
- 新增：`WritingConfigPanel.tsx`
- 新增：`PaperPicker.tsx`
- 新增：`WritingEditor.tsx`
- 新增：`WritingEvidencePanel.tsx`
- 新增：`ReferenceList.tsx`
- 新增：`ExportPanel.tsx`

- [ ] **步骤 1：实现 `PaperPicker`**

使用 `getPapers` 获取本地文献，支持全部文献和手动勾选。

- [ ] **步骤 2：实现 `WritingConfigPanel`**

支持主题、写作类型、语言、篇幅、引用格式和文献范围配置。

- [ ] **步骤 3：实现 `WritingEditor`**

展示可编辑大纲和草稿。

- [ ] **步骤 4：实现 `WritingEvidencePanel`**

展示证据编号、论文标题、chunk 序号、相似度和原文片段。

- [ ] **步骤 5：实现 `ReferenceList` 和 `ExportPanel`**

展示参考文献、Markdown 和 LaTeX，提供复制按钮。

- [ ] **步骤 6：实现 `WritingPage`**

串联配置、生成大纲、生成草稿、导出和错误状态。

- [ ] **步骤 7：验证**

运行：

```powershell
cd paper-agent-frontend
npm.cmd run lint
npm.cmd run build
```

预期：PASS。

---

## 任务 5：导航接入和整体验证

**文件：**

- 修改：`Layout.tsx`
- 修改：`Sidebar.tsx`

- [ ] **步骤 1：新增 `writing` 视图**

`Layout` 中增加 `writing` view，并渲染 `WritingPage`。

- [ ] **步骤 2：新增“写作”导航**

桌面侧边栏和移动端底部导航都增加写作入口。

- [ ] **步骤 3：完整验证**

运行：

```powershell
cd paper-agent-backend
.\mvnw.cmd test
.\mvnw.cmd compile
cd ..\paper-agent-frontend
npm.cmd run lint
npm.cmd run build
```

预期：全部通过。

- [ ] **步骤 4：本地接口冒烟**

确认前后端服务可用后，访问：

- `GET http://127.0.0.1:5173`
- `GET http://127.0.0.1:5173/api/papers`

预期：HTTP 200。

---

## 实施备注

- 当前不是 Git 仓库，不执行 commit。
- 后端测试命令如遇沙箱权限问题，使用已批准的 Maven 运行方式或请求提升。
- 前端命令使用 `npm.cmd`，避免 PowerShell 执行策略阻止 `npm.ps1`。
- 阶段三第一版不新增数据库表。
