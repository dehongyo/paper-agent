package com.paperagent.service;

import com.paperagent.dto.EvidenceChunk;
import com.paperagent.dto.SearchFilters;
import com.paperagent.dto.WritingRequest;
import com.paperagent.dto.WritingResponse;
import com.paperagent.entity.Paper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WritingOrchestratorServiceTest {

    @Mock
    PaperService paperService;

    @Mock
    EvidenceSearchService evidenceSearchService;

    @Mock
    WritingService writingService;

    WritingOrchestratorService writingOrchestratorService;

    @BeforeEach
    void setUp() {
        writingOrchestratorService = new WritingOrchestratorService(
                paperService,
                evidenceSearchService,
                writingService,
                new CitationService(),
                new ExportService()
        );
    }

    @Test
    void generateOutlineRejectsEmptyTopic() {
        WritingRequest request = new WritingRequest("", List.of(), "library", "literature-review", "zh", "standard", "gbt7714", null, null);

        assertThatThrownBy(() -> writingOrchestratorService.generateOutline(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Topic cannot be empty");
    }

    @Test
    void generateDraftReturnsInsufficientEvidenceWithoutCallingWriter() {
        Paper paper = readyPaper(1L);
        WritingRequest request = new WritingRequest("RAG", List.of(), "library", "literature-review", "zh", "standard", "gbt7714", null, null);
        when(paperService.getReadyPapersForWriting(List.of())).thenReturn(List.of(paper));
        when(evidenceSearchService.search("RAG", SearchFilters.of(null), 8)).thenReturn(List.of());

        WritingResponse response = writingOrchestratorService.generateDraft(request);

        assertThat(response.draft()).contains("当前本地文献证据不足");
        assertThat(response.references()).hasSize(1);
        verifyNoInteractions(writingService);
    }

    @Test
    void generateDraftUsesEvidenceAndProducesExports() {
        Paper paper = readyPaper(2L);
        EvidenceChunk chunk = new EvidenceChunk(10L, 2L, "RAG Paper", 0, "RAG grounds answers in retrieved text.", 0.91);
        WritingRequest request = new WritingRequest("RAG", List.of(2L), "selected", "literature-review", "zh", "standard", "apa", "1. Background", null);
        when(paperService.getReadyPapersForWriting(List.of(2L))).thenReturn(List.of(paper));
        when(evidenceSearchService.search("RAG", 2L, 8)).thenReturn(List.of(chunk));
        when(writingService.generateDraft("RAG", "1. Background", "literature-review", "zh", "standard", List.of(
                new com.paperagent.dto.WritingEvidence(1, 10L, 2L, "RAG Paper", 0, "RAG grounds answers in retrieved text.", 0.91)
        ))).thenReturn("RAG uses retrieved evidence [1].");

        WritingResponse response = writingOrchestratorService.generateDraft(request);

        assertThat(response.draft()).isEqualTo("RAG uses retrieved evidence [1].");
        assertThat(response.evidence()).hasSize(1);
        assertThat(response.references().get(0).formatted()).contains("(2025)");
        assertThat(response.exportMarkdown()).contains("## 正文");
        assertThat(response.exportLatex()).contains("\\section*{正文}");
    }

    private Paper readyPaper(Long id) {
        return Paper.builder()
                .id(id)
                .title("RAG Paper")
                .authors("Alice")
                .filename("rag.pdf")
                .filePath("uploads/papers/rag.pdf")
                .sourceUrl("https://example.test/rag")
                .publishedAt(LocalDate.of(2025, 1, 1))
                .status(Paper.PaperStatus.READY)
                .build();
    }
}
