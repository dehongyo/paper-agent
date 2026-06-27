# Paper Review Agent — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a paper review agent that lets users select a review template (built-in or uploaded PDF/DOC) and get AI-powered multi-dimensional review with source-cited comments.

**Architecture:** New Spring Boot controller/service layer independent of existing modules, backed by two new JPA entities (`ReviewTemplate`, `ReviewSession`). Frontend adds a `review` view with paper/template selection and a streaming review result + follow-up chat.

**Tech Stack:** Spring Boot 4.1.0, Spring AI 2.0.0, Apache PDFBox, Apache POI, React 18 + TypeScript + Tailwind CSS

---

## Environment

All Maven commands require Java 21:

```powershell
$env:JAVA_HOME="C:\jdks\openlogic-openjdk-21.0.11+10-windows-x64\openlogic-openjdk-21.0.11+10-windows-x64"
$env:DASHSCOPE_API_KEY="YOUR_DASHSCOPE_API_KEY"
```

---

### Task 1: Add Apache POI dependency and update schema.sql

**Files:**
- Modify: `paper-agent-backend/pom.xml` (add POI dependency)
- Modify: `paper-agent-backend/src/main/resources/schema.sql` (add two tables)

- [ ] **Step 1: Add POI dependency to pom.xml**

In `pom.xml`, after the PDFBox dependency block (line 74-79), add:

```xml
        <!-- DOC/DOCX 解析 -->
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>5.3.0</version>
        </dependency>
```

- [ ] **Step 2: Add review_template and review_session tables to schema.sql**

Append to `schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS review_template (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'custom',
    source_filename VARCHAR(255),
    content_text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS review_session (
    id BIGSERIAL PRIMARY KEY,
    paper_id BIGINT NOT NULL REFERENCES papers(id) ON DELETE CASCADE,
    template_id BIGINT NOT NULL REFERENCES review_template(id) ON DELETE RESTRICT,
    result_text TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'in_progress',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 3: Commit**

```bash
git add paper-agent-backend/pom.xml paper-agent-backend/src/main/resources/schema.sql
git commit -m "feat: add POI dependency and review schema tables"
```

---

### Task 2: Create backend entities

**Files:**
- Create: `paper-agent-backend/src/main/java/com/paperagent/entity/ReviewTemplate.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/entity/ReviewSession.java`

- [ ] **Step 1: Create ReviewTemplate entity**

```java
package com.paperagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_template")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 50)
    private String type; // "builtin" or "custom"

    @Column(name = "source_filename", length = 255)
    private String sourceFilename;

    @Column(name = "content_text", nullable = false, columnDefinition = "TEXT")
    private String contentText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 2: Create ReviewSession entity**

```java
package com.paperagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_session")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paper_id", nullable = false)
    private Long paperId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "result_text", columnDefinition = "TEXT")
    private String resultText;

    @Column(nullable = false, length = 20)
    private String status; // "in_progress" or "completed"

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "in_progress";
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
export JAVA_HOME="/c/jdks/openlogic-openjdk-21.0.11+10-windows-x64/openlogic-openjdk-21.0.11+10-windows-x64"
cd paper-agent-backend && ./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add paper-agent-backend/src/main/java/com/paperagent/entity/ReviewTemplate.java paper-agent-backend/src/main/java/com/paperagent/entity/ReviewSession.java
git commit -m "feat: add ReviewTemplate and ReviewSession entities"
```

---

### Task 3: Create repositories

**Files:**
- Create: `paper-agent-backend/src/main/java/com/paperagent/repository/ReviewTemplateRepository.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/repository/ReviewSessionRepository.java`

- [ ] **Step 1: Create ReviewTemplateRepository**

```java
package com.paperagent.repository;

import com.paperagent.entity.ReviewTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewTemplateRepository extends JpaRepository<ReviewTemplate, Long> {
    List<ReviewTemplate> findAllByOrderByCreatedAtDesc();
    List<ReviewTemplate> findByType(String type);
    long countByType(String type);
}
```

- [ ] **Step 2: Create ReviewSessionRepository**

```java
package com.paperagent.repository;

import com.paperagent.entity.ReviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewSessionRepository extends JpaRepository<ReviewSession, Long> {
    List<ReviewSession> findAllByOrderByCreatedAtDesc();
    List<ReviewSession> findByPaperIdOrderByCreatedAtDesc(Long paperId);
}
```

- [ ] **Step 3: Verify compilation**

```bash
cd paper-agent-backend && ./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add paper-agent-backend/src/main/java/com/paperagent/repository/ReviewTemplateRepository.java paper-agent-backend/src/main/java/com/paperagent/repository/ReviewSessionRepository.java
git commit -m "feat: add review repositories"
```

---

### Task 4: Create DTOs

**Files:**
- Create: `paper-agent-backend/src/main/java/com/paperagent/dto/ReviewStartRequest.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/dto/ReviewContinueRequest.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/dto/TemplateResponse.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/dto/ReviewSessionResponse.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/dto/ReviewStartEvent.java`

- [ ] **Step 1: Create ReviewStartRequest**

