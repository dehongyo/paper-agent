# Autonomous Literature Review Agent — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add autonomous literature review agent — user provides topic, AI autonomously searches via Tool Calling, imports papers, then generates outline/draft/export through orchestrated pipeline, with pause-confirm between phases.

**Architecture:** New `AutonomousWritingService` orchestrates 6 phases via SSE. Phase 2 uses Spring AI 2.0.0 `FunctionToolCallback` for autonomous literature search. Phases 4-6 reuse existing `WritingOrchestratorService`. Frontend extends `WritingPage` with autonomous mode and phase progress bar.

**Tech Stack:** Spring Boot 4.1.0, Spring AI 2.0.0 (Tool Calling via FunctionToolCallback), SSE, React 18 + TypeScript + Tailwind CSS

**Environment:** All Maven commands require Java 21 and DashScope API key.

---

### Task 1: Schema + Entity + Repository

**Files:**
- Modify: `paper-agent-backend/src/main/resources/schema.sql`
- Create: `paper-agent-backend/src/main/java/com/paperagent/entity/AutonomousWritingSession.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/repository/AutonomousWritingSessionRepository.java`

- [ ] **Step 1: Add table to schema.sql**

Append to `schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS autonomous_writing_session (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(500) NOT NULL,
    current_phase VARCHAR(30) NOT NULL DEFAULT 'topic_analysis',
    topic_analysis_json TEXT,
    search_results_json TEXT,
    imported_paper_ids TEXT,
    outline TEXT,
    draft TEXT,
    final_draft TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'in_progress',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

- [ ] **Step 2: Create AutonomousWritingSession entity**

```java
package com.paperagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "autonomous_writing_session")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutonomousWritingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String topic;

    @Column(name = "current_phase", nullable = false, length = 30)
    private String currentPhase;

    @Column(name = "topic_analysis_json", columnDefinition = "TEXT")
    private String topicAnalysisJson;

    @Column(name = "search_results_json", columnDefinition = "TEXT")
    private String searchResultsJson;

    @Column(name = "imported_paper_ids", columnDefinition = "TEXT")
    private String importedPaperIds;

    @Column(columnDefinition = "TEXT")
    private String outline;

    @Column(columnDefinition = "TEXT")
    private String draft;

    @Column(name = "final_draft", columnDefinition = "TEXT")
    private String finalDraft;

    @Column(nullable = false, length = 20)
    private String status; // in_progress, completed, cancelled

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "in_progress";
        if (currentPhase == null) currentPhase = "topic_analysis";
    }
}
```

- [ ] **Step 3: Create AutonomousWritingSessionRepository**

```java
package com.paperagent.repository;

import com.paperagent.entity.AutonomousWritingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AutonomousWritingSessionRepository extends JpaRepository<AutonomousWritingSession, Long> {
    List<AutonomousWritingSession> findAllByOrderByCreatedAtDesc();
}
```

- [ ] **Step 4: Verify compilation**

```bash
export JAVA_HOME="/c/jdks/openlogic-openjdk-21.0.11+10-windows-x64/openlogic-openjdk-21.0.11+10-windows-x64"
cd paper-agent-backend && ./mvnw compile -q
```

- [ ] **Step 5: Commit**

```bash
git add paper-agent-backend/src/main/resources/schema.sql paper-agent-backend/src/main/java/com/paperagent/entity/AutonomousWritingSession.java paper-agent-backend/src/main/java/com/paperagent/repository/AutonomousWritingSessionRepository.java
git commit -m "feat: add autonomous writing session entity and schema"
```

---

### Task 2: DTOs — Requests, Events, Responses

**Files:**
- Create: `paper-agent-backend/src/main/java/com/paperagent/dto/AutonomousWritingStartRequest.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/dto/AutonomousWritingPhaseEvent.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/dto/AutonomousWritingSessionResponse.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/dto/AutonomousWritingConfirmRequest.java`

- [ ] **Step 1: Create AutonomousWritingStartRequest**

```java
package com.paperagent.dto;

import jakarta.validation.constraints.NotBlank;

public record AutonomousWritingStartRequest(
        @NotBlank String topic
) {}
```

- [ ] **Step 2: Create AutonomousWritingPhaseEvent (SSE event)**

```java
package com.paperagent.dto;

