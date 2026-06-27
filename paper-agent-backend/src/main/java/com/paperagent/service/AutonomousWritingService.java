package com.paperagent.service;

import com.paperagent.dto.*;
import com.paperagent.entity.AutonomousWritingSession;
import com.paperagent.repository.AutonomousWritingSessionRepository;
import tools.jackson.databind.ObjectMapper;
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
    private final List<ToolCallback> searchTools;
    private final ObjectMapper objectMapper;

    private final Map<Long, Sinks.Many<AutonomousWritingPhaseEvent>> activeSessions = new ConcurrentHashMap<>();

    // ============================ Start ============================

    @Transactional
    public Flux<AutonomousWritingPhaseEvent> start(String topic) {
        AutonomousWritingSession session = sessionRepository.save(
                AutonomousWritingSession.builder().topic(topic).build());

        Sinks.Many<AutonomousWritingPhaseEvent> sink = Sinks.many().multicast().directBestEffort();
        activeSessions.put(session.getId(), sink);

        // Run Phase 1 immediately
        try {
            phase1_topicAnalysis(session, sink);
        } catch (Exception e) {
            log.error("Phase 1 failed for session {}", session.getId(), e);
            sink.tryEmitNext(AutonomousWritingPhaseEvent.error("topic_analysis", e.getMessage()));
        }

        return sink.asFlux()
                .doOnCancel(() -> cleanup(session.getId()))
                .doOnTerminate(() -> cleanup(session.getId()));
    }

    // ============================ Phase 1: Topic Analysis ============================

    private void phase1_topicAnalysis(AutonomousWritingSession session,
                                       Sinks.Many<AutonomousWritingPhaseEvent> sink) {
        session.setCurrentPhase("topic_analysis");
        sessionRepository.save(session);
        sink.tryEmitNext(AutonomousWritingPhaseEvent.inProgress("topic_analysis"));

        String prompt = """
                You are a research assistant helping to plan a literature review.
                Analyze the following topic and output STRICTLY valid JSON (no markdown, no extra text):

                {
                  "topic": "refined topic statement",
                  "subtopics": ["subtopic1", "subtopic2", ...],
                  "keywords": ["keyword phrase 1", "keyword phrase 2", ...],
                  "strategy": "recommended sources (arxiv, semantic-scholar, pubmed, dblp)"
                }

                Provide 3-6 subtopics and 5-8 English keywords.

                User topic: %s
                """.formatted(session.getTopic());

        String result = chatClient.prompt().user(prompt).call().content();
        String json = extractJson(result);
        session.setTopicAnalysisJson(json);
        session.setCurrentPhase("topic_analysis_completed");
        sessionRepository.save(session);

        // Parse JSON and emit
        sink.tryEmitNext(AutonomousWritingPhaseEvent.completed("topic_analysis", safeParseJson(json)));
    }

    // ============================ Phase 2: Literature Search (Tool Calling) ============================

    @Transactional
    public void runPhase2_literatureSearch(Long sessionId) {
        AutonomousWritingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        session.setCurrentPhase("literature_search");
        sessionRepository.save(session);

        Sinks.Many<AutonomousWritingPhaseEvent> sink = activeSessions.get(sessionId);
        if (sink == null) return;
        sink.tryEmitNext(AutonomousWritingPhaseEvent.inProgress("literature_search"));

        String topicAnalysis = session.getTopicAnalysisJson() != null
                ? session.getTopicAnalysisJson() : session.getTopic();

        String prompt = """
                You are searching for academic papers to write a literature review.

                Topic analysis: %s

                You have two tools available:
                - searchPapers(query, source): Search for papers. source: "arxiv", "semantic-scholar", "pubmed", "dblp", or "all"
                - finishSearch(selectedExternalIds, summary): Call this when you've found enough relevant papers (aim for 10-30). List the paper IDs you want to keep.

                Instructions:
                1. Search using the keywords from the topic analysis
                2. Review results and refine searches to explore different subtopics
                3. Call finishSearch when you've found enough papers
                4. The summary should briefly describe what papers you found and their relevance
                """.formatted(topicAnalysis);

        try {
            String result = chatClient.prompt()
                    .user(prompt)
                    .tools(searchTools.toArray(new ToolCallback[0]))
                    .call()
                    .content();

            session.setSearchResultsJson(result);
            session.setCurrentPhase("literature_search_completed");
            sessionRepository.save(session);
            sink.tryEmitNext(AutonomousWritingPhaseEvent.completed("literature_search",
                    Map.of("result", result)));
        } catch (Exception e) {
            log.error("Phase 2 search failed for session {}", sessionId, e);
            sink.tryEmitNext(AutonomousWritingPhaseEvent.error("literature_search", e.getMessage()));
        }
    }

    // ============================ Phase 3: Import Papers ============================

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
        int total = selectedExternalIds.size();

        for (int i = 0; i < total; i++) {
            String externalId = selectedExternalIds.get(i);
            try {
                var paper = paperService.importFromUrl(externalId);
                importedIds.add(paper.getId());
                sink.tryEmitNext(AutonomousWritingPhaseEvent.inProgress("import_papers",
                        Map.of("total", total, "completed", i + 1,
                                "currentTitle", paper.getTitle())));
            } catch (Exception e) {
                log.warn("Failed to import paper {}: {}", externalId, e.getMessage());
            }
        }

        String idCsv = importedIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        session.setImportedPaperIds(idCsv);
        session.setCurrentPhase("import_papers_completed");
        sessionRepository.save(session);
        sink.tryEmitNext(AutonomousWritingPhaseEvent.completed("import_papers",
                Map.of("importedCount", importedIds.size(), "paperIds", importedIds)));
    }

    // ============================ Phase 4: Outline ============================

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
        if (paperIds.isEmpty()) {
            sink.tryEmitNext(AutonomousWritingPhaseEvent.error("outline", "No papers imported"));
            return;
        }

        WritingRequest req = new WritingRequest(session.getTopic(), paperIds, "selected",
                "literature-review", "zh", "detailed", "gbt7714", null, null);
        WritingResponse resp = writingOrchestrator.generateOutline(req);

        session.setOutline(resp.outline());
        session.setCurrentPhase("outline_completed");
        sessionRepository.save(session);
        sink.tryEmitNext(AutonomousWritingPhaseEvent.completed("outline",
                Map.of("outline", resp.outline(), "references", resp.references())));
    }

    // ============================ Phase 5: Draft ============================

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
        if (paperIds.isEmpty()) {
            sink.tryEmitNext(AutonomousWritingPhaseEvent.error("draft", "No papers imported"));
            return;
        }

        WritingRequest req = new WritingRequest(session.getTopic(), paperIds, "selected",
                "literature-review", "zh", "detailed", "gbt7714",
                session.getOutline(), null);
        WritingResponse resp = writingOrchestrator.generateDraft(req);

        session.setDraft(resp.draft());
        session.setCurrentPhase("draft_completed");
        sessionRepository.save(session);
        sink.tryEmitNext(AutonomousWritingPhaseEvent.completed("draft",
                Map.of("draft", resp.draft(), "references", resp.references())));
    }

    // ============================ Phase 6: Review & Finalize ============================

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
                Check for: logical coherence, evidence support, topic coverage, factual errors.
                Output STRICTLY valid JSON (no markdown):
                {"revisions": "summary of what was changed", "finalDraft": "the revised full draft"}

                Topic: %s
                Outline: %s
                Draft: %s
                """.formatted(session.getTopic(),
                        nvl(session.getOutline()),
                        nvl(session.getDraft()));

        String result = chatClient.prompt().user(prompt).call().content();
        String json = extractJson(result);
        String finalDraft = extractJsonField(json, "finalDraft");
        String revisions = extractJsonField(json, "revisions");

        session.setFinalDraft(finalDraft != null ? finalDraft : session.getDraft());
        session.setStatus("completed");
        session.setCurrentPhase("done");
        sessionRepository.save(session);
        sink.tryEmitNext(AutonomousWritingPhaseEvent.completed("done",
                Map.of("revisions", nvl(revisions), "finalDraft", nvl(session.getFinalDraft()))));
    }

    // ============================ Session CRUD ============================

    public AutonomousWritingSessionResponse getSession(Long id) {
        return toResponse(sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + id)));
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

    // ============================ Helpers ============================

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
        if (aiOutput == null) return "{}";
        String s = aiOutput.trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) return s.substring(start, end + 1);
        return s;
    }

    private String extractJsonField(String json, String field) {
        try {
            var node = objectMapper.readTree(json);
            return node.has(field) ? node.get(field).asText() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private Object safeParseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return json;
        }
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private AutonomousWritingSessionResponse toResponse(AutonomousWritingSession s) {
        return new AutonomousWritingSessionResponse(
                s.getId(), s.getTopic(), s.getCurrentPhase(), s.getStatus(),
                s.getTopicAnalysisJson(), s.getSearchResultsJson(), s.getImportedPaperIds(),
                s.getOutline(), s.getDraft(), s.getFinalDraft(), s.getCreatedAt());
    }
}
