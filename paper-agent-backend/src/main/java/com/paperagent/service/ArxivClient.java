package com.paperagent.service;

import com.paperagent.dto.DiscoveryResult;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class ArxivClient {

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://export.arxiv.org")
            .build();

    public List<DiscoveryResult> search(String query, int limit) {
        String xml = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/query")
                        .queryParam("search_query", "all:" + query)
                        .queryParam("start", 0)
                        .queryParam("max_results", limit)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(8));
        return parse(xml);
    }

    List<DiscoveryResult> parse(String xml) {
        if (xml == null || xml.isBlank()) {
            return List.of();
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList entries = document.getElementsByTagName("entry");
            List<DiscoveryResult> results = new ArrayList<>();
            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                String id = text(entry, "id");
                String title = normalize(text(entry, "title"));
                String summary = normalize(text(entry, "summary"));
                String published = text(entry, "published");
                List<String> authors = new ArrayList<>();
                NodeList authorNodes = entry.getElementsByTagName("author");
                for (int j = 0; j < authorNodes.getLength(); j++) {
                    authors.add(text((Element) authorNodes.item(j), "name"));
                }
                results.add(new DiscoveryResult(
                        id,
                        "arXiv",
                        title,
                        authors,
                        published != null && published.length() >= 4 ? published.substring(0, 4) : null,
                        summary,
                        id,
                        id == null ? null : id.replace("/abs/", "/pdf/"),
                        null
                ));
            }
            return results;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }

    private static String normalize(String value) {
        return value == null ? null : value.replaceAll("\\s+", " ").trim();
    }
}
