package com.paperagent.dto;

import com.paperagent.entity.ChatSession;

import java.time.LocalDateTime;

public record ChatSessionResponse(
        Long id,
        String title,
        String scope,
        Long paperId,
        String rollingSummary,
        String stateJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ChatSessionResponse from(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getTitle(),
                session.getScope().name().toLowerCase(),
                session.getPaperId(),
                session.getRollingSummary(),
                session.getStateJson(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}
