package com.paperagent.service;

import com.paperagent.dto.ReferenceItem;
import com.paperagent.entity.Paper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CitationService {

    public List<ReferenceItem> formatReferences(List<Paper> papers, String citationStyle) {
        String style = normalizeStyle(citationStyle);
        AtomicInteger index = new AtomicInteger(1);
        return papers.stream()
                .map(paper -> toReferenceItem(paper, index.getAndIncrement(), style))
                .toList();
    }

    private ReferenceItem toReferenceItem(Paper paper, int index, String style) {
        String title = fallback(paper.getTitle(), "Untitled");
        String authors = fallback(paper.getAuthors(), "Unknown author");
        String year = paper.getPublishedAt() == null ? "n.d." : String.valueOf(paper.getPublishedAt().getYear());
        String locator = firstPresent(paper.getDoi(), paper.getSourceUrl(), "");
        String formatted = switch (style) {
            case "apa" -> "%s. (%s). %s.%s".formatted(authors, year, title, suffix(locator));
            case "ieee" -> "[%d] %s, \"%s,\" %s.%s".formatted(index, authors, title, year, suffix(locator));
            default -> "[%d] %s. %s[J/OL]. %s.%s".formatted(
                    index,
                    "Unknown author".equals(authors) ? "未知作者" : authors,
                    title,
                    "n.d.".equals(year) ? "日期不详" : year,
                    suffix(locator)
            );
        };

        return new ReferenceItem(
                index,
                paper.getId(),
                title,
                authors,
                year,
                paper.getDoi(),
                paper.getSourceUrl(),
                formatted.trim()
        );
    }

    private String normalizeStyle(String citationStyle) {
        if (citationStyle == null || citationStyle.isBlank()) {
            return "gbt7714";
        }
        String normalized = citationStyle.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("gb/t 7714") || normalized.equals("gbt")) {
            return "gbt7714";
        }
        if (!List.of("gbt7714", "apa", "ieee").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported citation style: " + citationStyle);
        }
        return normalized;
    }

    private String suffix(String locator) {
        return locator == null || locator.isBlank() ? "" : " " + locator.trim() + ".";
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
