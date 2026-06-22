# Paper Agent 阶段二实施计划

> **给 agentic workers 的说明：**执行本计划时必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`。任务步骤使用 checkbox（`- [ ]`）格式，执行时逐项更新。

**目标：**把 Paper Agent 从阶段一的“上传 PDF + 摘要 + 单篇论文问答”升级为“文献工作台 + 可溯源 RAG + 轻量论文检索”。

**架构：**保持当前 Spring Boot 单体后端和 React SPA 前端。后端扩展 `papers` 与 `paper_chunks` 的数据能力，新增文献管理、证据检索、可溯源问答和外部检索服务；前端在现有对话页和文献库页基础上增加管理、证据展示和论文检索页面。

**技术栈：**Java 17、Spring Boot 3.3、Spring Data JPA、JdbcTemplate、Spring AI、Reactor、JUnit 5、Mockito、React 19、TypeScript、Vite、Zustand、Tailwind CSS、lucide-react。

---

## 范围确认

阶段二包含三个相互衔接的部分：

1. **本地文献库管理**：编辑论文元数据、标签、笔记、删除、筛选和关键词检索。
2. **可溯源 RAG**：支持“当前论文”和“全部文献库”两种问答范围，回答必须返回来源证据。
3. **轻量外部论文检索**：接入 arXiv 和 Semantic Scholar，只展示论文信息和直达链接，不下载、不自动入库。

这三个部分可以放在同一个实施计划中，因为它们共同构成阶段二的文献工作台，并且每一层都能独立验证。

---

## 文件结构

### 后端需要修改或新增的文件

- 修改：`paper-agent-backend/src/main/resources/schema.sql`
  - 给 `papers` 表增加 DOI、来源链接、发表日期、笔记和标签字段。
- 修改：`paper-agent-backend/src/main/java/com/paperagent/entity/Paper.java`
  - 增加阶段二元数据字段。
- 修改：`paper-agent-backend/src/main/java/com/paperagent/dto/PaperListItem.java`
  - 扩展文献库列表返回字段。
- 修改：`paper-agent-backend/src/main/java/com/paperagent/dto/PaperSummaryResponse.java`
  - 扩展论文详情返回字段。
- 新增：`paper-agent-backend/src/main/java/com/paperagent/dto/PaperUpdateRequest.java`
  - 编辑论文元数据的请求体。
- 新增：`paper-agent-backend/src/main/java/com/paperagent/dto/EvidenceChunk.java`
  - 结构化证据片段。
- 新增：`paper-agent-backend/src/main/java/com/paperagent/dto/SemanticSearchRequest.java`
  - 语义检索请求体。
- 新增：`paper-agent-backend/src/main/java/com/paperagent/dto/SemanticSearchResponse.java`
  - 语义检索响应体。
- 新增：`paper-agent-backend/src/main/java/com/paperagent/dto/TraceableChatRequest.java`
  - 可溯源问答请求体。
- 新增：`paper-agent-backend/src/main/java/com/paperagent/dto/TraceableChatResponse.java`
  - 答案加证据响应体。
- 新增：`paper-agent-backend/src/main/java/com/paperagent/dto/DiscoveryResult.java`
  - 外部论文检索结果。
- 修改：`paper-agent-backend/src/main/java/com/paperagent/repository/PaperRepository.java`
  - 增加文献库筛选查询。
- 修改：`paper-agent-backend/src/main/java/com/paperagent/service/PaperService.java`
  - 增加更新、删除、标签、筛选。
- 新增：`paper-agent-backend/src/main/java/com/paperagent/service/EvidenceSearchService.java`
  - 统一处理单篇或全库语义证据检索。
- 修改：`paper-agent-backend/src/main/java/com/paperagent/service/ChatService.java`
  - 增加可溯源 RAG 问答。
- 新增：`paper-agent-backend/src/main/java/com/paperagent/service/DiscoveryService.java`
  - 聚合外部论文源。
- 新增：`paper-agent-backend/src/main/java/com/paperagent/service/ArxivClient.java`
  - 调用 arXiv API 并解析 Atom XML。
- 新增：`paper-agent-backend/src/main/java/com/paperagent/service/SemanticScholarClient.java`
  - 调用 Semantic Scholar API 并统一结果格式。
- 修改：`paper-agent-backend/src/main/java/com/paperagent/controller/PaperController.java`
  - 增加筛选、编辑、删除、标签接口。
- 新增：`paper-agent-backend/src/main/java/com/paperagent/controller/SearchController.java`
  - 增加语义检索接口。
- 修改：`paper-agent-backend/src/main/java/com/paperagent/controller/ChatController.java`
  - 增加非流式可溯源问答接口。
- 新增：`paper-agent-backend/src/main/java/com/paperagent/controller/DiscoveryController.java`
  - 增加轻量论文检索接口。

### 后端测试文件

- 新增：`paper-agent-backend/src/test/java/com/paperagent/service/PaperServiceTest.java`
- 新增：`paper-agent-backend/src/test/java/com/paperagent/service/EvidenceSearchServiceTest.java`
- 新增：`paper-agent-backend/src/test/java/com/paperagent/service/ChatServiceTest.java`
- 新增：`paper-agent-backend/src/test/java/com/paperagent/service/DiscoveryServiceTest.java`
- 新增：`paper-agent-backend/src/test/java/com/paperagent/controller/PaperControllerTest.java`
- 新增：`paper-agent-backend/src/test/java/com/paperagent/controller/SearchControllerTest.java`
- 新增：`paper-agent-backend/src/test/java/com/paperagent/controller/DiscoveryControllerTest.java`

### 前端需要修改或新增的文件

- 修改：`paper-agent-frontend/src/types/index.ts`
  - 增加阶段二 DTO 类型。
- 修改：`paper-agent-frontend/src/api/client.ts`
  - 增加编辑、删除、标签、语义检索、可溯源问答、外部检索 API。
- 修改：`paper-agent-frontend/src/components/layout/Layout.tsx`
  - 增加论文检索视图。
- 修改：`paper-agent-frontend/src/components/layout/Sidebar.tsx`
  - 增加“论文检索”导航项。
- 修改：`paper-agent-frontend/src/components/library/LibraryPage.tsx`
  - 增加筛选、编辑、删除和更高密度文献库 UI。
- 修改：`paper-agent-frontend/src/components/library/PaperCard.tsx`
  - 展示标签和管理操作。
- 新增：`paper-agent-frontend/src/components/library/PaperEditPanel.tsx`
  - 编辑论文元数据、笔记和标签。
- 新增：`paper-agent-frontend/src/components/chat/EvidenceList.tsx`
  - 展示 RAG 来源证据。
- 修改：`paper-agent-frontend/src/components/chat/ChatWindow.tsx`
  - 增加问答范围控制。
- 修改：`paper-agent-frontend/src/components/chat/ChatMessage.tsx`
  - 渲染证据来源。
- 修改：`paper-agent-frontend/src/store/chatStore.ts`
  - 支持可溯源问答响应。
- 新增：`paper-agent-frontend/src/components/discovery/DiscoveryPage.tsx`
  - 外部论文检索页面。

---

## 任务 1：后端论文元数据模型和 DTO

**文件：**

- 修改：`paper-agent-backend/src/main/resources/schema.sql`
- 修改：`paper-agent-backend/src/main/java/com/paperagent/entity/Paper.java`
- 修改：`paper-agent-backend/src/main/java/com/paperagent/dto/PaperListItem.java`
- 修改：`paper-agent-backend/src/main/java/com/paperagent/dto/PaperSummaryResponse.java`
- 新增：`paper-agent-backend/src/main/java/com/paperagent/dto/PaperUpdateRequest.java`
- 测试：`paper-agent-backend/src/test/java/com/paperagent/service/PaperServiceTest.java`

- [ ] **步骤 1：先写失败测试**

新增 `PaperServiceTest.java`，测试 `updatePaper` 能保存标题、作者、DOI、来源链接、发表日期、摘要、笔记和标签。

```java
package com.paperagent.service;

