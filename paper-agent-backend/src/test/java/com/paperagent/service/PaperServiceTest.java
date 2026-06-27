package com.paperagent.service;

import com.paperagent.dto.PaperUpdateRequest;
import com.paperagent.entity.Paper;
import com.paperagent.repository.PaperRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Mock
    MultipartFile multipartFile;

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
    void updatePaperMetadataAllowsAddingAndEditingTags() {
        Paper paper = Paper.builder()
                .id(8L)
                .title("Paper")
                .filename("paper.pdf")
                .filePath("uploads/papers/paper.pdf")
                .tags("old-tag")
                .status(Paper.PaperStatus.READY)
                .build();
        PaperUpdateRequest request = new PaperUpdateRequest(
                "Paper",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("rag", "graph neural networks", "rag", "  ")
        );

        when(paperRepository.findById(8L)).thenReturn(Optional.of(paper));
        when(paperRepository.save(paper)).thenReturn(paper);

        Paper updated = paperService.updatePaper(8L, request);

        assertThat(updated.getTags()).isEqualTo("rag,graph neural networks");
    }

    @Test
    void generateKeywordTagsExtractsThreeOrFourTagsFromPaperContent() {
        List<String> tags = PaperService.generateKeywordTags(
                "Retrieval Augmented Generation for Graph Neural Networks",
                "This paper studies retrieval augmented generation for graph neural networks and attention.",
                """
                Retrieval augmented generation improves question answering.
                Graph neural networks model citation graphs.
                Attention mechanisms rank retrieved evidence for graph retrieval.
                """
        );

        assertThat(tags).hasSizeBetween(3, 4);
        assertThat(tags).contains("retrieval augmented generation", "graph neural networks");
    }

    @Test
    void uploadAndProcessAssignsKeywordTags() throws Exception {
        String fullText = """
                Retrieval augmented generation improves question answering.
                Graph neural networks model citation graphs.
                Attention mechanisms rank retrieved evidence for graph retrieval.
        """;
        String summary = "This paper studies retrieval augmented generation for graph neural networks and attention.";
        when(multipartFile.getOriginalFilename()).thenReturn("rag.pdf");
        when(pdfParserService.extractTitle(anyString())).thenReturn("Retrieval Augmented Generation for Graph Neural Networks");
        when(pdfParserService.getPageCount(anyString())).thenReturn(8);
        when(pdfParserService.extractFullText(anyString())).thenReturn(fullText);
        when(pdfParserService.chunkText(fullText)).thenReturn(List.of("chunk"));
        when(chatService.generateSummary(fullText)).thenReturn(summary);
        when(paperRepository.save(any(Paper.class))).thenAnswer(invocation -> {
            Paper paper = invocation.getArgument(0);
            if (paper.getId() == null) {
                paper.setId(22L);
            }
            return paper;
        });

        Paper paper = paperService.uploadAndProcess(multipartFile);

        assertThat(paper.getStatus()).isEqualTo(Paper.PaperStatus.READY);
        assertThat(PaperService.parseTags(paper.getTags()))
                .hasSizeBetween(3, 4)
                .contains("retrieval augmented generation", "graph neural networks");
        verify(embeddingService).embedAndStore(22L, List.of("chunk"));
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
