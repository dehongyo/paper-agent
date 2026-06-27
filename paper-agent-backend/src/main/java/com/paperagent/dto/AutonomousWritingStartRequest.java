package com.paperagent.dto;

import jakarta.validation.constraints.NotBlank;

public record AutonomousWritingStartRequest(
        @NotBlank String topic
) {}
