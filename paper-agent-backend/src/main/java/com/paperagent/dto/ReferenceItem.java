package com.paperagent.dto;

public record ReferenceItem(
        int index,
        Long paperId,
        String title,
        String authors,
        String year,
        String doi,
        String sourceUrl,
        String formatted
) {}
