package com.paperagent.service;

import com.paperagent.dto.DiscoveryResult;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SemanticScholarClient {

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.semanticscholar.org")
            .build();

    public List<DiscoveryResult> search(String query, int limit) {
        Map<?, ?> response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/graph/v1/paper/search")
                        .queryParam("query", query)
                        .queryParam("limit", limit)
                        .queryParam("fields", "title,authors,year,abstract,url,externalIds,openAccessPdf")
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(8));
        return parse(response);
    }

    List<DiscoveryResult> parse(Map<?, ?> response) {
        if (response == null || !(response.get("data") instanceof List<?> data)) {
            return List.of();
        }
        List<DiscoveryResult> results = new ArrayList<>();
        for (Object item : data) {
            if (!(item instanceof Map<?, ?> paper)) {
                continue;
            }

            List<String> authors = new ArrayList<>();
            if (paper.get("authors") instanceof List<?> authorList) {
                for (Object author : authorList) {
                    if (author instanceof Map<?, ?> authorMap && authorMap.get("name") != null) {
                        authors.add(authorMap.get("name").toString());
                    }
                }
            }

            String doi = null;
            if (paper.get("externalIds") instanceof Map<?, ?> externalIds && externalIds.get("DOI") != null) {
                doi = externalIds.get("DOI").toString();
            }

            String pdfUrl = null;
            if (paper.get("openAccessPdf") instanceof Map<?, ?> pdf && pdf.get("url") != null) {
                pdfUrl = pdf.get("url").toString();
            }

            results.add(new DiscoveryResult(
                    value(paper.get("paperId")),
                    "Semantic Scholar",
                    value(paper.get("title")),
                    authors,
                    value(paper.get("year")),
                    value(paper.get("abstract")),
                    value(paper.get("url")),
                    pdfUrl,
                    doi
            ));
        }
        return results;
    }

    private static String value(Object value) {
        return value == null ? null : value.toString();
    }
}