import com.paperagent.dto.PaperUpdateRequest;
import com.paperagent.entity.Paper;
import com.paperagent.repository.PaperRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperServiceTest {

    @Mock PaperRepository paperRepository;
    @Mock PdfParserService pdfParserService;
    @Mock EmbeddingService embeddingService;
    @Mock ChatService chatService;

    @InjectMocks PaperService paperService;

    @Test
    void updatePaperMetadataPersistsPhase2Fields() {
        Paper paper = Paper.builder()
                .id(7L)
                .title("Old title")
                .authors("Old authors")
                .filename("paper.pdf")
                .filePath("uploads/papers/paper.pdf")
                .status(Paper.PaperStatus.READY)
                .build();

        PaperUpdateRequest request = new PaperUpdateRequest(
                "New title",
                "Alice; Bob",
                "10.1234/demo",
                "https://example.test/paper",
                LocalDate.of(2025, 5, 1),
                "Updated summary",
                "Important baseline paper",
                List.of("rag", "survey")
        );

        when(paperRepository.findById(7L)).thenReturn(Optional.of(paper));
        when(paperRepository.save(paper)).thenReturn(paper);

        Paper updated = paperService.updatePaper(7L, request);

        assertThat(updated.getTitle()).isEqualTo("New title");
        assertThat(updated.getAuthors()).isEqualTo("Alice; Bob");
        assertThat(updated.getDoi()).isEqualTo("10.1234/demo");
        assertThat(updated.getSourceUrl()).isEqualTo("https://example.test/paper");
        assertThat(updated.getPublishedAt()).isEqualTo(LocalDate.of(2025, 5, 1));
        assertThat(updated.getSummary()).isEqualTo("Updated summary");
        assertThat(updated.getNotes()).isEqualTo("Important baseline paper");
        assertThat(updated.getTags()).isEqualTo("rag,survey");
    }
}
```

- [ ] **步骤 2：运行测试，确认失败**

```powershell
cd paper-agent-backend
.\mvnw.cmd -Dtest=PaperServiceTest test
```

预期：失败，因为 `PaperUpdateRequest`、阶段二字段和 `PaperService#updatePaper` 尚不存在。

