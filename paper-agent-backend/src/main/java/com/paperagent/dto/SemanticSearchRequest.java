package com.paperagent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SemanticSearchRequest(
        @NotBlank(message = "检索问题不能为空")
        String query,

        Long paperId,

        @Min(1)
        @Max(20)
        Integer limit
) {
    public int safeLimit() {
        return limit == null ? 8 : limit;
    }
}