```java
package com.paperagent.dto;

import jakarta.validation.constraints.NotNull;

public record ReviewStartRequest(
        @NotNull Long paperId,
        @NotNull Long templateId
) {}
```

- [ ] **Step 2: Create ReviewContinueRequest**

```java
package com.paperagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReviewContinueRequest(
        @NotNull Long sessionId,
        @NotBlank String message
) {}
```

- [ ] **Step 3: Create TemplateResponse**

```java
package com.paperagent.dto;

import java.time.LocalDateTime;

public record TemplateResponse(
        Long id,
        String name,
        String type,
        String sourceFilename,
        int contentLength,
        LocalDateTime createdAt
) {}
```

- [ ] **Step 4: Create ReviewSessionResponse**

```java
package com.paperagent.dto;

import java.time.LocalDateTime;

public record ReviewSessionResponse(
        Long id,
        Long paperId,
        String paperTitle,
        Long templateId,
        String templateName,
        String status,
        String resultText,
        LocalDateTime createdAt
) {}
```

- [ ] **Step 5: Create ReviewStartEvent (SSE event type)**

```java
package com.paperagent.dto;

public record ReviewStartEvent(
        String type,    // "text", "done", "error"
        String content,
        String message
) {
    public static ReviewStartEvent text(String content) {
        return new ReviewStartEvent("text", content, null);
    }
    public static ReviewStartEvent done() {
        return new ReviewStartEvent("done", null, "Review complete");
    }
    public static ReviewStartEvent error(String message) {
        return new ReviewStartEvent("error", null, message);
    }
}
```

- [ ] **Step 6: Verify compilation**

```bash
cd paper-agent-backend && ./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add paper-agent-backend/src/main/java/com/paperagent/dto/ReviewStartRequest.java paper-agent-backend/src/main/java/com/paperagent/dto/ReviewContinueRequest.java paper-agent-backend/src/main/java/com/paperagent/dto/TemplateResponse.java paper-agent-backend/src/main/java/com/paperagent/dto/ReviewSessionResponse.java paper-agent-backend/src/main/java/com/paperagent/dto/ReviewStartEvent.java
git commit -m "feat: add review DTOs"
```

---

### Task 5: Create TemplateService (template management + built-in seeding)

**Files:**
- Create: `paper-agent-backend/src/main/java/com/paperagent/service/TemplateService.java`

- [ ] **Step 1: Create TemplateService**

```java
package com.paperagent.service;

import com.paperagent.dto.TemplateResponse;
import com.paperagent.entity.ReviewTemplate;
import com.paperagent.repository.ReviewTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final ReviewTemplateRepository templateRepository;

    private static final String BUILTIN_THESIS = """
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
            """;

    private static final String BUILTIN_IEEE = """
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
            """;

    @Transactional
    public void seedBuiltinTemplates() {
        if (templateRepository.countByType("builtin") == 0) {
            templateRepository.save(ReviewTemplate.builder()
                    .name("学位论文评审")
                    .type("builtin")
                    .contentText(BUILTIN_THESIS)
                    .build());
            templateRepository.save(ReviewTemplate.builder()
                    .name("IEEE / 期刊论文评审")
                    .type("builtin")
                    .contentText(BUILTIN_IEEE)
                    .build());
            log.info("Seeded 2 built-in review templates");
        }
    }

    public List<TemplateResponse> listTemplates() {
        return templateRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public TemplateResponse getTemplate(Long id) {
        ReviewTemplate t = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
        return toResponse(t);
    }

    public String getTemplateContent(Long id) {
        return templateRepository.findById(id)
                .map(ReviewTemplate::getContentText)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
    }

    @Transactional
    public TemplateResponse uploadTemplate(MultipartFile file) {
        String filename = file.getOriginalFilename();
        String content = extractText(file, filename);
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Failed to extract text from uploaded file");
        }

        String name = filename != null && filename.contains(".")
                ? filename.substring(0, filename.lastIndexOf('.'))
                : (filename != null ? filename : "custom-template");

        ReviewTemplate template = templateRepository.save(ReviewTemplate.builder()
                .name(name)
                .type("custom")
                .sourceFilename(filename)
                .contentText(content)
                .build());

        log.info("Uploaded review template: {} ({} chars)", name, content.length());
        return toResponse(template);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        ReviewTemplate t = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
        if ("builtin".equals(t.getType())) {
            throw new IllegalArgumentException("Cannot delete built-in templates");
        }
        templateRepository.deleteById(id);
        log.info("Deleted review template: {}", t.getName());
    }

    private String extractText(MultipartFile file, String filename) {
        try {
            String lower = filename != null ? filename.toLowerCase() : "";
            if (lower.endsWith(".pdf")) {
                try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
                    return new PDFTextStripper().getText(doc);
                }
            } else if (lower.endsWith(".docx")) {
                try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
                    return new XWPFWordExtractor(doc).getText();
                }
            } else if (lower.endsWith(".doc")) {
                // Old .doc format — POI HWPF
                try (var fis = file.getInputStream()) {
                    org.apache.poi.hwpf.HWPFDocument doc = new org.apache.poi.hwpf.HWPFDocument(fis);
                    return doc.getText().toString();
                }
            } else {
                throw new IllegalArgumentException(
                        "Unsupported file type: " + filename + ". Only PDF, DOC, and DOCX are accepted.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse file: " + filename, e);
        }
    }

    private TemplateResponse toResponse(ReviewTemplate t) {
        return new TemplateResponse(
                t.getId(),
                t.getName(),
                t.getType(),
                t.getSourceFilename(),
                t.getContentText() != null ? t.getContentText().length() : 0,
                t.getCreatedAt()
        );
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd paper-agent-backend && ./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add paper-agent-backend/src/main/java/com/paperagent/service/TemplateService.java
git commit -m "feat: add TemplateService with built-in seeding and PDF/DOC parsing"
```

