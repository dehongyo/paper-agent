package com.paperagent.dto;

import com.paperagent.entity.ChatMessage;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        String role,
        String content,
        String evidenceJson,
        Integer messageOrder,
        LocalDateTime createdAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getRole().name().toLowerCase(),
                message.getContent(),
                message.getEvidenceJson(),
                message.getMessageOrder(),
                message.getCreatedAt()
        );
    }
}
