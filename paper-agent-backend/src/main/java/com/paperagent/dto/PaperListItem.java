package com.paperagent.dto;

import com.paperagent.entity.Paper;
import com.paperagent.service.PaperService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PaperListItem(
        Long id,
        String title,
        String authors,
        String filename,
        String status,
        String summary,
        List<String> tags,
        String doi,
        String sourceUrl,
        LocalDate publishedAt,
        LocalDateTime createdAt
) {
    public static PaperListItem from(Paper paper) {
        return new PaperListItem(
                paper.getId(),
                paper.getTitle(),
                paper.getAuthors(),
                paper.getFilename(),
                paper.getStatus().name(),
                paper.getSummary(),
                PaperService.parseTags(paper.getTags()),
                paper.getDoi(),
                paper.getSourceUrl(),
                paper.getPublishedAt(),
                paper.getCreatedAt()
        );
    }
}
