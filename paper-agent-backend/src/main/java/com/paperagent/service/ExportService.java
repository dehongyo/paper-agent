package com.paperagent.service;

import com.paperagent.dto.ReferenceItem;
import com.paperagent.entity.Paper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExportService {

    public String toMarkdown(String topic, String outline, String draft, List<ReferenceItem> references) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(emptyToUntitled(topic)).append("\n\n");
        if (hasText(outline)) {
            builder.append("## 大纲\n\n").append(outline.trim()).append("\n\n");
        }
        if (hasText(draft)) {
            builder.append("## 正文\n\n").append(draft.trim()).append("\n\n");
        }
        appendMarkdownReferences(builder, references);
        return builder.toString().trim() + "\n";
    }

    public String toLatex(String topic, String outline, String draft, List<ReferenceItem> references) {
        StringBuilder builder = new StringBuilder();
        builder.append("\\documentclass[UTF8]{ctexart}\n")
                .append("\\usepackage{geometry}\n")
                .append("\\geometry{a4paper, margin=2.5cm}\n")
                .append("\\begin{document}\n")
                .append("\\title{").append(escapeLatex(emptyToUntitled(topic))).append("}\n")
                .append("\\maketitle\n\n");
        if (hasText(outline)) {
            builder.append("\\section*{大纲}\n")
                    .append(escapeLatex(outline.trim())).append("\n\n");
        }
        if (hasText(draft)) {
            builder.append("\\section*{正文}\n")
                    .append(escapeLatex(draft.trim())).append("\n\n");
        }
        if (references != null && !references.isEmpty()) {
            builder.append("\\section*{参考文献}\n")
                    .append("\\begin{enumerate}\n");
            for (ReferenceItem reference : references) {
                builder.append("\\item ").append(escapeLatex(reference.formatted())).append("\n");
            }
            builder.append("\\end{enumerate}\n");
        }
        builder.append("\\end{document}\n");
        return builder.toString();
    }

    private void appendMarkdownReferences(StringBuilder builder, List<ReferenceItem> references) {
        if (references == null || references.isEmpty()) {
            return;
        }
        builder.append("## 参考文献\n\n");
        for (ReferenceItem reference : references) {
            builder.append(reference.formatted()).append("\n\n");
        }
    }

    private String emptyToUntitled(String value) {
        return hasText(value) ? value.trim() : "Untitled";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String escapeLatex(String value) {
        return value
                .replace("\\", "\\textbackslash{}")
                .replace("&", "\\&")
                .replace("%", "\\%")
                .replace("$", "\\$")
                .replace("#", "\\#")
                .replace("_", "\\_")
                .replace("{", "\\{")
                .replace("}", "\\}");
    }

    public String toBibtex(Paper paper) {
        String author = paper.getAuthors() != null ? paper.getAuthors().replace(";", " and ") : "Unknown";
        String year = paper.getPublishedAt() != null ? String.valueOf(paper.getPublishedAt().getYear()) : "n.d.";
        String title = paper.getTitle() != null ? paper.getTitle() : "Untitled";
        String doi = paper.getDoi() != null ? paper.getDoi() : "";
        String key = (paper.getAuthors() != null ? paper.getAuthors().split("[;,]")[0].trim().toLowerCase().replaceAll("[^a-z]", "") : "unknown") + year;
        return String.format("@article{%s,\n  author = {%s},\n  title = {%s},\n  year = {%s},\n  doi = {%s}\n}", key, author, title, year, doi);
    }

    public String toEndnote(Paper paper) {
        String author = paper.getAuthors() != null ? paper.getAuthors().replace(";", "\nAU  - ") : "Unknown";
        String year = paper.getPublishedAt() != null ? String.valueOf(paper.getPublishedAt().getYear()) : "n.d.";
        String title = paper.getTitle() != null ? paper.getTitle() : "Untitled";
        String doi = paper.getDoi() != null ? paper.getDoi() : "";
        return String.format("%%0 Journal Article\nAU  - %s\nTI  - %s\nPY  - %s\nDO  - %s\n%%R  - %s\nER  - \n", author, title, year, doi, doi);
    }
}