- [ ] **步骤 3：扩展数据库 schema**

在 `papers` 表中 `summary TEXT,` 后添加：

```sql
    doi         VARCHAR(255),
    source_url  VARCHAR(1000),
    published_at DATE,
    notes       TEXT,
    tags        TEXT,
```

- [ ] **步骤 4：扩展 `Paper` 实体**

在 `Paper.java` 中添加：

```java
import java.time.LocalDate;
```

在 `summary` 字段后添加：

```java
    @Column(length = 255)
    private String doi;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "published_at")
    private LocalDate publishedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(columnDefinition = "TEXT")
    private String tags;
```

- [ ] **步骤 5：新增 `PaperUpdateRequest`**

```java
package com.paperagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record PaperUpdateRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 500, message = "标题不能超过 500 个字符")
        String title,

        @Size(max = 1000, message = "作者不能超过 1000 个字符")
        String authors,

        @Size(max = 255, message = "DOI 不能超过 255 个字符")
        String doi,

        @Size(max = 1000, message = "来源链接不能超过 1000 个字符")
        String sourceUrl,

        LocalDate publishedAt,
        String summary,
        String notes,
        List<String> tags
) {}
```

- [ ] **步骤 6：在 `PaperService` 增加更新和标签序列化**

添加 import：

```java
import com.paperagent.dto.PaperUpdateRequest;
import java.util.stream.Collectors;
```

添加方法：

