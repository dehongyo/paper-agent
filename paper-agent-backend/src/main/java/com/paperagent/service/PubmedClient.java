package com.paperagent.service;

import com.paperagent.dto.DiscoveryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class PubmedClient {
    private final WebClient webClient = WebClient.builder()
        .baseUrl("https://eutils.ncbi.nlm.nih.gov").build();

    public List<DiscoveryResult> search(String query, int limit) {
        try {
            // Search for IDs
            Map<?,?> searchResult = webClient.get()
                .uri(b -> b.path("/entrez/eutils/esearch.fcgi")
                    .queryParam("db", "pubmed")
                    .queryParam("retmax", limit)
                    .queryParam("retmode", "json")
                    .queryParam("term", query)
                    .build())
                .retrieve().bodyToMono(Map.class).block(Duration.ofSeconds(8));

            if (searchResult == null || !(searchResult.get("esearchresult") instanceof Map<?,?> result)) return List.of();
            Object idList = result.get("idlist");
            if (!(idList instanceof List<?> ids) || ids.isEmpty()) return List.of();

            String idStr = String.join(",", ids.stream().map(Object::toString).toList());

            // Fetch summaries
            Map<?,?> summaryResult = webClient.get()
                .uri(b -> b.path("/entrez/eutils/esummary.fcgi")
                    .queryParam("db", "pubmed")
                    .queryParam("id", idStr)
                    .queryParam("retmode", "json")
                    .build())
                .retrieve().bodyToMono(Map.class).block(Duration.ofSeconds(8));

            return parseSummaries(summaryResult);
        } catch (Exception e) {
            log.warn("PubMed search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<DiscoveryResult> parseSummaries(Map<?,?> result) {
        List<DiscoveryResult> results = new ArrayList<>();
        if (result == null || !(result.get("result") instanceof Map<?,?> root)) return results;
        for (Object value : root.values()) {
            if (!(value instanceof Map<?,?> paper)) continue;
            if (!paper.containsKey("uid")) continue;
            String id = value(paper.get("uid"));
            String title = value(paper.get("title"));
            if (title == null) continue;
            List<String> authors = new ArrayList<>();
            if (paper.get("authors") instanceof List<?> authorList) {
                for (Object a : authorList) {
                    if (a instanceof Map<?,?> am && am.get("name") != null) authors.add(am.get("name").toString());
                }
            }
            String year = null;
            if (paper.get("pubdate") instanceof String pubdate && pubdate.length() >= 4) year = pubdate.substring(0, 4);
            String doi = null;
            if (paper.get("elocationid") instanceof String eloc && eloc.startsWith("doi:")) doi = eloc.substring(4);
            results.add(new DiscoveryResult(id, "PubMed", title, authors, year, value(paper.get("description")), null, null, doi));
        }
        return results;
    }

    private String value(Object v) { return v == null ? null : v.toString(); }
}
