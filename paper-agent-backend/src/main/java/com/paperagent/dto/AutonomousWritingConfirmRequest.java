package com.paperagent.dto;

import jakarta.validation.constraints.NotNull;

public record AutonomousWritingConfirmRequest(
        @NotNull Long sessionId,
        String payload
) {}
