package com.paperagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record PaperUpdateRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 500, message = "标题不能超过 500 个字符")
        String title,

        @Size(max = 1000, message = "作者不能超过 1000 个字符")
        String authors,

        @Size(max = 255, message = "DOI 不能超过 255 个字符")
        String doi,

        @Size(max = 1000, message = "来源链接不能超过 1000 个字符")
        String sourceUrl,

        LocalDate publishedAt,
        String summary,
        String notes,
        List<String> tags
) {}
