package com.paperagent.controller;

import tools.jackson.databind.ObjectMapper;
import com.paperagent.dto.EvidenceChunk;
import com.paperagent.dto.SearchFilters;
import com.paperagent.dto.SemanticSearchRequest;
import com.paperagent.service.EvidenceSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.time.LocalDate;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class SearchControllerTest {

    @Autowired
    WebApplicationContext wac;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    EvidenceSearchService evidenceSearchService;

    @Test
    void semanticSearchReturnsEvidence() throws Exception {
        when(evidenceSearchService.search("rag", new SearchFilters(null, null, null, null), 8))
                .thenReturn(List.of(new EvidenceChunk(1L, 2L, "Paper", 0, "Text", 0.91)));

        mockMvc.perform(post("/api/search/semantic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SemanticSearchRequest("rag", null, null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence[0].paperTitle").value("Paper"))
                .andExpect(jsonPath("$.evidence[0].similarity").value(0.91));
    }

    @Test
    void semanticSearchPassesMetadataFilters() throws Exception {
        when(evidenceSearchService.search(
                "rag",
                new SearchFilters(
                        2L,
                        "GraphRAG",
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2025, 12, 31)
                ),
                8
        )).thenReturn(List.of(new EvidenceChunk(1L, 2L, "Paper", 0, "Text", 0.91)));

        String body = """
                {
                  "query": "rag",
                  "paperId": 2,
                  "tag": "GraphRAG",
                  "publishedFrom": "2024-01-01",
                  "publishedTo": "2025-12-31"
                }
                """;

        mockMvc.perform(post("/api/search/semantic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence[0].paperTitle").value("Paper"));
    }
}
