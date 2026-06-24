package com.paperagent.dto;

import java.util.List;

public record TraceableChatStreamEvent(
        String type,
        String content,
        List<EvidenceChunk> evidence
) {
    public static TraceableChatStreamEvent answer(String content) {
        return new TraceableChatStreamEvent("answer", content, List.of());
    }

    public static TraceableChatStreamEvent evidence(List<EvidenceChunk> evidence) {
        return new TraceableChatStreamEvent("evidence", "", evidence);
    }

    public static TraceableChatStreamEvent done() {
        return new TraceableChatStreamEvent("done", "", List.of());
    }
}
