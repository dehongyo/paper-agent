package com.paperagent.service;

import com.paperagent.dto.DiscoveryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryService {

    private final ArxivClient arxivClient;
    private final SemanticScholarClient semanticScholarClient;
    private final PubmedClient pubmedClient;
    private final DblpClient dblpClient;

    public List<DiscoveryResult> search(String query, String source, int limit) {
        String safeSource = source == null || source.isBlank() ? "all" : source;
        int safeLimit = limit <= 0 ? 10 : Math.min(limit, 20);
        List<DiscoveryResult> results = new ArrayList<>();

        if ("all".equals(safeSource) || "arxiv".equals(safeSource)) {
            try {
                results.addAll(arxivClient.search(query, safeLimit));
            } catch (Exception e) {
                log.warn("arXiv search failed: {}", e.getMessage());
            }
        }

        if ("all".equals(safeSource) || "semantic-scholar".equals(safeSource)) {
            try {
                results.addAll(semanticScholarClient.search(query, safeLimit));
            } catch (Exception e) {
                log.warn("Semantic Scholar search failed: {}", e.getMessage());
            }
        }

        if ("all".equals(safeSource) || "pubmed".equals(safeSource)) {
            try {
                results.addAll(pubmedClient.search(query, safeLimit));
            } catch (Exception e) {
                log.warn("PubMed search failed: {}", e.getMessage());
            }
        }

        if ("all".equals(safeSource) || "dblp".equals(safeSource)) {
            try {
                results.addAll(dblpClient.search(query, safeLimit));
            } catch (Exception e) {
                log.warn("DBLP search failed: {}", e.getMessage());
            }
        }

        return results.stream().limit(safeLimit).toList();
    }
}
