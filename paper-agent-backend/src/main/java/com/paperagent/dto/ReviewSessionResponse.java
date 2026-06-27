package com.paperagent.dto;

import java.time.LocalDateTime;

public record ReviewSessionResponse(
        Long id,
        Long paperId,
        String paperTitle,
        Long templateId,
        String templateName,
        String status,
        String resultText,
        LocalDateTime createdAt
) {}
