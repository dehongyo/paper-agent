package com.paperagent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record SemanticSearchRequest(
        @NotBlank(message = "Search query must not be blank")
        String query,

        Long paperId,

        @Min(1)
        @Max(20)
        Integer limit,

        String tag,

        LocalDate publishedFrom,

        LocalDate publishedTo
) {
    public int safeLimit() {
        return limit == null ? 8 : limit;
    }

    public SearchFilters filters() {
        return new SearchFilters(paperId, tag, publishedFrom, publishedTo);
    }
}
