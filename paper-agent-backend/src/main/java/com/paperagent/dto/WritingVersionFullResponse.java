package com.paperagent.dto;

import java.time.LocalDateTime;
import java.util.List;

public record WritingVersionFullResponse(
    Long id,
    String topic,
    String outline,
    String draft,
    List<ReferenceItem> references,
    Integer versionNumber,
    LocalDateTime createdAt
) {}
