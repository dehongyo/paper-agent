package com.paperagent.service;

import com.paperagent.dto.DiscoveryResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveryServiceTest {

    @Mock
    ArxivClient arxivClient;

    @Mock
    SemanticScholarClient semanticScholarClient;

    @InjectMocks
    DiscoveryService discoveryService;

    @Test
    void allSourceCombinesArxivAndSemanticScholar() {
        DiscoveryResult arxiv = new DiscoveryResult(
                "arxiv-1",
                "arXiv",
                "Paper A",
                List.of("Alice"),
                "2025",
                "Abstract A",
                "https://arxiv.org/abs/1",
                "https://arxiv.org/pdf/1",
                null
        );
        DiscoveryResult semantic = new DiscoveryResult(
                "s2-1",
                "Semantic Scholar",
                "Paper B",
                List.of("Bob"),
                "2024",
                "Abstract B",
                "https://www.semanticscholar.org/paper/1",
                null,
                "10.1234/demo"
        );
        when(arxivClient.search("rag", 5)).thenReturn(List.of(arxiv));
        when(semanticScholarClient.search("rag", 5)).thenReturn(List.of(semantic));

        List<DiscoveryResult> results = discoveryService.search("rag", "all", 5);

        assertThat(results).containsExactly(arxiv, semantic);
    }

    @Test
    void returnsPartialResultsWhenOneSourceFails() {
        DiscoveryResult semantic = new DiscoveryResult(
                "s2-1",
                "Semantic Scholar",
                "Paper B",
                List.of("Bob"),
                "2024",
                "Abstract B",
                "https://www.semanticscholar.org/paper/1",
                null,
                "10.1234/demo"
        );
        when(arxivClient.search("rag", 5)).thenThrow(new RuntimeException("arxiv down"));
        when(semanticScholarClient.search("rag", 5)).thenReturn(List.of(semantic));

        List<DiscoveryResult> results = discoveryService.search("rag", "all", 5);

        assertThat(results).containsExactly(semantic);
    }
}
