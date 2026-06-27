package com.paperagent.service;

import com.paperagent.dto.AutonomousWritingPhaseEvent;
import com.paperagent.dto.AutonomousWritingSessionResponse;
import com.paperagent.entity.AutonomousWritingSession;
import com.paperagent.repository.AutonomousWritingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutonomousWritingService {

    private final AutonomousWritingSessionRepository sessionRepository;

    public Flux<AutonomousWritingPhaseEvent> start(String topic) {
        AutonomousWritingSession session = AutonomousWritingSession.builder()
                .topic(topic)
                .currentPhase("topic_analysis")
                .status("in_progress")
                .build();
        session = sessionRepository.save(session);
        log.info("Created autonomous writing session id={} for topic={}", session.getId(), topic);
        return Flux.just(AutonomousWritingPhaseEvent.inProgress("topic_analysis"));
    }

    public void runPhase2_literatureSearch(Long sessionId) {
        AutonomousWritingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        session.setCurrentPhase("literature_search");
        sessionRepository.save(session);
        log.info("Session {} entering phase 2: literature_search", sessionId);
    }

    public void runPhase3_importPapers(Long sessionId, List<String> selectedIds) {
        AutonomousWritingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        session.setCurrentPhase("import_papers");
        sessionRepository.save(session);
        log.info("Session {} entering phase 3: import_papers with {} selected papers", sessionId, selectedIds.size());
    }

    public void runPhase4_outline(Long sessionId) {
        AutonomousWritingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        session.setCurrentPhase("outline");
        sessionRepository.save(session);
        log.info("Session {} entering phase 4: outline", sessionId);
    }

    public void runPhase5_draft(Long sessionId, String outline) {
        AutonomousWritingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        session.setCurrentPhase("draft");
        if (outline != null) {
            session.setOutline(outline);
        }
        sessionRepository.save(session);
        log.info("Session {} entering phase 5: draft", sessionId);
    }

    public void runPhase6_review(Long sessionId) {
        AutonomousWritingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        session.setCurrentPhase("review");
        sessionRepository.save(session);
        log.info("Session {} entering phase 6: review", sessionId);
    }

    public List<AutonomousWritingSessionResponse> listSessions() {
        return sessionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public AutonomousWritingSessionResponse getSession(Long id) {
        return sessionRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + id));
    }

    public void cancelSession(Long id) {
        AutonomousWritingSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + id));
        session.setStatus("cancelled");
        sessionRepository.save(session);
        log.info("Session {} cancelled", id);
    }

    public void deleteSession(Long id) {
        sessionRepository.deleteById(id);
        log.info("Session {} deleted", id);
    }

    private AutonomousWritingSessionResponse toResponse(AutonomousWritingSession s) {
        return new AutonomousWritingSessionResponse(
                s.getId(), s.getTopic(), s.getCurrentPhase(), s.getStatus(),
                s.getTopicAnalysisJson(), s.getSearchResultsJson(), s.getImportedPaperIds(),
                s.getOutline(), s.getDraft(), s.getFinalDraft(), s.getCreatedAt());
    }
}
