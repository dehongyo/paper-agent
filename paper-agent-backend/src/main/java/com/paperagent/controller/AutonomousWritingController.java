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
                autonomousWritingService.runPhase2_literatureSearch(sessionId);
            }
            case "literature_search" -> {
                @SuppressWarnings("unchecked")
                List<String> selectedIds = (List<String>) body.get("selectedExternalIds");
                autonomousWritingService.runPhase3_importPapers(sessionId,
                        selectedIds != null ? selectedIds : List.of());
            }
            case "import_papers" -> {
                autonomousWritingService.runPhase4_outline(sessionId);
            }
            case "outline" -> {
                String outline = body.get("outline") != null ? body.get("outline").toString() : null;
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
