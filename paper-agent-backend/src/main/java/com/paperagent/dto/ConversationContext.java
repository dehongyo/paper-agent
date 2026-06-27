package com.paperagent.dto;

import java.util.List;

public record ConversationContext(
        String rollingSummary,
        String stateJson,
        List<ChatHistoryMessage> recentMessages
) {
    public static ConversationContext empty() {
        return new ConversationContext(null, null, List.of());
    }

    public List<ChatHistoryMessage> safeRecentMessages() {
        return recentMessages == null ? List.of() : recentMessages;
    }

    public boolean hasMemory() {
        return hasText(rollingSummary) || hasText(stateJson);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
