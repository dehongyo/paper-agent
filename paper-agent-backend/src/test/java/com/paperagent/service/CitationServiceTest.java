package com.paperagent.service;

import com.paperagent.dto.ReferenceItem;
import com.paperagent.entity.Paper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CitationServiceTest {

    private final CitationService citationService = new CitationService();

    @Test
    void formatsGbt7714References() {
        Paper paper = Paper.builder()
                .id(1L)
                .title("Retrieval Augmented Generation")
                .authors("Alice; Bob")
                .doi("10.1000/rag")
                .publishedAt(LocalDate.of(2025, 1, 1))
                .build();

        List<ReferenceItem> references = citationService.formatReferences(List.of(paper), "gbt7714");

        assertThat(references).hasSize(1);
        assertThat(references.get(0).formatted())
                .isEqualTo("[1] Alice; Bob. Retrieval Augmented Generation[J/OL]. 2025. 10.1000/rag.");
    }

    @Test
    void formatsApaAndIeeeReferences() {
        Paper paper = Paper.builder()
                .id(2L)
                .title("Agentic Survey")
                .authors("Carol")
                .sourceUrl("https://example.test/paper")
                .publishedAt(LocalDate.of(2024, 6, 1))
                .build();

        assertThat(citationService.formatReferences(List.of(paper), "apa").get(0).formatted())
                .isEqualTo("Carol. (2024). Agentic Survey. https://example.test/paper.");
        assertThat(citationService.formatReferences(List.of(paper), "ieee").get(0).formatted())
                .isEqualTo("[1] Carol, \"Agentic Survey,\" 2024. https://example.test/paper.");
    }

    @Test
    void fallsBackWhenMetadataIsMissing() {
        Paper paper = Paper.builder()
                .id(3L)
                .title("Untitled Evidence")
                .build();

        ReferenceItem reference = citationService.formatReferences(List.of(paper), "gb/t 7714").get(0);

        assertThat(reference.formatted()).isEqualTo("[1] 未知作者. Untitled Evidence[J/OL]. 日期不详.");
        assertThat(reference.authors()).isEqualTo("Unknown author");
        assertThat(reference.year()).isEqualTo("n.d.");
    }

    @Test
    void rejectsUnsupportedCitationStyle() {
        Paper paper = Paper.builder().id(4L).title("Paper").build();

        assertThatThrownBy(() -> citationService.formatReferences(List.of(paper), "mla"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported citation style");
    }
}
