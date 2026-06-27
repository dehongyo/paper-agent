package com.paperagent.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record TraceableChatRequest(
        @NotBlank(message = "Message must not be blank")
        String message,
        Long paperId,
        String scope,
        Long sessionId,
        String tag,
        LocalDate publishedFrom,
        LocalDate publishedTo
) {
    public String safeScope() {
        return scope == null || scope.isBlank() ? "paper" : scope;
    }

    public SearchFilters filters() {
        Long filteredPaperId = "library".equals(safeScope()) ? null : paperId;
        return new SearchFilters(filteredPaperId, tag, publishedFrom, publishedTo);
    }
}