---

### Task 6: Create ReviewService (core review logic)

**Files:**
- Create: `paper-agent-backend/src/main/java/com/paperagent/service/ReviewService.java`
- Modify: `paper-agent-backend/src/main/java/com/paperagent/service/PaperService.java` (add getFullText method if not exists)

- [ ] **Step 1: Check PaperService for full-text access method**

Read `PaperService.java` to find if there's already a `getFullText(Long id)` style method. The Paper entity has no `fullText` field — text is stored in `paper_chunks`. We need to use the existing `PaperService` to get paper text or read from chunks.

Check: `PaperService.java` line ~30 to see what methods are available for getting paper text.

- [ ] **Step 2: Create ReviewService**

```java
package com.paperagent.service;

import com.paperagent.dto.ReviewContinueRequest;
import com.paperagent.dto.ReviewSessionResponse;
import com.paperagent.dto.ReviewStartEvent;
import com.paperagent.dto.ReviewStartRequest;
import com.paperagent.entity.Paper;
import com.paperagent.entity.PaperChunk;
import com.paperagent.entity.ReviewSession;
import com.paperagent.repository.PaperChunkRepository;
import com.paperagent.repository.PaperRepository;
import com.paperagent.repository.ReviewSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ChatClient chatClient;
    private final ReviewSessionRepository sessionRepository;
    private final PaperRepository paperRepository;
    private final PaperChunkRepository chunkRepository;
    private final TemplateService templateService;

    @Transactional
    public Flux<ReviewStartEvent> startReview(ReviewStartRequest request) {
        Long paperId = request.paperId();
        Long templateId = request.templateId();

        // Load paper full text from chunks
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new IllegalArgumentException("Paper not found: " + paperId));
        List<PaperChunk> chunks = chunkRepository.findByPaperIdOrderByChunkIndex(paperId);
        String fullText = chunks.stream()
                .map(PaperChunk::getContent)
                .collect(Collectors.joining("\n\n"));

        if (fullText.isBlank()) {
            return Flux.just(ReviewStartEvent.error("The paper has no parsed content. Please wait for processing to complete."));
        }

        // Load template content
        String templateContent = templateService.getTemplateContent(templateId);

        // Create session
        ReviewSession session = sessionRepository.save(ReviewSession.builder()
                .paperId(paperId)
                .templateId(templateId)
                .status("in_progress")
                .build());

        // Build review prompt
        String prompt = buildReviewPrompt(templateContent, fullText, paper.getTitle());

        // Stream review
        StringBuilder resultBuffer = new StringBuilder();
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .map(chunk -> {
                    resultBuffer.append(chunk);
                    return ReviewStartEvent.text(chunk);
                })
                .concatWithValues(ReviewStartEvent.done())
                .doOnComplete(() -> {
                    session.setResultText(resultBuffer.toString());
                    session.setStatus("completed");
                    sessionRepository.save(session);
                    log.info("Review session {} completed for paper {}", session.getId(), paperId);
                })
                .doOnError(err -> {
                    session.setStatus("error");
                    session.setResultText(resultBuffer.toString());
                    sessionRepository.save(session);
                    log.error("Review session {} failed", session.getId(), err);
                });
    }

    public String continueReview(ReviewContinueRequest request) {
        ReviewSession session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + request.sessionId()));

        String prompt = """
                You are a rigorous academic paper reviewer. The user has a follow-up question about your previous review.

                Previous review:
                %s

                User question:
                %s

                Rules:
                1. Answer strictly based on the paper content referenced in the review above
                2. Every point must cite specific passages from the paper
                3. Use 【原文段落X：...】format for citations
                4. If the paper doesn't contain enough information, say "（当前文本无法判断）"
                5. Use Chinese, professional and constructive tone
                """.formatted(
                        truncate(session.getResultText(), 6000),
                        request.message()
                );

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    public List<ReviewSessionResponse> listSessions() {
        return sessionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public ReviewSessionResponse getSession(Long id) {
        ReviewSession s = sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + id));
        return toResponse(s);
    }

    @Transactional
    public void deleteSession(Long id) {
        sessionRepository.deleteById(id);
    }

    private String buildReviewPrompt(String templateContent, String fullText, String paperTitle) {
        // Truncate paper text if excessively long (keep first ~25000 chars for review)
        String paperText = fullText.length() > 25000
                ? fullText.substring(0, 25000) + "\n\n... (truncated for review)"
                : fullText;

        return """
                You are a rigorous academic paper reviewer. Please review the following paper according to the review criteria below.

                ## Paper Title
                %s

                ## Review Criteria (review template)
                %s

                ## Paper Content
                %s

                ## Instructions
                1. Review the paper item by item according to the criteria above.
                2. **Every review point — positive or negative — MUST quote the relevant passage from the paper** using the format 【原文段落X：...】. Literally copy the passage.
                3. For each criterion item, state your assessment clearly, then provide the text evidence.
                4. If the paper does not contain enough information to evaluate a criterion, write "（当前文本无法判断）" and explain what is missing.
                5. Be specific and constructive. Do not make vague claims.
                6. Use Chinese for all commentary. Keep a professional, helpful tone.
                7. At the end, provide an overall assessment paragraph.

                Start your review now. Follow the criteria order as listed above.
                """.formatted(paperTitle, templateContent, paperText);
    }

    private ReviewSessionResponse toResponse(ReviewSession s) {
        Paper paper = paperRepository.findById(s.getPaperId()).orElse(null);
        String templateName;
        try {
            templateName = templateService.getTemplate(s.getTemplateId()).name();
        } catch (Exception e) {
            templateName = "(deleted)";
        }
        return new ReviewSessionResponse(
                s.getId(),
                s.getPaperId(),
                paper != null ? paper.getTitle() : "(deleted)",
                s.getTemplateId(),
                templateName,
                s.getStatus(),
                s.getResultText(),
                s.getCreatedAt()
        );
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
```

