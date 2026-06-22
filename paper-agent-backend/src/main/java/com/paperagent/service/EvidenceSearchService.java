package com.paperagent.service;

import com.paperagent.dto.EvidenceChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceSearchService {

    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;

    public List<EvidenceChunk> search(String query, Long paperId, int limit) {
        try {
            String vector = vectorToString(embeddingModel.embed(query));
            String paperFilter = paperId == null ? "" : "AND pc.paper_id = ? ";
            String sql = """
                SELECT pc.id AS chunk_id,
                       pc.paper_id AS paper_id,
                       p.title AS paper_title,
                       pc.chunk_index AS chunk_index,
                       pc.content AS content,
                       1 - (pc.embedding <=> ?::vector) AS similarity
                FROM paper_chunks pc
                JOIN papers p ON p.id = pc.paper_id
                WHERE p.status = 'READY'
                %s
                ORDER BY pc.embedding <=> ?::vector
                LIMIT ?
                """.formatted(paperFilter);

            Object[] args = paperId == null
                    ? new Object[] { vector, vector, limit }
                    : new Object[] { vector, paperId, vector, limit };

            return jdbcTemplate.query(sql, (rs, rowNum) -> new EvidenceChunk(
                    rs.getLong("chunk_id"),
                    rs.getLong("paper_id"),
                    rs.getString("paper_title"),
                    rs.getInt("chunk_index"),
                    rs.getString("content"),
                    rs.getDouble("similarity")
            ), args);
        } catch (Exception e) {
            log.error("Evidence search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
