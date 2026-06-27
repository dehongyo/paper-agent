package com.paperagent.config;

import com.paperagent.dto.DiscoveryResult;
import com.paperagent.service.DiscoveryService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AutonomousWritingToolConfig {

    @Bean
    public ToolCallback searchPapersTool(DiscoveryService discoveryService) {
        return FunctionToolCallback.<SearchPapersInput, List<DiscoveryResult>>builder("searchPapers",
                input -> discoveryService.search(input.query(), input.source(), 10))
                .description("Search for academic papers by keyword. source can be 'arxiv', 'semantic-scholar', 'pubmed', 'dblp', or 'all'. Returns title, authors, year, abstract, source, DOI for each paper.")
                .inputType(SearchPapersInput.class)
                .build();
    }

    @Bean
    public ToolCallback finishSearchTool() {
        return FunctionToolCallback.<FinishSearchInput, FinishSearchOutput>builder("finishSearch",
                input -> {
                    String summary = input.summary() != null ? input.summary() : "Search complete";
                    int count = input.selectedExternalIds() != null ? input.selectedExternalIds().size() : 0;
                    return new FinishSearchOutput("Selected " + count + " papers. " + summary);
                })
                .description("Call this when you have found enough relevant papers to start writing. Provide a brief summary of the search results including paper titles and why they are relevant.")
                .inputType(FinishSearchInput.class)
                .build();
    }

    public record SearchPapersInput(String query, String source) {}
    public record FinishSearchInput(List<String> selectedExternalIds, String summary) {}
    public record FinishSearchOutput(String message) {}
}
