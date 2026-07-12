package com.paperagent.dto;

import java.util.List;

public record SessionMemoryResponse(
        Long sessionId,
        String rollingSummary,
        String stateJson,
        List<MemoryEntry> longTermMemories,
        int recentMessageCount
) {
    public record MemoryEntry(
            Long id,
            String scope,
            String memoryType,
            String content,
            double importance,
            double confidence,
            String updatedAt
    ) {}
}
