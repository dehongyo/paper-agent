package com.paperagent.service;

import com.paperagent.dto.EvidenceChunk;
import com.paperagent.dto.SearchFilters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceSearchService {

    private static final int PARENT_CONTEXT_RADIUS = 1;

    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;

    public List<EvidenceChunk> search(String query, Long paperId, int limit) {
        return search(query, SearchFilters.of(paperId), limit);
    }

    public List<EvidenceChunk> searchWithParentContext(String query, Long paperId, int limit) {
        return searchWithParentContext(query, SearchFilters.of(paperId), limit);
    }

    public List<EvidenceChunk> searchWithParentContext(String query, SearchFilters filters, int limit) {
        return search(query, filters, limit).stream()
                .map(this::expandParentContext)
                .toList();
    }

    public List<EvidenceChunk> search(String query, SearchFilters filters, int limit) {
        try {
            String vector = vectorToString(embeddingModel.embed(query));
            int safeLimit = Math.max(1, limit);
            int candidateLimit = Math.max(safeLimit * 5, 25);
            List<EvidenceChunk> vectorCandidates = safeList(vectorSearch(vector, filters, candidateLimit));
            List<EvidenceChunk> keywordCandidates = safeList(keywordSearch(query, filters, candidateLimit));
            return rerank(query, vectorCandidates, keywordCandidates, safeLimit);
        } catch (Exception e) {
            log.error("Evidence search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<EvidenceChunk> vectorSearch(String vector, SearchFilters filters, int candidateLimit) {
        SqlParts sqlParts = buildMetadataFilter(filters);
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
                  AND pc.embedding IS NOT NULL
                %s
                ORDER BY pc.embedding <=> ?::vector
                LIMIT ?
                """.formatted(sqlParts.whereClause());

        List<Object> args = new ArrayList<>();
        args.add(vector);
        args.addAll(sqlParts.args());
        args.add(vector);
        args.add(candidateLimit);
        return jdbcTemplate.query(sql, evidenceMapper(), args.toArray());
    }

    private List<EvidenceChunk> keywordSearch(String query, SearchFilters filters, int candidateLimit) {
        SqlParts sqlParts = buildMetadataFilter(filters);
        String sql = """
                WITH q AS (SELECT websearch_to_tsquery('simple', ?) AS query)
                SELECT pc.id AS chunk_id,
                       pc.paper_id AS paper_id,
                       p.title AS paper_title,
                       pc.chunk_index AS chunk_index,
                       pc.content AS content,
                       ts_rank_cd(to_tsvector('simple', pc.content), q.query) AS similarity
                FROM paper_chunks pc
                JOIN papers p ON p.id = pc.paper_id
                CROSS JOIN q
                WHERE p.status = 'READY'
                  AND to_tsvector('simple', pc.content) @@ q.query
                %s
                ORDER BY similarity DESC
                LIMIT ?
                """.formatted(sqlParts.whereClause());

        List<Object> args = new ArrayList<>();
        args.add(query);
        args.addAll(sqlParts.args());
        args.add(candidateLimit);
        return jdbcTemplate.query(sql, evidenceMapper(), args.toArray());
    }

    private SqlParts buildMetadataFilter(SearchFilters filters) {
        if (filters == null) {
            return new SqlParts("", List.of());
        }

        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (filters.paperId() != null) {
            where.append("  AND pc.paper_id = ?\n");
            args.add(filters.paperId());
        }
        String tag = filters.normalizedTag();
        if (tag != null) {
            where.append("  AND COALESCE(p.tags, '') ILIKE ?\n");
            args.add("%" + tag + "%");
        }
        if (filters.publishedFrom() != null) {
            where.append("  AND p.published_at >= ?\n");
            args.add(filters.publishedFrom());
        }
        if (filters.publishedTo() != null) {
            where.append("  AND p.published_at <= ?\n");
            args.add(filters.publishedTo());
        }
        return new SqlParts(where.toString(), args);
    }

    private RowMapper<EvidenceChunk> evidenceMapper() {
        return (rs, rowNum) -> new EvidenceChunk(
                rs.getLong("chunk_id"),
                rs.getLong("paper_id"),
                rs.getString("paper_title"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getDouble("similarity")
        );
    }

    private List<EvidenceChunk> rerank(
            String query,
            List<EvidenceChunk> vectorCandidates,
            List<EvidenceChunk> keywordCandidates,
            int limit
    ) {
        Map<Long, CandidateScore> byChunkId = new LinkedHashMap<>();
        for (EvidenceChunk chunk : vectorCandidates) {
            byChunkId.computeIfAbsent(chunk.chunkId(), id -> new CandidateScore(chunk))
                    .addVectorScore(chunk.similarity());
        }
        for (EvidenceChunk chunk : keywordCandidates) {
            byChunkId.computeIfAbsent(chunk.chunkId(), id -> new CandidateScore(chunk))
                    .addKeywordScore(chunk.similarity());
        }
        return byChunkId.values().stream()
                .sorted(Comparator.comparingDouble((CandidateScore item) -> item.score(query)).reversed())
                .limit(limit)
                .map(CandidateScore::chunk)
                .toList();
    }

    private List<EvidenceChunk> safeList(List<EvidenceChunk> chunks) {
        return chunks == null ? List.of() : chunks;
    }

    private EvidenceChunk expandParentContext(EvidenceChunk child) {
        if (child == null || child.paperId() == null || child.chunkIndex() == null) {
            return child;
        }
        int start = Math.max(0, child.chunkIndex() - PARENT_CONTEXT_RADIUS);
        int end = child.chunkIndex() + PARENT_CONTEXT_RADIUS;
        try {
            List<String> parentChunks = jdbcTemplate.queryForList("""
                    SELECT content
                    FROM paper_chunks
                    WHERE paper_id = ?
                      AND chunk_index BETWEEN ? AND ?
                    ORDER BY chunk_index ASC
                    """, String.class, child.paperId(), start, end);
            if (parentChunks == null || parentChunks.isEmpty()) {
                return child;
            }
            return new EvidenceChunk(
                    child.chunkId(),
                    child.paperId(),
                    child.paperTitle(),
                    child.chunkIndex(),
                    String.join("\n\n", parentChunks),
                    child.similarity()
            );
        } catch (Exception e) {
            log.warn("Failed to expand parent context for chunk {}: {}", child.chunkId(), e.getMessage());
            return child;
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

    private record SqlParts(String whereClause, List<Object> args) {}

    private static final class CandidateScore {
        private final EvidenceChunk chunk;
        private double vectorScore;
        private double keywordScore;
        private boolean vectorHit;
        private boolean keywordHit;

        private CandidateScore(EvidenceChunk chunk) {
            this.chunk = chunk;
        }

        private EvidenceChunk chunk() {
            return chunk;
        }

        private void addVectorScore(Double score) {
            vectorHit = true;
            vectorScore = Math.max(vectorScore, safeScore(score));
        }

        private void addKeywordScore(Double score) {
            keywordHit = true;
            keywordScore = Math.max(keywordScore, safeScore(score));
        }

        private double score(String query) {
            double lexicalScore = lexicalScore(query, chunk.paperTitle() + " " + chunk.content());
            double retrievalScore = Math.max(vectorScore, keywordScore);
            double hybridBoost = vectorHit && keywordHit ? 0.10 : 0.0;
            double keywordBoost = keywordHit ? 0.15 : 0.0;
            return retrievalScore * 0.65 + lexicalScore * 0.35 + hybridBoost + keywordBoost;
        }

        private static double safeScore(Double score) {
            if (score == null || score.isNaN() || score.isInfinite()) {
                return 0.0;
            }
            return Math.max(0.0, score);
        }

        private static double lexicalScore(String query, String content) {
            if (query == null || query.isBlank() || content == null || content.isBlank()) {
                return 0.0;
            }
            String normalizedQuery = query.toLowerCase(Locale.ROOT).trim();
            String normalizedContent = content.toLowerCase(Locale.ROOT);
            double score = normalizedContent.contains(normalizedQuery) ? 1.0 : 0.0;
            String[] terms = normalizedQuery.split("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
            int usefulTerms = 0;
            int matchedTerms = 0;
            for (String term : terms) {
                if (term.length() < 2) {
                    continue;
                }
                usefulTerms++;
                if (normalizedContent.contains(term)) {
                    matchedTerms++;
                }
            }
            if (usefulTerms > 0) {
                score += (double) matchedTerms / usefulTerms;
            }
            return score;
        }
    }
}
