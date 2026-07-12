package com.paperagent.dto;

import java.util.List;

public record ConversationContext(
        String rollingSummary,
        String stateJson,
        List<ChatHistoryMessage> recentMessages,
        List<String> longTermMemories
) {
    public ConversationContext(
            String rollingSummary,
            String stateJson,
            List<ChatHistoryMessage> recentMessages
    ) {
        this(rollingSummary, stateJson, recentMessages, List.of());
    }

    public static ConversationContext empty() {
        return new ConversationContext(null, null, List.of(), List.of());
    }

    public static ConversationContext withMemories(
            String rollingSummary,
            String stateJson,
            List<ChatHistoryMessage> recentMessages,
            List<String> longTermMemories
    ) {
        return new ConversationContext(rollingSummary, stateJson, recentMessages, longTermMemories);
    }

    public List<ChatHistoryMessage> safeRecentMessages() {
        return recentMessages == null ? List.of() : recentMessages;
    }

    public List<String> safeLongTermMemories() {
        return longTermMemories == null ? List.of() : longTermMemories;
    }

    public boolean hasMemory() {
        return hasText(rollingSummary) || hasText(stateJson) || hasMemories(longTermMemories);
    }

    public boolean hasLongTermMemories() {
        return hasMemories(longTermMemories);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean hasMemories(List<String> memories) {
        return memories != null && !memories.isEmpty();
    }
}