```java
    @Transactional
    public Paper updatePaper(Long id, PaperUpdateRequest request) {
        Paper paper = getPaper(id);
        paper.setTitle(request.title().trim());
        paper.setAuthors(blankToNull(request.authors()));
        paper.setDoi(blankToNull(request.doi()));
        paper.setSourceUrl(blankToNull(request.sourceUrl()));
        paper.setPublishedAt(request.publishedAt());
        paper.setSummary(blankToNull(request.summary()));
        paper.setNotes(blankToNull(request.notes()));
        paper.setTags(serializeTags(request.tags()));
        return paperRepository.save(paper);
    }

    public static List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .toList();
    }

    public static String serializeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        String value = tags.stream()
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
        return value.isBlank() ? null : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
```

- [ ] **步骤 7：扩展返回 DTO**

`PaperListItem` 增加作者、摘要、标签、DOI、来源链接和发表日期。

`PaperSummaryResponse` 增加 DOI、来源链接、发表日期、笔记和标签。

字段名必须和前端类型保持一致：`authors`、`summary`、`tags`、`doi`、`sourceUrl`、`publishedAt`、`notes`。

- [ ] **步骤 8：运行测试**

```powershell
cd paper-agent-backend
.\mvnw.cmd -Dtest=PaperServiceTest test
```

预期：PASS。

- [ ] **步骤 9：编译后端**

```powershell
cd paper-agent-backend
.\mvnw.cmd compile
```

预期：BUILD SUCCESS。

---

## 任务 2：后端本地文献库 API

**文件：**

- 修改：`paper-agent-backend/src/main/java/com/paperagent/repository/PaperRepository.java`
- 修改：`paper-agent-backend/src/main/java/com/paperagent/service/PaperService.java`
- 修改：`paper-agent-backend/src/main/java/com/paperagent/controller/PaperController.java`
- 测试：`paper-agent-backend/src/test/java/com/paperagent/controller/PaperControllerTest.java`

- [ ] **步骤 1：先写失败测试**

新增 `PaperControllerTest.java`，覆盖：

- `GET /api/papers?query=&status=&tag=` 会把筛选条件传给 service。
- `PATCH /api/papers/{id}` 会返回更新后的论文详情。
- `DELETE /api/papers/{id}` 会调用删除逻辑并返回 204。

- [ ] **步骤 2：运行测试，确认失败**

```powershell
cd paper-agent-backend
.\mvnw.cmd -Dtest=PaperControllerTest test
```

预期：失败，因为筛选、编辑、删除接口尚不存在。

- [ ] **步骤 3：扩展 `PaperRepository`**

新增 `search` 查询：

```java
    @Query("""
        SELECT p FROM Paper p
        WHERE (:status IS NULL OR p.status = :status)
          AND (:query IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(COALESCE(p.authors, '')) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(COALESCE(p.summary, '')) LIKE LOWER(CONCAT('%', :query, '%')))
          AND (:tag IS NULL OR LOWER(COALESCE(p.tags, '')) LIKE LOWER(CONCAT('%', :tag, '%')))
        ORDER BY p.createdAt DESC
        """)
    List<Paper> search(String query, Paper.PaperStatus status, String tag);
```

- [ ] **步骤 4：扩展 `PaperService`**

新增：

```java
    public List<Paper> searchPapers(String query, PaperStatus status, String tag) {
        return paperRepository.search(blankToNull(query), status, blankToNull(tag));
    }

    public List<String> getAllTags() {
        return paperRepository.findAll().stream()
                .flatMap(paper -> parseTags(paper.getTags()).stream())
                .distinct()
                .sorted()
                .toList();
    }

    @Transactional
    public void deletePaper(Long id) {
        Paper paper = getPaper(id);
        embeddingService.deleteByPaperId(id);
        paperRepository.delete(paper);
        try {
            Files.deleteIfExists(Paths.get(paper.getFilePath()));
        } catch (IOException e) {
            log.warn("Failed to delete file for paper {}: {}", id, e.getMessage());
        }
    }
```

- [ ] **步骤 5：扩展 `PaperController`**

新增或修改接口：

- `GET /api/papers?query=&status=&tag=`
- `PATCH /api/papers/{id}`
- `DELETE /api/papers/{id}`
- `GET /api/papers/tags`

`toSummaryResponse` 必须返回阶段二新增字段。

