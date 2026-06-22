package com.paperagent.controller;

import com.paperagent.dto.SemanticSearchRequest;
import com.paperagent.dto.SemanticSearchResponse;
import com.paperagent.service.EvidenceSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final EvidenceSearchService evidenceSearchService;

    @PostMapping("/semantic")
    public SemanticSearchResponse semantic(@Valid @RequestBody SemanticSearchRequest request) {
        return new SemanticSearchResponse(
                request.query(),
                request.paperId(),
                evidenceSearchService.search(request.query(), request.paperId(), request.safeLimit())
        );
    }
}
