package com.paperagent.service;

import com.paperagent.dto.EvidenceChunk;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
                anyString(),
                ArgumentMatchers.<RowMapper<EvidenceChunk>>any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of(chunk));

        List<EvidenceChunk> result = evidenceSearchService.search("rag", null, 5);

        assertThat(result).containsExactly(chunk);
    }
}