public record AutonomousWritingPhaseEvent(
        String phase,     // topic_analysis, literature_search, import_papers, outline, draft, review, done
        String status,    // in_progress, completed, error
        Object data       // phase-specific payload, can be null
) {
    public static AutonomousWritingPhaseEvent inProgress(String phase, Object data) {
        return new AutonomousWritingPhaseEvent(phase, "in_progress", data);
    }
    public static AutonomousWritingPhaseEvent completed(String phase, Object data) {
        return new AutonomousWritingPhaseEvent(phase, "completed", data);
    }
    public static AutonomousWritingPhaseEvent error(String phase, String message) {
        return new AutonomousWritingPhaseEvent(phase, "error", Map.of("message", message));
    }

    private static final java.util.Map<String, Object> EMPTY = java.util.Map.of();
    // Need jackson for serialization; use a helper record for error payload
}
```

Actually, simpler implementation without Map:

```java
package com.paperagent.dto;

public record AutonomousWritingPhaseEvent(
        String phase,
        String status,
        Object data
) {
    public static AutonomousWritingPhaseEvent inProgress(String phase) {
        return new AutonomousWritingPhaseEvent(phase, "in_progress", null);
    }
    public static AutonomousWritingPhaseEvent inProgress(String phase, Object data) {
        return new AutonomousWritingPhaseEvent(phase, "in_progress", data);
    }
    public static AutonomousWritingPhaseEvent completed(String phase, Object data) {
        return new AutonomousWritingPhaseEvent(phase, "completed", data);
    }
    public static AutonomousWritingPhaseEvent error(String phase, String message) {
        return new AutonomousWritingPhaseEvent(phase, "error", new PhaseError(message));
    }
    public record PhaseError(String message) {}
}
```

- [ ] **Step 3: Create AutonomousWritingSessionResponse**

```java
package com.paperagent.dto;

import java.time.LocalDateTime;

public record AutonomousWritingSessionResponse(
        Long id,
        String topic,
        String currentPhase,
        String status,
        String topicAnalysisJson,
        String searchResultsJson,
        String importedPaperIds,
        String outline,
        String draft,
        String finalDraft,
        LocalDateTime createdAt
) {}
```

- [ ] **Step 4: Create AutonomousWritingConfirmRequest**

```java
package com.paperagent.dto;

import jakarta.validation.constraints.NotNull;

public record AutonomousWritingConfirmRequest(
        @NotNull Long sessionId,
        String payload // optional updated data from user (edited outline, selected papers, etc.)
) {}
```

- [ ] **Step 5: Verify compilation**

```bash
cd paper-agent-backend && ./mvnw compile -q
```

- [ ] **Step 6: Commit**

```bash
git add paper-agent-backend/src/main/java/com/paperagent/dto/AutonomousWritingStartRequest.java paper-agent-backend/src/main/java/com/paperagent/dto/AutonomousWritingPhaseEvent.java paper-agent-backend/src/main/java/com/paperagent/dto/AutonomousWritingSessionResponse.java paper-agent-backend/src/main/java/com/paperagent/dto/AutonomousWritingConfirmRequest.java
git commit -m "feat: add autonomous writing DTOs"
```

---

### Task 3: Tool Configuration for Phase 2 Literature Search

**Files:**
- Create: `paper-agent-backend/src/main/java/com/paperagent/config/AutonomousWritingToolConfig.java`

- [ ] **Step 1: Create Tool Configuration**

Spring AI 2.0.0 uses `FunctionToolCallback` for Lambda-based tool definitions. Each tool is a `java.util.function.Function` wrapped in a `ToolCallback`.

```java
package com.paperagent.config;

