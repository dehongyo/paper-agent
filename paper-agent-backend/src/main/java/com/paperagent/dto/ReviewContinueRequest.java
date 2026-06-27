package com.paperagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReviewContinueRequest(
        @NotNull Long sessionId,
        @NotBlank String message
) {}
