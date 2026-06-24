package com.paperagent.dto;

public record ChatSessionCreateRequest(
        String title,
        String scope,
        Long paperId
) {
    public String safeScope() {
        return scope == null || scope.isBlank() ? "paper" : scope;
    }
}