- [ ] **Step 3: Check PaperChunkRepository exists with needed method**

Read `PaperChunkRepository.java` to confirm `findByPaperIdOrderByChunkIndex` exists. If not, add it:

```java
List<PaperChunk> findByPaperIdOrderByChunkIndex(Long paperId);
```

- [ ] **Step 4: Verify compilation**

```bash
cd paper-agent-backend && ./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add paper-agent-backend/src/main/java/com/paperagent/service/ReviewService.java
git commit -m "feat: add ReviewService with streaming review and follow-up"
```

---

### Task 7: Create ReviewController

**Files:**
- Create: `paper-agent-backend/src/main/java/com/paperagent/controller/ReviewController.java`

- [ ] **Step 1: Create ReviewController**

```java
package com.paperagent.controller;

import com.paperagent.dto.ReviewContinueRequest;
import com.paperagent.dto.ReviewSessionResponse;
import com.paperagent.dto.ReviewStartEvent;
import com.paperagent.dto.ReviewStartRequest;
import com.paperagent.dto.TemplateResponse;
import com.paperagent.service.ReviewService;
import com.paperagent.service.TemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final TemplateService templateService;

    // ── Templates ──

    @GetMapping("/templates")
    public List<TemplateResponse> listTemplates() {
        return templateService.listTemplates();
    }

    @GetMapping("/templates/{id}")
    public TemplateResponse getTemplate(@PathVariable Long id) {
        return templateService.getTemplate(id);
    }

    @PostMapping(value = "/templates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TemplateResponse uploadTemplate(@RequestPart("file") MultipartFile file) {
        return templateService.uploadTemplate(file);
    }

    @DeleteMapping("/templates/{id}")
    public ResponseEntity<Map<String, String>> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    // ── Review Sessions ──

    @PostMapping(value = "/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ReviewStartEvent> startReview(@Valid @RequestBody ReviewStartRequest request) {
        log.info("Starting review: paperId={}, templateId={}", request.paperId(), request.templateId());
        return reviewService.startReview(request);
    }

    @PostMapping("/continue")
    public String continueReview(@Valid @RequestBody ReviewContinueRequest request) {
        log.info("Continue review: sessionId={}", request.sessionId());
        return reviewService.continueReview(request);
    }

    @GetMapping("/sessions")
    public List<ReviewSessionResponse> listSessions() {
        return reviewService.listSessions();
    }

    @GetMapping("/sessions/{id}")
    public ReviewSessionResponse getSession(@PathVariable Long id) {
        return reviewService.getSession(id);
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Map<String, String>> deleteSession(@PathVariable Long id) {
        reviewService.deleteSession(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd paper-agent-backend && ./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add paper-agent-backend/src/main/java/com/paperagent/controller/ReviewController.java
git commit -m "feat: add ReviewController with template and review session APIs"
```

---

### Task 8: Seed built-in templates on startup

**Files:**
- Modify: `paper-agent-backend/src/main/java/com/paperagent/PaperAgentApplication.java`

- [ ] **Step 1: Update PaperAgentApplication to seed templates on startup**

```java
package com.paperagent;

import com.paperagent.service.TemplateService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class PaperAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaperAgentApplication.class, args);
    }

    @Bean
    CommandLineRunner seedTemplates(TemplateService templateService) {
        return args -> templateService.seedBuiltinTemplates();
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd paper-agent-backend && ./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add paper-agent-backend/src/main/java/com/paperagent/PaperAgentApplication.java
git commit -m "feat: seed built-in review templates on startup"
```

---

### Task 9: Verify backend compiles and backend test pass

- [ ] **Step 1: Run full compile**

