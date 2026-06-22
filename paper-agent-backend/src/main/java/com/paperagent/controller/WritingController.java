package com.paperagent.controller;

import com.paperagent.dto.WritingRequest;
import com.paperagent.dto.WritingResponse;
import com.paperagent.service.WritingOrchestratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/writing")
@RequiredArgsConstructor
public class WritingController {

    private final WritingOrchestratorService writingOrchestratorService;

    @PostMapping("/outline")
    public WritingResponse outline(@Valid @RequestBody WritingRequest request) {
        return writingOrchestratorService.generateOutline(request);
    }

    @PostMapping("/draft")
    public WritingResponse draft(@Valid @RequestBody WritingRequest request) {
        return writingOrchestratorService.generateDraft(request);
    }

    @PostMapping("/export")
    public WritingResponse export(@Valid @RequestBody WritingRequest request) {
        return writingOrchestratorService.export(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
