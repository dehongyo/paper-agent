package com.paperagent.service;

import com.paperagent.dto.EvidenceChunk;
import com.paperagent.dto.SearchFilters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvidenceSearchServiceTest {

    @Mock
    EmbeddingModel embeddingModel;

    @Mock
    JdbcTemplate jdbcTemplate;

    @InjectMocks
    EvidenceSearchService evidenceSearchService;

    @Test
    void searchesAcrossLibraryWhenPaperIdIsNull() {
        EvidenceChunk chunk = new EvidenceChunk(10L, 3L, "Paper title", 2, "Relevant text", 0.84);
        when(embeddingModel.embed("rag")).thenReturn(new float[] {0.1f, 0.2f});
        when(jdbcTemplate.query(
                ArgumentMatchers.contains("pc.embedding <=>"),
                ArgumentMatchers.<RowMapper<EvidenceChunk>>any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of(chunk));
        when(jdbcTemplate.query(
                ArgumentMatchers.contains("websearch_to_tsquery"),
                ArgumentMatchers.<RowMapper<EvidenceChunk>>any(),
                any(),
                any()
        )).thenReturn(List.of());

        List<EvidenceChunk> result = evidenceSearchService.search("rag", SearchFilters.of(null), 5);

        assertThat(result).containsExactly(chunk);
    }

    @Test
    void appliesMetadataFiltersToHybridSearchQueries() {
        SearchFilters filters = new SearchFilters(
                7L,
                "GraphRAG",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2025, 12, 31)
        );
        when(embeddingModel.embed("rag")).thenReturn(new float[] {0.1f, 0.2f});
        evidenceSearchService.search("rag", filters, 5);

        verify(jdbcTemplate).query(
                ArgumentMatchers.contains("pc.embedding <=>"),
                ArgumentMatchers.<RowMapper<EvidenceChunk>>any(),
                eq("[0.1,0.2]"),
                eq(7L),
                eq("%GraphRAG%"),
                eq(LocalDate.of(2024, 1, 1)),
                eq(LocalDate.of(2025, 12, 31)),
                eq("[0.1,0.2]"),
                eq(25)
        );
        verify(jdbcTemplate).query(
                ArgumentMatchers.contains("websearch_to_tsquery"),
                ArgumentMatchers.<RowMapper<EvidenceChunk>>any(),
                eq("rag"),
                eq(7L),
                eq("%GraphRAG%"),
                eq(LocalDate.of(2024, 1, 1)),
                eq(LocalDate.of(2025, 12, 31)),
                eq(25)
        );
    }

    @Test
    void mergesHybridCandidatesAndReranksExactKeywordMatchesFirst() {
        EvidenceChunk semantic = new EvidenceChunk(1L, 2L, "Semantic", 0, "A general retrieval augmented generation method.", 0.91);
        EvidenceChunk keyword = new EvidenceChunk(2L, 3L, "Keyword", 0, "The GraphRAG method builds a graph for retrieval.", 0.52);
        when(embeddingModel.embed("GraphRAG")).thenReturn(new float[] {0.1f, 0.2f});
        when(jdbcTemplate.query(
                ArgumentMatchers.contains("pc.embedding <=>"),
                ArgumentMatchers.<RowMapper<EvidenceChunk>>any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of(semantic));
        when(jdbcTemplate.query(
                ArgumentMatchers.contains("websearch_to_tsquery"),
                ArgumentMatchers.<RowMapper<EvidenceChunk>>any(),
                any(),
                any()
        )).thenReturn(List.of(keyword));

        List<EvidenceChunk> result = evidenceSearchService.search("GraphRAG", SearchFilters.of(null), 2);

        assertThat(result).extracting(EvidenceChunk::chunkId).containsExactly(2L, 1L);
    }

    @Test
    void expandsChildHitsWithNeighboringParentContext() {
        EvidenceChunk child = new EvidenceChunk(10L, 2L, "Paper", 4, "Child chunk mentions GraphRAG.", 0.9);
        when(embeddingModel.embed("GraphRAG")).thenReturn(new float[] {0.1f, 0.2f});
        when(jdbcTemplate.query(
                ArgumentMatchers.contains("pc.embedding <=>"),
                ArgumentMatchers.<RowMapper<EvidenceChunk>>any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of(child));
        when(jdbcTemplate.query(
                ArgumentMatchers.contains("websearch_to_tsquery"),
                ArgumentMatchers.<RowMapper<EvidenceChunk>>any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of());
        when(jdbcTemplate.queryForList(
                ArgumentMatchers.contains("chunk_index BETWEEN"),
                ArgumentMatchers.eq(String.class),
                any(),
                any(),
                any()
        )).thenReturn(List.of(
                "Previous parent context.",
                "Child chunk mentions GraphRAG.",
                "Next parent context."
        ));

        List<EvidenceChunk> result = evidenceSearchService.searchWithParentContext("GraphRAG", SearchFilters.of(2L), 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).chunkId()).isEqualTo(10L);
        assertThat(result.get(0).content())
                .contains("Previous parent context.")
                .contains("Child chunk mentions GraphRAG.")
                .contains("Next parent context.");
    }
}
