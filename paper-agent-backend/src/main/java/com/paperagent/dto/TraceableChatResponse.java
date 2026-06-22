package com.paperagent.dto;

import java.util.List;

public record TraceableChatResponse(
        String answer,
        List<EvidenceChunk> evidence
) {}
