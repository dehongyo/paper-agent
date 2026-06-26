package com.paperagent.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record BatchImportRequest(
        List<@NotBlank String> urlsOrDois
) {}