- [ ] **步骤 6：运行测试**

```powershell
cd paper-agent-backend
.\mvnw.cmd -Dtest=PaperControllerTest test
```

预期：PASS。

- [ ] **步骤 7：后端编译**

```powershell
cd paper-agent-backend
.\mvnw.cmd compile
```

预期：BUILD SUCCESS。

---

## 任务 3：后端证据检索 API

**文件：**

- 新增：`EvidenceChunk.java`
- 新增：`SemanticSearchRequest.java`
- 新增：`SemanticSearchResponse.java`
- 新增：`EvidenceSearchService.java`
- 新增：`SearchController.java`
- 测试：`EvidenceSearchServiceTest.java`
- 测试：`SearchControllerTest.java`

- [ ] **步骤 1：先写失败测试**

`EvidenceSearchServiceTest` 验证：当 `paperId` 为 null 时，可以执行全库检索，并返回包含论文标题、chunk id、chunk index、内容和相似度的 `EvidenceChunk`。

`SearchControllerTest` 验证：`POST /api/search/semantic` 返回结构化证据。

- [ ] **步骤 2：运行测试，确认失败**

```powershell
cd paper-agent-backend
.\mvnw.cmd -Dtest=EvidenceSearchServiceTest,SearchControllerTest test
```

预期：失败，因为 DTO、service、controller 尚不存在。

- [ ] **步骤 3：新增 DTO**

`EvidenceChunk`：

```java
public record EvidenceChunk(
        Long chunkId,
        Long paperId,
        String paperTitle,
        Integer chunkIndex,
        String content,
        Double similarity
) {}
```

`SemanticSearchRequest`：

```java
public record SemanticSearchRequest(
        @NotBlank(message = "检索问题不能为空")
        String query,
        Long paperId,
        @Min(1) @Max(20) Integer limit
) {
    public int safeLimit() {
        return limit == null ? 8 : limit;
    }
}
```

`SemanticSearchResponse`：

```java
public record SemanticSearchResponse(
        String query,
        Long paperId,
        List<EvidenceChunk> evidence
) {}
```

- [ ] **步骤 4：新增 `EvidenceSearchService`**

实现：

- 使用 `EmbeddingModel` 对 query 向量化。
- 使用 JdbcTemplate 查询 `paper_chunks`。
- 当 `paperId == null` 时检索全部 READY 论文。
- 当 `paperId != null` 时只检索指定论文。
- 返回 `EvidenceChunk` 列表。
- 捕获异常并返回空列表，避免聊天流程崩溃。

- [ ] **步骤 5：新增 `SearchController`**

接口：

```java
@PostMapping("/semantic")
public SemanticSearchResponse semantic(@Valid @RequestBody SemanticSearchRequest request)
```

路径：`/api/search/semantic`。

- [ ] **步骤 6：运行测试**

```powershell
cd paper-agent-backend
.\mvnw.cmd -Dtest=EvidenceSearchServiceTest,SearchControllerTest test
```

预期：PASS。

---

## 任务 4：后端可溯源 RAG API

**文件：**

- 新增：`TraceableChatRequest.java`
- 新增：`TraceableChatResponse.java`
- 修改：`ChatService.java`
- 修改：`ChatController.java`
- 测试：`ChatServiceTest.java`

- [ ] **步骤 1：先写失败测试**

`ChatServiceTest` 覆盖：

- 证据为空时，不调用模型，直接返回“本地文献库中没有足够信息”。
- 证据存在时，返回模型答案，并保留 evidence 列表。

- [ ] **步骤 2：运行测试，确认失败**

```powershell
cd paper-agent-backend
.\mvnw.cmd -Dtest=ChatServiceTest test
```

预期：失败，因为可溯源 DTO 和 `chatWithEvidence` 尚不存在。

- [ ] **步骤 3：新增 DTO**

`TraceableChatRequest`：

```java
public record TraceableChatRequest(
        @NotBlank(message = "消息不能为空")
        String message,
        Long paperId,
        String scope
) {
    public String safeScope() {
        return scope == null || scope.isBlank() ? "paper" : scope;
    }
}
```