```bash
export JAVA_HOME="/c/jdks/openlogic-openjdk-21.0.11+10-windows-x64/openlogic-openjdk-21.0.11+10-windows-x64"
cd paper-agent-backend && ./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 2: Run existing tests**

```bash
cd paper-agent-backend && ./mvnw test -q
```

Expected: All existing tests pass.

---

### Task 10: Add frontend TypeScript types

**Files:**
- Modify: `paper-agent-frontend/src/types/index.ts` (append new types)

- [ ] **Step 1: Append review types**

Add to the end of `index.ts`:

```typescript
// ── Review ──

export interface ReviewTemplate {
  id: number;
  name: string;
  type: 'builtin' | 'custom';
  sourceFilename: string | null;
  contentLength: number;
  createdAt: string;
}

export interface ReviewStartRequest {
  paperId: number;
  templateId: number;
}

export interface ReviewContinueRequest {
  sessionId: number;
  message: string;
}

export interface ReviewSessionResponse {
  id: number;
  paperId: number;
  paperTitle: string;
  templateId: number;
  templateName: string;
  status: 'in_progress' | 'completed' | 'error';
  resultText: string | null;
  createdAt: string;
}

export interface ReviewStartEvent {
  type: 'text' | 'done' | 'error';
  content: string;
  message: string | null;
}
```

- [ ] **Step 2: Commit**

```bash
git add paper-agent-frontend/src/types/index.ts
git commit -m "feat: add review TypeScript types"
```

---

### Task 11: Add frontend API client

**Files:**
- Create: `paper-agent-frontend/src/api/review.ts`

- [ ] **Step 1: Create review API client**

```typescript
import type {
  ReviewTemplate,
  ReviewStartRequest,
  ReviewContinueRequest,
  ReviewSessionResponse,
} from '../types';

const BASE_URL = window.location.port === '5173'
  ? '/api'
  : 'http://localhost:5173/api';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    const msg = await res.text().catch(() => 'Unknown error');
    throw new Error(`${res.status}: ${msg}`);
  }
  return res.json();
}

// ── Templates ──

export async function listTemplates(): Promise<ReviewTemplate[]> {
  return request<ReviewTemplate[]>('/review/templates');
}

export async function getTemplate(id: number): Promise<ReviewTemplate> {
  return request<ReviewTemplate>(`/review/templates/${id}`);
}

export async function uploadTemplate(file: File): Promise<ReviewTemplate> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await fetch(`${BASE_URL}/review/templates`, { method: 'POST', body: formData });
  if (!res.ok) {
    const msg = await res.text().catch(() => 'Unknown error');
    throw new Error(`${res.status}: ${msg}`);
  }
  return res.json();
}

export async function deleteTemplate(id: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/review/templates/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error(`Delete template failed: ${res.status}`);
}

// ── Review Sessions ──

export function streamReview(
  req: ReviewStartRequest,
  onChunk: (text: string) => void,
  onDone: () => void,
  onError: (err: Error) => void
): AbortController {
  const controller = new AbortController();
  fetch(`${BASE_URL}/review/start`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
    signal: controller.signal,
  })
    .then(async (res) => {
      if (!res.ok) {
        const msg = await res.text().catch(() => 'Unknown error');
        throw new Error(`${res.status}: ${msg}`);
      }
      const reader = res.body?.getReader();
      if (!reader) throw new Error('No response body');
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        // Parse SSE events
        const lines = buffer.split('\n');
        buffer = '';
        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.slice(5).trim();
            if (data) {
              try {
                const event = JSON.parse(data);
                if (event.type === 'text') {
                  onChunk(event.content);
                } else if (event.type === 'done') {
                  onDone();
                  return;
                } else if (event.type === 'error') {
                  onError(new Error(event.message || 'Review failed'));
                  return;
                }
              } catch {
                // Partial chunk — put back in buffer
                buffer = line + '\n';
              }
            }
          } else if (line.trim()) {
            buffer += line + '\n';
          }
        }
      }
      onDone();
    })
    .catch((err) => {
      if (err.name !== 'AbortError') onError(err);
    });
  return controller;
}

export async function continueReview(req: ReviewContinueRequest): Promise<string> {
  const res = await fetch(`${BASE_URL}/review/continue`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });
  if (!res.ok) throw new Error(`Continue review failed: ${res.status}`);
  return res.text();
}

export async function listReviewSessions(): Promise<ReviewSessionResponse[]> {
  return request<ReviewSessionResponse[]>('/review/sessions');
}

export async function getReviewSession(id: number): Promise<ReviewSessionResponse> {
  return request<ReviewSessionResponse>(`/review/sessions/${id}`);
}

export async function deleteReviewSession(id: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/review/sessions/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error(`Delete session failed: ${res.status}`);
}
```

- [ ] **Step 2: Commit**

```bash
git add paper-agent-frontend/src/api/review.ts
git commit -m "feat: add review API client"
```

---

### Task 12: Add 'review' to View type and Layout routing

**Files:**
- Modify: `paper-agent-frontend/src/components/layout/Layout.tsx`
- Modify: `paper-agent-frontend/src/components/layout/Sidebar.tsx`

- [ ] **Step 1: Update Layout.tsx — add 'review' to View type and route ReviewPage**

```tsx
import { useState } from 'react';
import { Sidebar } from './Sidebar';
import { ChatWindow } from '../chat/ChatWindow';
import { DiscoveryPage } from '../discovery/DiscoveryPage';
import { LibraryPage } from '../library/LibraryPage';
import { WritingPage } from '../writing/WritingPage';
import { ReviewPage } from '../review/ReviewPage';

