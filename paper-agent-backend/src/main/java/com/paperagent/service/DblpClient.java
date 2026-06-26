package com.paperagent.service;

import com.paperagent.dto.DiscoveryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class DblpClient {
    private final WebClient webClient = WebClient.builder()
        .baseUrl("https://dblp.org").build();

    public List<DiscoveryResult> search(String query, int limit) {
        try {
            Map<?,?> response = webClient.get()
                .uri(b -> b.path("/search/publ/api")
                    .queryParam("q", query)
                    .queryParam("h", limit)
                    .queryParam("format", "json")
                    .build())
                .retrieve().bodyToMono(Map.class).block(Duration.ofSeconds(8));

            List<DiscoveryResult> results = new ArrayList<>();
            if (response == null || !(response.get("result") instanceof Map<?,?> result)) return results;
            if (!(result.get("hits") instanceof Map<?,?> hits)) return results;
            if (!(hits.get("hit") instanceof List<?> hitList)) return results;

            for (Object item : hitList) {
                if (!(item instanceof Map<?,?> hit)) continue;
                if (!(hit.get("info") instanceof Map<?,?> info)) continue;
                String title = value(info.get("title"));
                String year = value(info.get("year"));
                String doi = value(info.get("doi"));
                String url = value(info.get("url"));
                List<String> authors = new ArrayList<>();
                if (info.get("authors") instanceof Map<?,?> authorsMap) {
                    if (authorsMap.get("author") instanceof List<?> al) {
                        for (Object a : al) {
                            if (a instanceof Map<?,?> am && am.get("text") != null) authors.add(am.get("text").toString());
                        }
                    } else if (authorsMap.get("author") instanceof Map<?,?> single) {
                        if (single.get("text") != null) authors.add(single.get("text").toString());
                    }
                }
                String id = value(hit.get("@id"));
                results.add(new DiscoveryResult(id, "DBLP", title, authors, year, null, url, null, doi));
            }
            return results;
        } catch (Exception e) {
            log.warn("DBLP search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String value(Object v) { return v == null ? null : v.toString(); }
}
