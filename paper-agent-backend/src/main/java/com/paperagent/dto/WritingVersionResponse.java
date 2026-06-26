package com.paperagent.dto;

import java.time.LocalDateTime;

public record WritingVersionResponse(
    Long id,
    String topic,
    Integer versionNumber,
    LocalDateTime createdAt
) {}