`TraceableChatResponse`：

```java
public record TraceableChatResponse(
        String answer,
        List<EvidenceChunk> evidence
) {}
```

- [ ] **步骤 4：扩展 `ChatService`**

新增依赖：

```java
private final EvidenceSearchService evidenceSearchService;
```

新增方法：

```java
public TraceableChatResponse chatWithEvidence(String userMessage, Long paperId, String scope)
```

行为：

- `scope == "library"` 时，全库检索。
- 否则使用 `paperId` 做单篇检索。
- 没有证据时返回证据不足提示。
- 有证据时，构造严格 grounding prompt，调用模型生成回答，并返回 evidence。

- [ ] **步骤 5：扩展 `ChatController`**

新增接口：

```java
@PostMapping("/rag")
public TraceableChatResponse chatRag(@Valid @RequestBody TraceableChatRequest request)
```

路径：`POST /api/chat/rag`。

- [ ] **步骤 6：运行测试和编译**

```powershell
cd paper-agent-backend
.\mvnw.cmd -Dtest=ChatServiceTest test
.\mvnw.cmd compile
```

预期：PASS / BUILD SUCCESS。

---

## 任务 5：后端轻量外部论文检索 API

**文件：**

- 新增：`DiscoveryResult.java`
- 新增：`ArxivClient.java`
- 新增：`SemanticScholarClient.java`
- 新增：`DiscoveryService.java`
- 新增：`DiscoveryController.java`
- 测试：`DiscoveryServiceTest.java`
- 测试：`DiscoveryControllerTest.java`

- [ ] **步骤 1：先写失败测试**

`DiscoveryServiceTest` 验证：

- `source=all` 时合并 arXiv 和 Semantic Scholar 结果。
- 单个外部源失败时，不影响另一个源返回结果。

`DiscoveryControllerTest` 验证：

- `GET /api/discovery/search?query=rag` 返回标题、来源和外部链接。

- [ ] **步骤 2：运行测试，确认失败**

```powershell
cd paper-agent-backend
.\mvnw.cmd -Dtest=DiscoveryServiceTest,DiscoveryControllerTest test
```

预期：失败，因为 discovery 相关类尚不存在。

- [ ] **步骤 3：新增 `DiscoveryResult`**

```java
public record DiscoveryResult(
        String externalId,
        String source,
        String title,
        List<String> authors,
        String year,
        String abstractText,
        String landingUrl,
        String pdfUrl,
        String doi
) {}
```

- [ ] **步骤 4：新增外部客户端**

`ArxivClient`：

- 调用 `https://export.arxiv.org/api/query`。
- 解析 Atom XML。
- 返回 `DiscoveryResult`。

`SemanticScholarClient`：

- 调用 `https://api.semanticscholar.org/graph/v1/paper/search`。
- 查询字段：`title,authors,year,abstract,url,externalIds,openAccessPdf`。
- 返回 `DiscoveryResult`。

- [ ] **步骤 5：新增 `DiscoveryService`**

行为：

- `source=all` 时查询两个来源。
- `source=arxiv` 时只查 arXiv。
- `source=semantic-scholar` 时只查 Semantic Scholar。
- 默认 limit 为 10，最大 20。
- 单个来源失败时记录 warning，并返回其他来源结果。

- [ ] **步骤 6：新增 `DiscoveryController`**

接口：

```java
GET /api/discovery/search?query=&source=&limit=
```

返回 `List<DiscoveryResult>`。

- [ ] **步骤 7：运行测试**

```powershell
cd paper-agent-backend
.\mvnw.cmd -Dtest=DiscoveryServiceTest,DiscoveryControllerTest test
```

预期：PASS。

---

## 任务 6：前端类型和 API Client

**文件：**

- 修改：`paper-agent-frontend/src/types/index.ts`
- 修改：`paper-agent-frontend/src/api/client.ts`

- [ ] **步骤 1：扩展前端类型**