import com.paperagent.dto.DiscoveryResult;
import com.paperagent.service.DiscoveryService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AutonomousWritingToolConfig {

    @Bean
    public ToolCallback searchPapersTool(DiscoveryService discoveryService) {
        return FunctionToolCallback.<SearchPapersInput, List<DiscoveryResult>>builder("searchPapers",
                input -> discoveryService.search(input.query, input.source, 10))
                .description("Search for academic papers by keyword. Returns title, authors, year, abstract, source, DOI, and URLs.")
                .inputType(SearchPapersInput.class)
                .build();
    }

    @Bean
    public ToolCallback finishSearchTool() {
        return FunctionToolCallback.<FinishSearchInput, FinishSearchOutput>builder("finishSearch",
                input -> new FinishSearchOutput(input.selectedExternalIds == null ? "no selection" : "selected " + input.selectedExternalIds.size() + " papers"))
                .description("Call this when you have found enough papers and are done searching. Provide the list of selected paper externalIds and a brief summary of what was found.")
                .inputType(FinishSearchInput.class)
                .build();
    }

    public record SearchPapersInput(String query, String source) {}
    public record FinishSearchInput(java.util.List<String> selectedExternalIds, String summary) {}
    public record FinishSearchOutput(String message) {}
}
```

The tricky part is that `finishSearch` is just a signal to the orchestrator that the AI is done. The actual selected papers data will be extracted from the tool call arguments by the AutonomousWritingService. The `searchPapersTool` returns `DiscoveryResult` objects; Spring AI will serialize them as JSON for the model to read.

- [ ] **Step 2: Verify compilation**

```bash
cd paper-agent-backend && ./mvnw compile -q
```

- [ ] **Step 3: Commit**

```bash
git add paper-agent-backend/src/main/java/com/paperagent/config/AutonomousWritingToolConfig.java
git commit -m "feat: add autonomous writing tool configuration with searchPapers and finishSearch"
```

---

### Task 4: AutonomousWritingService (Core Orchestrator)

**Files:**
- Create: `paper-agent-backend/src/main/java/com/paperagent/service/AutonomousWritingService.java`

This is the largest file. It orchestrates all 6 phases with SSE events.

```java
package com.paperagent.service;

