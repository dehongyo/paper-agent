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