新增或扩展：

- `PaperListItem`
- `PaperSummary`
- `PaperUpdateRequest`
- `EvidenceChunk`
- `SemanticSearchRequest`
- `SemanticSearchResponse`
- `TraceableChatRequest`
- `TraceableChatResponse`
- `DiscoveryResult`

`ChatMessage` 增加可选字段：

```ts
evidence?: EvidenceChunk[];
```

- [ ] **步骤 2：扩展 API client**

新增方法：

- `getPapers(filters?: { query?: string; status?: string; tag?: string })`
- `updatePaper(id: number, payload: PaperUpdateRequest)`
- `deletePaper(id: number)`
- `getTags()`
- `semanticSearch(req: SemanticSearchRequest)`
- `traceableChat(req: TraceableChatRequest)`
- `discoverPapers(params: { query: string; source: 'all' | 'arxiv' | 'semantic-scholar'; limit?: number })`

- [ ] **步骤 3：运行前端构建**

```powershell
cd paper-agent-frontend
npm run build
```

预期：PASS。现有 UI 在类型扩展后仍应能通过构建。

---

## 任务 7：前端文献库管理 UI

**文件：**

- 修改：`LibraryPage.tsx`
- 修改：`PaperCard.tsx`
- 新增：`PaperEditPanel.tsx`

- [ ] **步骤 1：新增 `PaperEditPanel`**

功能：

- 编辑标题、作者、标签、DOI、来源链接、发表日期、笔记和摘要。
- 标签输入使用逗号分隔。
- 保存时调用 `onSave(payload)`。
- 关闭时调用 `onClose()`。

- [ ] **步骤 2：扩展 `PaperCard`**

新增 props：

```ts
onEdit: (paperId: number) => void;
onDelete: (paperId: number) => void;
```

卡片展示：

- 标题
- 作者
- 文件名
- 状态
- 标签
- 摘要预览
- 开始对话
- 编辑
- 删除

- [ ] **步骤 3：扩展 `LibraryPage`**

新增状态：

```ts
const [query, setQuery] = useState('');
const [status, setStatus] = useState('');
const [tag, setTag] = useState('');
const [tags, setTags] = useState<string[]>([]);
const [editingPaper, setEditingPaper] = useState<PaperSummary | null>(null);
```

行为：

- 加载论文时带上筛选条件。
- 加载所有 tags。
- 点击编辑时调用 `getPaper(id)` 并打开编辑面板。
- 保存时调用 `updatePaper`。
- 删除前使用 `window.confirm('确定删除这篇论文吗？')`。
- 删除成功后刷新列表。

- [ ] **步骤 4：运行前端构建**

```powershell
cd paper-agent-frontend
npm run build
```

预期：PASS。

---

## 任务 8：前端可溯源聊天 UI

**文件：**

- 新增：`EvidenceList.tsx`
- 修改：`ChatMessage.tsx`
- 修改：`ChatWindow.tsx`
- 修改：`chatStore.ts`

- [ ] **步骤 1：新增 `EvidenceList`**

功能：

- 接收 `evidence?: EvidenceChunk[]`。
- 没有证据时不渲染。
- 有证据时展示来源标题、相似度和片段内容。
- 点击证据时调用 `onSelectPaper(paperId)`。

- [ ] **步骤 2：在 `ChatMessage` 渲染证据**

给 `ChatMessage` 增加：

```ts
onSelectPaper?: (paperId: number) => void;
```

助手消息下方渲染：

```tsx
<EvidenceList evidence={message.evidence} onSelectPaper={onSelectPaper} />
```

- [ ] **步骤 3：扩展 `chatStore`**

新增状态：

```ts
scope: 'paper' | 'library';
setScope: (scope: 'paper' | 'library') => void;
```

行为：

- `scope === 'paper'` 时保留当前流式问答。
- `scope === 'library'` 时调用 `traceableChat`。
- 将返回的 `answer` 和 `evidence` 写入助手消息。

- [ ] **步骤 4：扩展 `ChatWindow`**