export type View = 'chat' | 'library' | 'discovery' | 'writing' | 'review';

export function Layout() {
  const [currentView, setCurrentView] = useState<View>('chat');

  return (
    <div className="app-shell">
      <div className="app-frame">
        <Sidebar currentView={currentView} onNavigate={setCurrentView} />
        <main className="main-canvas">
          {currentView === 'chat' && <ChatWindow />}
          {currentView === 'library' && <LibraryPage onNavigate={setCurrentView} />}
          {currentView === 'writing' && <WritingPage />}
          {currentView === 'discovery' && <DiscoveryPage />}
          {currentView === 'review' && <ReviewPage />}
        </main>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Update Sidebar.tsx — add review nav item**

In `Sidebar.tsx`, change the import line (add `ClipboardCheck`):

```tsx
import { ClipboardCheck, FilePenLine, Library, MessageCircle, Search } from 'lucide-react';
```

Add to `navItems` array after 'writing':

```tsx
{ view: 'review', label: '论文评审', helper: '深度审读意见', icon: ClipboardCheck },
```

So the full array becomes:

```tsx
const navItems: Array<{ view: View; label: string; helper: string; icon: typeof MessageCircle }> = [
  { view: 'chat', label: '对话', helper: '围绕论文追问', icon: MessageCircle },
  { view: 'library', label: '文献库', helper: '管理本地论文', icon: Library },
  { view: 'writing', label: '写作', helper: '综述与引用', icon: FilePenLine },
  { view: 'review', label: '论文评审', helper: '深度审读意见', icon: ClipboardCheck },
  { view: 'discovery', label: '论文检索', helper: '外部文献链接', icon: Search },
];
```

- [ ] **Step 3: Commit**

```bash
git add paper-agent-frontend/src/components/layout/Layout.tsx paper-agent-frontend/src/components/layout/Sidebar.tsx
git commit -m "feat: add review view to layout and sidebar navigation"
```

---

### Task 13: Create ReviewPage component

**Files:**
- Create: `paper-agent-frontend/src/components/review/ReviewPage.tsx`

- [ ] **Step 1: Create ReviewPage**

```tsx
import { useState, useEffect, useRef, useCallback } from 'react';
import { Upload, FileText, Play, Loader2 } from 'lucide-react';
import { listTemplates, uploadTemplate, streamReview, listReviewSessions, continueReview } from '../../api/review';
import { getPapers } from '../../api/client';
import type { ReviewTemplate, ReviewSessionResponse, PaperListItem } from '../../types';

type Tab = 'run' | 'history';

export function ReviewPage() {
  const [tab, setTab] = useState<Tab>('run');

  // Paper selection
  const [papers, setPapers] = useState<PaperListItem[]>([]);
  const [selectedPaperId, setSelectedPaperId] = useState<number | null>(null);

  // Template selection
  const [templates, setTemplates] = useState<ReviewTemplate[]>([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null);

  // Review state
  const [reviewing, setReviewing] = useState(false);
  const [resultText, setResultText] = useState('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Follow-up
  const [followUp, setFollowUp] = useState('');
  const [followUpLoading, setFollowUpLoading] = useState(false);
  const [followUpHistory, setFollowUpHistory] = useState<Array<{ role: 'user' | 'assistant'; content: string }>>([]);

  // History
  const [sessions, setSessions] = useState<ReviewSessionResponse[]>([]);

  const resultRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  const loadPapers = useCallback(async () => {
    try { setPapers(await getPapers()); } catch { /* ignore */ }
  }, []);

  const loadTemplates = useCallback(async () => {
    try { setTemplates(await listTemplates()); } catch { /* ignore */ }
  }, []);

  const loadSessions = useCallback(async () => {
    try { setSessions(await listReviewSessions()); } catch { /* ignore */ }
  }, []);

  useEffect(() => { loadPapers(); loadTemplates(); }, [loadPapers, loadTemplates]);

  const handleUploadTemplate = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      await uploadTemplate(file);
      await loadTemplates();
      e.target.value = '';
    } catch (err: any) {
      setError(err.message);
    }
  };

  const startReview = () => {
    if (!selectedPaperId || !selectedTemplateId) return;
    setError(null);
    setResultText('');
    setFollowUpHistory([]);
    setReviewing(true);
    setCurrentSessionId(null);

    abortRef.current = streamReview(
      { paperId: selectedPaperId, templateId: selectedTemplateId },
      (text) => { setResultText((prev) => prev + text); },
      () => {
        setReviewing(false);
        loadSessions();
      },
      (err) => {
        setError(err.message);
        setReviewing(false);
      }
    );
  };

  const cancelReview = () => {
    abortRef.current?.abort();
    setReviewing(false);
  };

  const sendFollowUp = async () => {
    if (!currentSessionId && !followUp.trim()) return;
    // For simplicity, we use the latest session
    const sid = currentSessionId || 0;
    if (!sid) return;
    setFollowUpLoading(true);
    setFollowUpHistory((prev) => [...prev, { role: 'user' as const, content: followUp }]);
    const q = followUp;
    setFollowUp('');
    try {
      const answer = await continueReview({ sessionId: sid, message: q });
      setFollowUpHistory((prev) => [...prev, { role: 'assistant' as const, content: answer }]);
    } catch (err: any) {
      setError(err.message);
    }
    setFollowUpLoading(false);
  };

  // Auto-scroll review result
  useEffect(() => {
    if (resultRef.current) resultRef.current.scrollTop = resultRef.current.scrollHeight;
  }, [resultText]);

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-4 border-b border-[var(--color-border)]">
        <div>
          <h2 className="text-lg font-bold text-[var(--color-ink)]">论文评审</h2>
          <p className="text-xs text-[var(--color-ink-mute)]">按评审模板对论文进行深度审读，出具带原文引用的评审意见</p>
        </div>
        <div className="flex gap-1.5 rounded-lg bg-[var(--color-bg-mute)] p-1">
          <button onClick={() => setTab('run')} className={`px-3 py-1.5 text-xs font-semibold rounded-md transition ${tab === 'run' ? 'bg-white text-[var(--color-ink)] shadow-sm' : 'text-[var(--color-ink-mute)]'}`}>执行评审</button>
          <button onClick={() => { setTab('history'); loadSessions(); }} className={`px-3 py-1.5 text-xs font-semibold rounded-md transition ${tab === 'history' ? 'bg-white text-[var(--color-ink)] shadow-sm' : 'text-[var(--color-ink-mute)]'}`}>评审记录</button>
        </div>
      </div>

      {tab === 'run' ? (
        <div className="flex flex-1 overflow-hidden">
          {/* Left panel: config */}
          <div className="w-80 border-r border-[var(--color-border)] p-4 space-y-5 overflow-y-auto">
            {/* Paper selector */}
            <div>
              <label className="text-xs font-semibold text-[var(--color-ink-mute)] uppercase tracking-wide">选择论文</label>
              <select
                className="mt-1.5 w-full rounded-lg border border-[var(--color-border)] bg-white px-3 py-2 text-sm"
                value={selectedPaperId ?? ''}
                onChange={(e) => setSelectedPaperId(e.target.value ? Number(e.target.value) : null)}
                disabled={reviewing}
              >
                <option value="">-- 选择论文 --</option>
                {papers.filter(p => p.status === 'READY').map((p) => (
                  <option key={p.id} value={p.id}>{p.title}</option>
                ))}
              </select>
            </div>

            {/* Template selector */}
            <div>
              <label className="text-xs font-semibold text-[var(--color-ink-mute)] uppercase tracking-wide">评审模板</label>
              <select
                className="mt-1.5 w-full rounded-lg border border-[var(--color-border)] bg-white px-3 py-2 text-sm"
                value={selectedTemplateId ?? ''}
                onChange={(e) => setSelectedTemplateId(e.target.value ? Number(e.target.value) : null)}
                disabled={reviewing}
              >
                <option value="">-- 选择模板 --</option>
                {templates.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.type === 'builtin' ? '📋 ' : '📄 '}{t.name}
                  </option>
                ))}
              </select>
            </div>

            {/* Upload template */}
            <div>
              <label className="text-xs font-semibold text-[var(--color-ink-mute)] uppercase tracking-wide">上传自定义模板</label>
              <label className="mt-1.5 flex items-center justify-center gap-2 w-full rounded-lg border-2 border-dashed border-[var(--color-border)] px-3 py-3 text-xs text-[var(--color-ink-mute)] cursor-pointer hover:border-[var(--color-primary)] hover:text-[var(--color-primary)] transition">
                <Upload size={14} />
                上传 PDF / DOC / DOCX
                <input type="file" accept=".pdf,.doc,.docx" className="hidden" onChange={handleUploadTemplate} />
              </label>
            </div>

            {/* Start button */}
            <button
              onClick={reviewing ? cancelReview : startReview}
              disabled={!selectedPaperId || !selectedTemplateId}
              className={`w-full flex items-center justify-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold transition ${
                reviewing
                  ? 'bg-red-50 text-red-600 border border-red-200 hover:bg-red-100'
                  : 'bg-[var(--color-primary)] text-white hover:opacity-90 disabled:opacity-40 disabled:cursor-not-allowed'
              }`}
            >
              {reviewing ? (
                <><Loader2 size={16} className="animate-spin" /> 取消评审</>
              ) : (
                <><Play size={16} /> 开始评审</>
              )}
            </button>

            {error && (
              <div className="rounded-lg bg-red-50 border border-red-200 p-3 text-xs text-red-600">{error}</div>
            )}
          </div>

          {/* Right panel: result */}
          <div className="flex-1 flex flex-col min-w-0">
            <div ref={resultRef} className="flex-1 overflow-y-auto p-5">
              {resultText ? (
                <div className="prose prose-sm max-w-none text-sm leading-relaxed text-[var(--color-ink)] whitespace-pre-wrap">
                  {resultText}
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center h-full text-[var(--color-ink-mute)]">
                  <FileText size={40} className="mb-3 opacity-30" />
                  <p className="text-sm">选择论文和评审模板后，点击"开始评审"</p>
                </div>
              )}
              {reviewing && !resultText && (
                <div className="flex items-center justify-center gap-2 py-8 text-[var(--color-ink-mute)]">
                  <Loader2 size={18} className="animate-spin" />
                  <span className="text-sm">正在加载论文全文并生成评审...</span>
                </div>
              )}
            </div>

            {/* Follow-up chat */}
            {resultText && !reviewing && (
              <div className="border-t border-[var(--color-border)] p-4">
                {followUpHistory.map((msg, i) => (
                  <div key={i} className={`mb-3 ${msg.role === 'user' ? 'text-right' : ''}`}>
                    <div className={`inline-block max-w-[80%] rounded-xl px-4 py-2 text-sm ${
                      msg.role === 'user'
                        ? 'bg-[var(--color-primary)] text-white'
                        : 'bg-[var(--color-bg-mute)] text-[var(--color-ink)]'
                    }`}>
                      <div className="whitespace-pre-wrap">{msg.content}</div>
                    </div>
                  </div>
                ))}
                <div className="flex gap-2">
                  <input
                    className="flex-1 rounded-lg border border-[var(--color-border)] px-3 py-2 text-sm"
                    placeholder="对评审意见进行追问..."
                    value={followUp}
                    onChange={(e) => setFollowUp(e.target.value)}
                    onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendFollowUp(); } }}
                  />
                  <button
                    onClick={sendFollowUp}
                    disabled={!followUp.trim() || followUpLoading}
                    className="rounded-lg bg-[var(--color-primary)] px-4 py-2 text-sm font-semibold text-white disabled:opacity-40"
                  >
                    {followUpLoading ? <Loader2 size={16} className="animate-spin" /> : '发送'}
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      ) : (
        /* History tab */
        <div className="flex-1 overflow-y-auto p-4">
          {sessions.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full text-[var(--color-ink-mute)]">
              <p className="text-sm">暂无评审记录</p>
            </div>
          ) : (
            <div className="space-y-2">
              {sessions.map((s) => (
                <div key={s.id} className="rounded-xl border border-[var(--color-border)] bg-white p-4">
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-sm font-semibold text-[var(--color-ink)]">{s.paperTitle}</span>
                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                      s.status === 'completed' ? 'bg-green-50 text-green-600' :
                      s.status === 'error' ? 'bg-red-50 text-red-600' :
                      'bg-yellow-50 text-yellow-600'
                    }`}>{s.status}</span>
                  </div>
                  <div className="text-xs text-[var(--color-ink-mute)]">
                    模板：{s.templateName} · {new Date(s.createdAt).toLocaleString('zh-CN')}
                  </div>
                  {s.resultText && (
                    <details className="mt-2">
                      <summary className="text-xs text-[var(--color-primary)] cursor-pointer">查看评审详情</summary>
                      <div className="mt-2 p-3 rounded-lg bg-[var(--color-bg-mute)] text-xs leading-relaxed whitespace-pre-wrap max-h-96 overflow-y-auto">
                        {s.resultText}
                      </div>
                    </details>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add paper-agent-frontend/src/components/review/ReviewPage.tsx
git commit -m "feat: add ReviewPage with paper/template selection, streaming review, and follow-up chat"
```

---

### Task 14: Verify frontend builds

- [ ] **Step 1: TypeScript check**

```bash
cd paper-agent-frontend && npx tsc --noEmit 2>&1 | head -30
```

Expected: No errors related to review components.

- [ ] **Step 2: Build check**

```bash
cd paper-agent-frontend && npm run build 2>&1 | tail -20
```

Expected: Build succeeds.

- [ ] **Step 3: Commit any remaining changes**

```bash
git status
git add -A
git commit -m "chore: finalize review agent frontend integration"
```

---

### Task 15: Manual integration test

- [ ] **Step 1: Start the backend**

```powershell
$env:JAVA_HOME="C:\jdks\openlogic-openjdk-21.0.11+10-windows-x64\openlogic-openjdk-21.0.11+10-windows-x64"
$env:DASHSCOPE_API_KEY="YOUR_DASHSCOPE_API_KEY"
cd paper-agent-backend
.\mvnw.cmd spring-boot:run
```

Wait for `Started PaperAgentApplication in X seconds`.

- [ ] **Step 2: Verify template seeding**

```bash
curl -s http://localhost:8080/api/review/templates | python3 -m json.tool
```

Expected: JSON array with 2 built-in templates ("学位论文评审", "IEEE / 期刊论文评审").

- [ ] **Step 3: Start the frontend**

```powershell
cd paper-agent-frontend
npm.cmd run dev
```

- [ ] **Step 4: Test manually**

1. Open `http://localhost:5173`
2. Click "论文评审" in sidebar
3. Select a READY paper
4. Select a template
5. Click "开始评审"
6. Verify streaming review output
7. Type a follow-up question and send

- [ ] **Step 5: Shut down both servers after testing**

```bash
taskkill //F //PID <backend-pid>
# Ctrl+C for frontend
```
