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