新增范围切换控件：

- 当前论文
- 全部文献库

将 `setSelectedPaper` 传给 `ChatMessage`，用于点击证据后切换论文上下文。

- [ ] **步骤 5：运行前端构建**

```powershell
cd paper-agent-frontend
npm run build
```

预期：PASS。

---

## 任务 9：前端论文检索页

**文件：**

- 新增：`DiscoveryPage.tsx`
- 修改：`Layout.tsx`
- 修改：`Sidebar.tsx`

- [ ] **步骤 1：新增 `DiscoveryPage`**

页面包含：

- 搜索框。
- 来源选择器：全部来源、arXiv、Semantic Scholar。
- 检索按钮。
- 结果列表。

结果卡片展示：

- 标题。
- 来源。
- 作者。
- 年份。
- 摘要片段。
- 打开页面链接。
- PDF 链接，如果 API 提供。
- DOI，如果 API 提供。

页面文案必须明确：这里只提供外部直达链接，不自动下载或入库。

- [ ] **步骤 2：扩展 `Layout`**

视图类型增加：

```ts
type View = 'chat' | 'library' | 'discovery';
```

增加渲染：

```tsx
{currentView === 'discovery' && <DiscoveryPage />}
```

- [ ] **步骤 3：扩展 `Sidebar`**

新增导航：

```tsx
<button className={linkClass('discovery')} onClick={() => onNavigate('discovery')}>
  <Search size={18} /> 论文检索
</button>
```

- [ ] **步骤 4：运行前端构建**

```powershell
cd paper-agent-frontend
npm run build
```

预期：PASS。

---

## 任务 10：完整验证

**文件：**

- 不新增业务文件，只验证。

- [ ] **步骤 1：运行后端测试**

```powershell
cd paper-agent-backend
.\mvnw.cmd test
```

预期：BUILD SUCCESS。

- [ ] **步骤 2：运行后端编译**

```powershell
cd paper-agent-backend
.\mvnw.cmd compile
```

预期：BUILD SUCCESS。

- [ ] **步骤 3：运行前端构建**

```powershell
cd paper-agent-frontend
npm run build
```

预期：TypeScript 和 Vite 构建成功。

- [ ] **步骤 4：启动本地应用手动验证**

后端：

```powershell
cd paper-agent-backend
.\mvnw.cmd spring-boot:run
```

前端：

```powershell
cd paper-agent-frontend
npm run dev
```

预期：

- 后端监听 `http://localhost:8080`。
- 前端监听 Vite 输出的本地地址，通常是 `http://localhost:5173`。

- [ ] **步骤 5：浏览器冒烟验证**

在浏览器中验证：

- 侧边栏有“对话”“文献库”“论文检索”。
- 文献库能筛选、编辑、删除、上传。
- 编辑论文后，标签和笔记能保存。
- 聊天页切到“全部文献库”后，回答下方能显示证据来源。
- 论文检索页搜索 `retrieval augmented generation`，结果卡片能展示外部链接。

- [ ] **步骤 6：最终报告验证结果**

最终回复中包含：

```text
已验证：
- 后端测试：PASS / 未运行，原因
- 后端编译：PASS / 未运行，原因
- 前端构建：PASS / 未运行，原因
- 浏览器冒烟验证：PASS / 未运行，原因
```

---

## Git 说明

当前 `c:\paperagent` 不是 Git 仓库，`git status` 返回：

```text
fatal: not a git repository (or any of the parent directories): .git
```

因此执行阶段二时不能按任务 commit。若后续初始化 Git 仓库，建议每完成一个任务提交一次，提交信息示例：

- `feat(backend): add phase 2 paper metadata`
- `feat(backend): add library management endpoints`
- `feat(backend): add evidence search`
- `feat(backend): add traceable rag endpoint`
- `feat(backend): add external discovery search`
- `feat(frontend): add phase 2 api client types`
- `feat(frontend): add library management ui`
- `feat(frontend): add traceable chat evidence`
- `feat(frontend): add paper discovery page`