import com.paperagent.dto.*;
import com.paperagent.entity.AutonomousWritingSession;
import com.paperagent.repository.AutonomousWritingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutonomousWritingService {

    private final ChatClient chatClient;
    private final DiscoveryService discoveryService;
    private final PaperService paperService;
    private final WritingOrchestratorService writingOrchestrator;
    private final AutonomousWritingSessionRepository sessionRepository;
    private final List<ToolCallback> searchTools; // injected from AutonomousWritingToolConfig

    private final Map<Long, Sinks.Many<AutonomousWritingPhaseEvent>> activeSessions = new ConcurrentHashMap<>();

    @Transactional
    public Flux<AutonomousWritingPhaseEvent> start(String topic) {
        AutonomousWritingSession session = sessionRepository.save(
                AutonomousWritingSession.builder().topic(topic).build());

        Sinks.Many<AutonomousWritingPhaseEvent> sink = Sinks.many().multicast().directBestEffort();
        activeSessions.put(session.getId(), sink);

        return sink.asFlux()
                .doOnSubscribe(s -> runPhases(session, sink))
                .doOnCancel(() -> cleanup(session.getId()))
                .doOnTerminate(() -> cleanup(session.getId()));
    }

    private void runPhases(AutonomousWritingSession session, Sinks.Many<AutonomousWritingPhaseEvent> sink) {
        try {
            // Phase execution is driven by user confirmations; see confirmPhase()
            phase1_topicAnalysis(session, sink);
        } catch (Exception e) {
            log.error("Autonomous writing session {} failed", session.getId(), e);
            sink.tryEmitNext(AutonomousWritingPhaseEvent.error(session.getCurrentPhase(), e.getMessage()));
        }
    }

    // ── Phase 1: Topic Analysis ──

    private void phase1_topicAnalysis(AutonomousWritingSession session, Sinks.Many<AutonomousWritingPhaseEvent> sink) {
        session.setCurrentPhase("topic_analysis");
        sessionRepository.save(session);
        sink.tryEmitNext(AutonomousWritingPhaseEvent.inProgress("topic_analysis"));

        String prompt = """
                You are a research assistant helping to plan a literature review.
                Analyze the following topic and break it down into:
                1. A refined topic statement
                2. 3-6 subtopics or research questions
                3. A list of 5-8 specific search keywords/phrases (in English for database search)
                4. Recommended search strategy (which sources to use: arxiv, semantic-scholar, pubmed, dblp)

                Respond in valid JSON format:
                {"topic": "...", "subtopics": [...], "keywords": [...], "strategy": "..."}

                User topic: %s
                """.formatted(session.getTopic());

        String result = chatClient.prompt().user(prompt).call().content();
        session.setTopicAnalysisJson(extractJson(result));
        session.setCurrentPhase("topic_analysis_completed");
        sessionRepository.save(session);
        sink.tryEmitNext(AutonomousWritingPhaseEvent.completed("topic_analysis", parseJson(result)));
    }

    // ── Phase 2: Literature Search (Tool Calling) ──

    @Transactional
    public void runPhase2_literatureSearch(Long sessionId) {
        AutonomousWritingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        session.setCurrentPhase("literature_search");
        sessionRepository.save(session);

        Sinks.Many<AutonomousWritingPhaseEvent> sink = activeSessions.get(sessionId);
        if (sink == null) return;
        sink.tryEmitNext(AutonomousWritingPhaseEvent.inProgress("literature_search"));

        String topicAnalysis = session.getTopicAnalysisJson() != null ? session.getTopicAnalysisJson() : session.getTopic();
        String prompt = """
                You are searching for academic papers to write a literature review.

                Topic analysis: %s

                You have two tools:
                - searchPapers(query, source): search for papers. source can be "arxiv", "semantic-scholar", "pubmed", "dblp", or "all"
                - finishSearch(selectedExternalIds, summary): call this when you have found enough relevant papers

                Instructions:
                1. Search using the keywords from the topic analysis
                2. Review results and refine searches as needed
                3. Cover all subtopics
                4. When done, call finishSearch with the externalId list of papers you recommend
                """.formatted(topicAnalysis);

        try {
            String result = chatClient.prompt()
                    .user(prompt)
                    .tools(searchTools.toArray(new ToolCallback[0]))
                    .call()
                    .content();

            log.info("Phase 2 search complete for session {}. Result: {}", sessionId, result);

            // Extract selected paper IDs from the tool call result
            // The AI's text response after tool calling contains its reasoning and selections
            session.setSearchResultsJson(result);
            session.setCurrentPhase("literature_search_completed");
            sessionRepository.save(session);
            sink.tryEmitNext(AutonomousWritingPhaseEvent.completed("literature_search",
                    Map.of("result", result, "searchComplete", true)));
        } catch (Exception e) {
            log.error("Phase 2 search failed for session {}", sessionId, e);
            sink.tryEmitNext(AutonomousWritingPhaseEvent.error("literature_search", e.getMessage()));
        }
    }

    // ── Phase 3: Import Papers ──

    @Transactional
    public void runPhase3_importPapers(Long sessionId, List<String> selectedExternalIds) {
        AutonomousWritingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        session.setCurrentPhase("import_papers");
        sessionRepository.save(session);

        Sinks.Many<AutonomousWritingPhaseEvent> sink = activeSessions.get(sessionId);
        if (sink == null) return;
        sink.tryEmitNext(AutonomousWritingPhaseEvent.inProgress("import_papers",
                Map.of("total", selectedExternalIds.size(), "completed", 0)));

        List<Long> importedIds = new ArrayList<>();
        for (int i = 0; i < selectedExternalIds.size(); i++) {
            try {
                // Import from URL/DOI
                var paper = paperService.importFromUrl(selectedExternalIds.get(i));
                importedIds.add(paper.getId());
                sink.tryEmitNext(AutonomousWritingPhaseEvent.inProgress("import_papers",
                        Map.of("total", selectedExternalIds.size(), "completed", i + 1,
                                "currentTitle", paper.getTitle())));
            } catch (Exception e) {
                log.warn("Failed to import paper {}: {}", selectedExternalIds.get(i), e.getMessage());
            }
        }

        session.setImportedPaperIds(importedIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(""));
        session.setCurrentPhase("import_papers_completed");
        sessionRepository.save(session);
        sink.tryEmitNext(AutonomousWritingPhaseEvent.completed("import_papers",
                Map.of("importedCount", importedIds.size())));
    }

    // ── Phase 4-6: Outline, Draft, Review ──

    @Transactional
    public void runPhase4_outline(Long sessionId) {
        AutonomousWritingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        session.setCurrentPhase("outline");
        sessionRepository.save(session);

        Sinks.Many<AutonomousWritingPhaseEvent> sink = activeSessions.get(sessionId);
        if (sink == null) return;
        sink.tryEmitNext(AutonomousWritingPhaseEvent.inProgress("outline"));

        List<Long> paperIds = parseImportedIds(session.getImportedPaperIds());
        WritingRequest req = new WritingRequest(session.getTopic(), paperIds, "selected",
                "literature-review", "zh", "detailed", "gbt7714", null, null);
        WritingResponse resp = writingOrchestrator.generateOutline(req);

        session.setOutline(resp.outline());
        session.setCurrentPhase("outline_completed");
        sessionRepository.save(session);
        sink.tryEmitNext(AutonomousWritingPhaseEvent.completed("outline",
                Map.of("outline", resp.outline(), "references", resp.references())));
    }

    @Transactional
    public void runPhase5_draft(Long sessionId, String outline) {
        AutonomousWritingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        session.setCurrentPhase("draft");
        if (outline != null && !outline.isBlank()) session.setOutline(outline);
        sessionRepository.save(session);

        Sinks.Many<AutonomousWritingPhaseEvent> sink = activeSessions.get(sessionId);
        if (sink == null) return;
        sink.tryEmitNext(AutonomousWritingPhaseEvent.inProgress("draft"));

        List<Long> paperIds = parseImportedIds(session.getImportedPaperIds());
        WritingRequest req = new WritingRequest(session.getTopic(), paperIds, "selected",
                "literature-review", "zh", "detailed", "gbt7714", session.getOutline(), null);
        WritingResponse resp = writingOrchestrator.generateDraft(req);

        session.setDraft(resp.draft());
        session.setCurrentPhase("draft_completed");
        sessionRepository.save(session);
        sink.tryEmitNext(AutonomousWritingPhaseEvent.completed("draft",
                Map.of("draft", resp.draft(), "references", resp.references())));
    }

    @Transactional
    public void runPhase6_review(Long sessionId) {
        AutonomousWritingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        session.setCurrentPhase("review");
        sessionRepository.save(session);

        Sinks.Many<AutonomousWritingPhaseEvent> sink = activeSessions.get(sessionId);
        if (sink == null) return;
        sink.tryEmitNext(AutonomousWritingPhaseEvent.inProgress("review"));

        String prompt = """
                You are a rigorous academic reviewer. Review the following literature review draft.

                Topic: %s
                Outline: %s
                Draft: %s

                Check for:
                1. Logical coherence and flow
                2. Whether claims are evidence-supported
                3. Coverage of the topic and subtopics
                4. Any factual errors or unsupported assertions

                Provide a revised version of the draft.
                Format your response as JSON: {"revisions": "summary of changes", "finalDraft": "revised text"}
                """.formatted(session.getTopic(),
                        session.getOutline() != null ? session.getOutline() : "",
                        session.getDraft() != null ? session.getDraft() : "");

        String result = chatClient.prompt().user(prompt).call().content();
        String finalDraft = extractJsonField(result, "finalDraft");
        String revisions = extractJsonField(result, "revisions");

        session.setFinalDraft(finalDraft);
        session.setStatus("completed");
        session.setCurrentPhase("done");
        sessionRepository.save(session);
        sink.tryEmitNext(AutonomousWritingPhaseEvent.completed("done",
                Map.of("revisions", revisions, "finalDraft", finalDraft)));
    }

    // ── Session management ──

    public AutonomousWritingSessionResponse getSession(Long id) {
        AutonomousWritingSession s = sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + id));
        return toResponse(s);
    }

    public List<AutonomousWritingSessionResponse> listSessions() {
        return sessionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public void cancelSession(Long id) {
        AutonomousWritingSession s = sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + id));
        s.setStatus("cancelled");
        sessionRepository.save(s);
        cleanup(id);
    }

    @Transactional
    public void deleteSession(Long id) {
        cleanup(id);
        sessionRepository.deleteById(id);
    }

    // ── Helpers ──

    private void cleanup(Long sessionId) {
        Sinks.Many<AutonomousWritingPhaseEvent> sink = activeSessions.remove(sessionId);
        if (sink != null) sink.tryEmitComplete();
    }

    private List<Long> parseImportedIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
    }

    private String extractJson(String aiOutput) {
        // Extract JSON block from AI output (may be surrounded by markdown fences)
        if (aiOutput == null) return "{}";
        String s = aiOutput.trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) return s.substring(start, end + 1);
        return s;
    }

    private Object parseJson(String aiOutput) {
        // Parse JSON string to Object for SSE data passing
        // Simple approach: just pass the raw JSON string; frontend parses it
        return extractJson(aiOutput);
    }

    private String extractJsonField(String aiOutput, String field) {
        // Simple extraction from JSON-like response
        String json = extractJson(aiOutput);
        try {
            var mapper = new tools.jackson.databind.ObjectMapper();
            var node = mapper.readTree(json);
            return node.has(field) ? node.get(field).asText() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private AutonomousWritingSessionResponse toResponse(AutonomousWritingSession s) {
        return new AutonomousWritingSessionResponse(
                s.getId(), s.getTopic(), s.getCurrentPhase(), s.getStatus(),
                s.getTopicAnalysisJson(), s.getSearchResultsJson(), s.getImportedPaperIds(),
                s.getOutline(), s.getDraft(), s.getFinalDraft(), s.getCreatedAt());
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd paper-agent-backend && ./mvnw compile -q
```

- [ ] **Step 3: Commit**

```bash
git add paper-agent-backend/src/main/java/com/paperagent/service/AutonomousWritingService.java
git commit -m "feat: add AutonomousWritingService with 6-phase orchestration and Tool Calling"
```

---

### Task 5: AutonomousWritingController

**Files:**
- Create: `paper-agent-backend/src/main/java/com/paperagent/controller/AutonomousWritingController.java`

- [ ] **Step 1: Create AutonomousWritingController**

```java
package com.paperagent.controller;

import com.paperagent.dto.*;
import com.paperagent.service.AutonomousWritingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/writing/autonomous")
@RequiredArgsConstructor
public class AutonomousWritingController {

    private final AutonomousWritingService autonomousWritingService;

    @PostMapping(value = "/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AutonomousWritingPhaseEvent> start(@Valid @RequestBody AutonomousWritingStartRequest request) {
        log.info("Starting autonomous writing: topic={}", request.topic());
        return autonomousWritingService.start(request.topic());
    }

    @PostMapping("/confirm")
    public ResponseEntity<Map<String, String>> confirmPhase(@RequestBody Map<String, Object> body) {
        Long sessionId = Long.valueOf(body.get("sessionId").toString());
        String phase = (String) body.get("phase");

        switch (phase) {
            case "topic_analysis" -> {
                // Proceed to literature search
                autonomousWritingService.runPhase2_literatureSearch(sessionId);
            }
            case "literature_search" -> {
                @SuppressWarnings("unchecked")
                List<String> selectedIds = (List<String>) body.get("selectedExternalIds");
                autonomousWritingService.runPhase3_importPapers(sessionId, selectedIds);
            }
            case "import_papers" -> {
                autonomousWritingService.runPhase4_outline(sessionId);
            }
            case "outline" -> {
                String outline = (String) body.getOrDefault("outline", null);
                autonomousWritingService.runPhase5_draft(sessionId, outline);
            }
            case "draft" -> {
                autonomousWritingService.runPhase6_review(sessionId);
            }
            default -> {
                return ResponseEntity.badRequest().body(Map.of("error", "Unknown phase: " + phase));
            }
        }
        return ResponseEntity.ok(Map.of("status", "confirmed", "phase", phase));
    }

    @GetMapping("/sessions")
    public List<AutonomousWritingSessionResponse> listSessions() {
        return autonomousWritingService.listSessions();
    }

    @GetMapping("/sessions/{id}")
    public AutonomousWritingSessionResponse getSession(@PathVariable Long id) {
        return autonomousWritingService.getSession(id);
    }

    @PostMapping("/sessions/{id}/cancel")
    public ResponseEntity<Map<String, String>> cancel(@PathVariable Long id) {
        autonomousWritingService.cancelSession(id);
        return ResponseEntity.ok(Map.of("status", "cancelled"));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Map<String, String>> deleteSession(@PathVariable Long id) {
        autonomousWritingService.deleteSession(id);
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

- [ ] **Step 3: Commit**

```bash
git add paper-agent-backend/src/main/java/com/paperagent/controller/AutonomousWritingController.java
git commit -m "feat: add AutonomousWritingController with SSE streaming and phase confirmation"
```

---

### Task 6: Fix compilation issues and verify backend

- [ ] **Step 1: Full compile check and fix any issues**

```bash
export JAVA_HOME="/c/jdks/openlogic-openjdk-21.0.11+10-windows-x64/openlogic-openjdk-21.0.11+10-windows-x64"
cd paper-agent-backend && ./mvnw compile 2>&1
```

- [ ] **Step 2: Run existing tests**

```bash
cd paper-agent-backend && ./mvnw test -q 2>&1 | tail -5
```

Expected: All tests pass.

- [ ] **Step 3: Commit any fixes**

---

### Task 7: Frontend — Types + API client

**Files:**
- Modify: `paper-agent-frontend/src/types/index.ts`
- Create: `paper-agent-frontend/src/api/autonomous-writing.ts`

- [ ] **Step 1: Append types to index.ts**

```typescript
// ── Autonomous Writing ──

export interface AutonomousWritingStartRequest {
  topic: string;
}

export interface AutonomousWritingPhaseEvent {
  phase: string;
  status: 'in_progress' | 'completed' | 'error';
  data: any;
}

export interface AutonomousWritingSessionResponse {
  id: number;
  topic: string;
  currentPhase: string;
  status: string;
  topicAnalysisJson: string | null;
  searchResultsJson: string | null;
  importedPaperIds: string | null;
  outline: string | null;
  draft: string | null;
  finalDraft: string | null;
  createdAt: string;
}

export interface SearchPaperResult {
  externalId: string;
  source: string;
  title: string;
  authors: string[];
  year: string | null;
  abstractText: string | null;
  landingUrl: string | null;
  pdfUrl: string | null;
  doi: string | null;
}

export interface PhaseData {
  topic?: string;
  subtopics?: string[];
  keywords?: string[];
  result?: string;
  searchComplete?: boolean;
  papers?: SearchPaperResult[];
  importedCount?: number;
  outline?: string;
  draft?: string;
  references?: any[];
  revisions?: string;
  finalDraft?: string;
  total?: number;
  completed?: number;
  currentTitle?: string;
}
```

- [ ] **Step 2: Create autonomous-writing API client**

```typescript
import type { AutonomousWritingPhaseEvent, AutonomousWritingSessionResponse } from '../types';

const BASE_URL = window.location.port === '5173'
  ? '/api'
  : 'http://localhost:5173/api';

export function streamAutonomousWriting(
  topic: string,
  onEvent: (event: AutonomousWritingPhaseEvent) => void,
  onError: (err: Error) => void
): AbortController {
  const controller = new AbortController();
  fetch(`${BASE_URL}/writing/autonomous/start`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ topic }),
    signal: controller.signal,
  })
    .then(async (res) => {
      if (!res.ok) {
        const msg = await res.text().catch(() => 'Unknown');
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

        const lines = buffer.split('\n');
        buffer = '';
        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.slice(5).trim();
            if (data) {
              try {
                const event = JSON.parse(data) as AutonomousWritingPhaseEvent;
                onEvent(event);
              } catch {
                buffer = line + '\n';
              }
            }
          } else if (line.trim()) {
            buffer += line + '\n';
          }
        }
      }
    })
    .catch((err) => {
      if (err.name !== 'AbortError') onError(err);
    });
  return controller;
}

export async function confirmPhase(
  sessionId: number,
  phase: string,
  extra?: Record<string, any>
): Promise<{ status: string }> {
  const res = await fetch(`${BASE_URL}/writing/autonomous/confirm`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId, phase, ...extra }),
  });
  if (!res.ok) throw new Error(`Confirm failed: ${res.status}`);
  return res.json();
}

export async function listAutonomousSessions(): Promise<AutonomousWritingSessionResponse[]> {
  const res = await fetch(`${BASE_URL}/writing/autonomous/sessions`);
  if (!res.ok) throw new Error(`List sessions failed: ${res.status}`);
  return res.json();
}

export async function cancelAutonomousSession(id: number): Promise<void> {
  await fetch(`${BASE_URL}/writing/autonomous/sessions/${id}/cancel`, { method: 'POST' });
}
```

- [ ] **Step 3: Commit**

```bash
git add paper-agent-frontend/src/types/index.ts paper-agent-frontend/src/api/autonomous-writing.ts
git commit -m "feat: add autonomous writing frontend types and API client"
```

---

### Task 8: Frontend — Autonomous Writing Panel in WritingPage

**Files:**
- Modify: `paper-agent-frontend/src/components/writing/WritingPage.tsx`

- [ ] **Step 1: Read current WritingPage.tsx**

Read the existing file to understand the current component structure.

- [ ] **Step 2: Add autonomous writing UI**

Add a new section within WritingPage that includes:

```tsx
// Key additions to WritingPage:

// 1. New state for autonomous mode
const [autoMode, setAutoMode] = useState(false);
const [autoSessionId, setAutoSessionId] = useState<number | null>(null);
const [autoPhase, setAutoPhase] = useState<string>('');
const [autoData, setAutoData] = useState<any>(null);
const [autoTopic, setAutoTopic] = useState('');
const abortRef = useRef<AbortController | null>(null);

// Define phases for progress bar
const PHASES = [
  { key: 'topic_analysis', label: '主题分析' },
  { key: 'literature_search', label: '文献检索' },
  { key: 'import_papers', label: '导入论文' },
  { key: 'outline', label: '大纲' },
  { key: 'draft', label: '草稿' },
  { key: 'review', label: '审校' },
  { key: 'done', label: '完成' },
];

const phaseIndex = PHASES.findIndex(p => autoData?.phase === p.key || autoPhase === p.key);

const startAutonomous = () => {
  if (!autoTopic.trim()) return;
  abortRef.current = streamAutonomousWriting(
    autoTopic,
    (event) => {
      setAutoPhase(event.phase);
      setAutoData(event.data);
      if (event.phase === 'done') {
        // Show result
      }
    },
    (err) => { /* handle error */ }
  );
};

const handleConfirm = async () => {
  if (!autoSessionId) return;
  const extra: Record<string, any> = {};
  // Add phase-specific data
  if (autoData?.phase === 'literature_search_completed' || autoPhase === 'literature_search_completed') {
    extra.selectedExternalIds = selectedPaperIds;
  }
  await confirmPhase(autoSessionId, autoPhase.replace('_completed', ''), extra);
};
```

Full component is too long to inline. The key UI additions:

1. **Mode toggle**: Tab switch between existing manual writing mode and "自主综述" mode
2. **Progress bar**: Horizontal step indicator showing 7 phases, current highlighted, completed with ✓
3. **Topic input**: When in autonomous mode and at topic_analysis phase
4. **Phase result panel**: Shows phase output:
   - Phase 1: topic, subtopics, keywords (editable)
   - Phase 2: search results list with checkboxes
   - Phase 3: import progress
   - Phase 4: outline editor (reuse existing)
   - Phase 5: draft preview (reuse existing)
   - Phase 6: revisions + final draft + export buttons
5. **Confirm button**: "确认并进入下一步" at bottom of each completed phase

- [ ] **Step 3: Verify build**

```bash
cd paper-agent-frontend && npx tsc --noEmit && npm run build
```

- [ ] **Step 4: Commit**

```bash
git add paper-agent-frontend/src/components/writing/WritingPage.tsx
git commit -m "feat: add autonomous writing mode with phase progress to WritingPage"
```

---

### Task 9: Integration test

- [ ] **Step 1: Start backend**

```powershell
$env:JAVA_HOME="C:\jdks\openlogic-openjdk-21.0.11+10-windows-x64\openlogic-openjdk-21.0.11+10-windows-x64"
$env:DASHSCOPE_API_KEY="YOUR_DASHSCOPE_API_KEY"
cd paper-agent-backend
.\mvnw.cmd spring-boot:run
```

- [ ] **Step 2: Test SSE endpoint**

```bash
curl -N -X POST http://localhost:8080/api/writing/autonomous/start \
  -H "Content-Type: application/json" \
  -d '{"topic":"deep learning for medical image segmentation"}' 2>&1 | head -20
```

Expected: SSE stream with phase events starting with `data:{"phase":"topic_analysis"...`

- [ ] **Step 3: Start frontend and test manually**

```powershell
cd paper-agent-frontend
npm.cmd run dev
```

Open http://localhost:5173, navigate to 写作, switch to 自主综述 mode, enter topic, click start.

- [ ] **Step 4: Push all commits**

```bash
git push origin main
```
