package com.paperagent.dto;

import jakarta.validation.constraints.NotBlank;

public record TraceableChatRequest(
        @NotBlank(message = "消息不能为空")
        String message,
        Long paperId,
        String scope,
        Long sessionId
) {
    public String safeScope() {
        return scope == null || scope.isBlank() ? "paper" : scope;
    }
}
