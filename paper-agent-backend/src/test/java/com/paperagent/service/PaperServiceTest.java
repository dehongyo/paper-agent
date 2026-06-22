package com.paperagent.service;

import com.paperagent.dto.PaperUpdateRequest;
import com.paperagent.entity.Paper;
import com.paperagent.repository.PaperRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperServiceTest {

    @Mock
    PaperRepository paperRepository;

    @Mock
    PdfParserService pdfParserService;

    @Mock
    EmbeddingService embeddingService;

    @Mock
    ChatService chatService;

    @InjectMocks
    PaperService paperService;

    @Test
    void updatePaperMetadataPersistsPhase2Fields() {
        Paper paper = Paper.builder()
                .id(7L)
                .title("Old title")
                .authors("Old authors")
                .filename("paper.pdf")
                .filePath("uploads/papers/paper.pdf")
                .status(Paper.PaperStatus.READY)
                .build();
        PaperUpdateRequest request = new PaperUpdateRequest(
                "New title",
                "Alice; Bob",
                "10.1234/demo",
                "https://example.test/paper",
                LocalDate.of(2025, 5, 1),
                "Updated summary",
                "Important baseline paper",
                List.of("rag", "survey")
        );

        when(paperRepository.findById(7L)).thenReturn(Optional.of(paper));
        when(paperRepository.save(paper)).thenReturn(paper);

        Paper updated = paperService.updatePaper(7L, request);

        assertThat(updated.getTitle()).isEqualTo("New title");
        assertThat(updated.getAuthors()).isEqualTo("Alice; Bob");
        assertThat(updated.getDoi()).isEqualTo("10.1234/demo");
        assertThat(updated.getSourceUrl()).isEqualTo("https://example.test/paper");
        assertThat(updated.getPublishedAt()).isEqualTo(LocalDate.of(2025, 5, 1));
        assertThat(updated.getSummary()).isEqualTo("Updated summary");
        assertThat(updated.getNotes()).isEqualTo("Important baseline paper");
        assertThat(updated.getTags()).isEqualTo("rag,survey");
    }

    @Test
    void searchPapersWithoutFiltersUsesOrderedFindAll() {
        Paper paper = Paper.builder()
                .id(3L)
                .title("Demo")
                .filename("demo.pdf")
                .filePath("uploads/papers/demo.pdf")
                .status(Paper.PaperStatus.READY)
                .build();

        when(paperRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(paper));

        List<Paper> results = paperService.searchPapers("  ", null, null);

        assertThat(results).containsExactly(paper);
        verify(paperRepository).findAllByOrderByCreatedAtDesc();
        verify(paperRepository, never()).search(any(), any(), any());
    }
}
