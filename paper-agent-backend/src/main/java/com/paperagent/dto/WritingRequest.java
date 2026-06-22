package com.paperagent.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record WritingRequest(
        @Size(max = 300, message = "Topic cannot exceed 300 characters")
        String topic,
        List<Long> paperIds,
        String scope,
        String writingType,
        String language,
        String length,
        String citationStyle,
        String outline,
        String draft
) {
    public String safeTopic() {
        return topic == null ? "" : topic.trim();
    }

    public String safeScope() {
        return scope == null || scope.isBlank() ? "library" : scope.trim();
    }

    public String safeWritingType() {
        return writingType == null || writingType.isBlank() ? "literature-review" : writingType.trim();
    }

    public String safeLanguage() {
        return language == null || language.isBlank() ? "zh" : language.trim();
    }

    public String safeLength() {
        return length == null || length.isBlank() ? "standard" : length.trim();
    }

    public String safeCitationStyle() {
        return citationStyle == null || citationStyle.isBlank() ? "gbt7714" : citationStyle.trim();
    }
}
