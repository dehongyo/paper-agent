package com.paperagent.controller;

import com.paperagent.dto.ReferenceItem;
import com.paperagent.dto.SaveVersionRequest;
import com.paperagent.dto.WritingRequest;
import com.paperagent.dto.WritingResponse;
import com.paperagent.dto.WritingVersionFullResponse;
import com.paperagent.dto.WritingVersionResponse;
import com.paperagent.entity.WritingVersion;
import com.paperagent.repository.WritingVersionRepository;
import com.paperagent.service.WritingOrchestratorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/writing")
@RequiredArgsConstructor
public class WritingController {

    private final WritingOrchestratorService writingOrchestratorService;
    private final WritingVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

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

    @PostMapping("/versions")
    public WritingVersionResponse saveVersion(@RequestBody SaveVersionRequest request) {
        WritingVersion version = WritingVersion.builder()
            .topic(request.topic())
            .outline(request.outline())
            .draft(request.draft())
            .referencesJson(toJson(request.references()))
            .versionNumber(versionRepository.findAllByOrderByCreatedAtDesc().stream()
                .findFirst().map(v -> v.getVersionNumber() + 1).orElse(1))
            .build();
        version = versionRepository.save(version);
        return new WritingVersionResponse(version.getId(), version.getTopic(), version.getVersionNumber(), version.getCreatedAt());
    }

    @GetMapping("/versions")
    public List<WritingVersionResponse> listVersions() {
        return versionRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(v -> new WritingVersionResponse(v.getId(), v.getTopic(), v.getVersionNumber(), v.getCreatedAt()))
            .toList();
    }

    @GetMapping("/versions/{id}")
    public ResponseEntity<WritingVersionFullResponse> getVersion(@PathVariable Long id) {
        return versionRepository.findById(id)
            .map(v -> {
                List<ReferenceItem> refs = parseReferences(v.getReferencesJson());
                return ResponseEntity.ok(new WritingVersionFullResponse(
                    v.getId(), v.getTopic(), v.getOutline(), v.getDraft(), refs, v.getVersionNumber(), v.getCreatedAt()));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/versions/{id}")
    public void deleteVersion(@PathVariable Long id) {
        versionRepository.deleteById(id);
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); } catch (Exception e) { return "[]"; }
    }

    private List<ReferenceItem> parseReferences(String json) {
        try { return objectMapper.readValue(json, new TypeReference<List<ReferenceItem>>() {}); } catch (Exception e) { return List.of(); }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
