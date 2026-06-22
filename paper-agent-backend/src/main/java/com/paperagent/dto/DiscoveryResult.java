package com.paperagent.dto;

import java.util.List;

public record DiscoveryResult(
        String externalId,
        String source,
        String title,
        List<String> authors,
        String year,
        String abstractText,
        String landingUrl,
        String pdfUrl,
        String doi
) {}
