package com.paperagent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 对文本块进行向量化并存入 paper_chunks 表
     */
    @Transactional
    public void embedAndStore(Long paperId, List<String> chunks) {
        if (chunks.isEmpty()) {
            log.warn("No chunks to embed for paper {}", paperId);
            return;
        }

        // 批量 embedding
        List<float[]> embeddings = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            try {
                float[] vector = embeddingModel.embed(chunks.get(i));
                embeddings.add(vector);
            } catch (Exception e) {
                log.error("Failed to embed chunk {} for paper {}: {}", i, paperId, e.getMessage());
                // 用零向量做 fallback
                embeddings.add(new float[1024]);
            }
        }

        // 批量插入（每条一个 INSERT，简单直接）
        String sql = "INSERT INTO paper_chunks (paper_id, chunk_index, content, embedding) VALUES (?, ?, ?, ?::vector)";
        for (int i = 0; i < chunks.size(); i++) {
            jdbcTemplate.update(sql, paperId, i, chunks.get(i), vectorToString(embeddings.get(i)));
        }

        log.info("Embedded and stored {} chunks for paper {}", chunks.size(), paperId);
    }

    /**
     * 删除论文的所有 chunks
     */
    @Transactional
    public void deleteByPaperId(Long paperId) {
        jdbcTemplate.update("DELETE FROM paper_chunks WHERE paper_id = ?", paperId);
    }

    /**
     * 向量相似度搜索（余弦距离）
     */
    public List<String> similaritySearch(Long paperId, String query, int topK) {
        try {
            float[] queryVector = embeddingModel.embed(query);
            String vectorStr = vectorToString(queryVector);

            String sql = """
                SELECT content, 1 - (embedding <=> ?::vector) AS similarity
                FROM paper_chunks
                WHERE paper_id = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;

            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> rs.getString("content"),
                    vectorStr, paperId, vectorStr, topK);
        } catch (Exception e) {
            log.error("Similarity search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Embed a single memory content and update the conversation_memories row.
     * Returns the vector string on success, null on failure.
     */
    public String embedMemoryContent(Long memoryId, String content) {
        try {
            float[] vector = embeddingModel.embed(content);
            String vectorStr = vectorToString(vector);
            jdbcTemplate.update(
                    "UPDATE conversation_memories SET embedding = ?::vector WHERE id = ?",
                    vectorStr, memoryId);
            return vectorStr;
        } catch (Exception e) {
            log.error("Failed to embed memory {}: {}", memoryId, e.getMessage());
            return null;
        }
    }

    /**
     * Vector similarity search over conversation_memories.
     * Returns memory IDs ranked by cosine similarity.
     */
    public List<Long> searchMemoriesByVector(String query, String scope, int limit) {
        try {
            float[] queryVector = embeddingModel.embed(query);
            String vectorStr = vectorToString(queryVector);
            String sql = """
                    SELECT id, 1 - (embedding <=> ?::vector) AS similarity
                    FROM conversation_memories
                    WHERE embedding IS NOT NULL
                      AND (? IS NULL OR scope = ?)
                    ORDER BY embedding <=> ?::vector
                    LIMIT ?
                    """;
            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> rs.getLong("id"),
                    vectorStr, scope, scope, vectorStr, limit);
        } catch (Exception e) {
            log.error("Memory vector search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 将 float[] 转为 pgvector 兼容的字符串格式 "[0.1,0.2,...]"
     */
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
