package com.paperagent.dto;

import jakarta.validation.constraints.NotNull;

public record ReviewStartRequest(
        @NotNull Long paperId,
        @NotNull Long templateId
) {}
