package com.paperagent.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PaperSummaryResponse(
        Long id,
        String title,
        String authors,
        String filename,
        Integer pageCount,
        String summary,
        String status,
        String doi,
        String sourceUrl,
        LocalDate publishedAt,
        String notes,
        List<String> tags,
        LocalDateTime createdAt
) {}
