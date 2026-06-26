package com.paperagent.dto;

import jakarta.validation.constraints.NotBlank;

public record PaperImportRequest(
        @NotBlank(message = "URL 或 DOI 不能为空")
        String urlOrDoi
) {
    public String safeUrlOrDoi() {
        return urlOrDoi == null ? "" : urlOrDoi.trim();
    }
}
