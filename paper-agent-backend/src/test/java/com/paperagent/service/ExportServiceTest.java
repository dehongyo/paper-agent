package com.paperagent.service;

import com.paperagent.dto.ReferenceItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportServiceTest {

    private final ExportService exportService = new ExportService();

    @Test
    void exportsMarkdownWithCoreSections() {
        ReferenceItem reference = new ReferenceItem(
                1,
                9L,
                "Paper",
                "Alice",
                "2025",
                "10.1/demo",
                null,
                "[1] Alice. Paper[J/OL]. 2025. 10.1/demo."
        );

        String markdown = exportService.toMarkdown(
                "RAG survey",
                "1. Background",
                "RAG improves grounding [1].",
                List.of(reference)
        );

        assertThat(markdown).contains("# RAG survey");
        assertThat(markdown).contains("## 大纲");
        assertThat(markdown).contains("## 正文");
        assertThat(markdown).contains("## 参考文献");
        assertThat(markdown).contains("RAG improves grounding [1].");
    }

    @Test
    void exportsLatexWithDocumentStructure() {
        ReferenceItem reference = new ReferenceItem(
                1,
                9L,
                "Paper",
                "Alice",
                "2025",
                null,
                null,
                "[1] Alice. Paper[J/OL]. 2025."
        );

        String latex = exportService.toLatex(
                "RAG & Survey",
                "1. Background",
                "RAG uses evidence [1].",
                List.of(reference)
        );

        assertThat(latex).contains("\\documentclass[UTF8]{ctexart}");
        assertThat(latex).contains("\\title{RAG \\& Survey}");
        assertThat(latex).contains("\\section*{正文}");
        assertThat(latex).contains("\\begin{enumerate}");
        assertThat(latex).contains("\\end{document}");
    }
}
