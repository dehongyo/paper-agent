package com.paperagent.dto;

import java.util.List;

public record SemanticSearchResponse(
        String query,
        Long paperId,
        List<EvidenceChunk> evidence
) {}
