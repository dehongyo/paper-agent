package com.paperagent.dto;

import java.time.LocalDateTime;

public record TemplateResponse(
        Long id,
        String name,
        String type,
        String sourceFilename,
        int contentLength,
        LocalDateTime createdAt
) {}
