package com.paperagent.dto;

import java.time.LocalDate;

public record SearchFilters(
        Long paperId,
        String tag,
        LocalDate publishedFrom,
        LocalDate publishedTo
) {
    public static SearchFilters of(Long paperId) {
        return new SearchFilters(paperId, null, null, null);
    }

    public String normalizedTag() {
        return tag == null || tag.isBlank() ? null : tag.trim();
    }
}
