package com.paperagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperagent.dto.EvidenceChunk;
import com.paperagent.dto.SemanticSearchRequest;
import com.paperagent.service.EvidenceSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    EvidenceSearchService evidenceSearchService;

    @Test
    void semanticSearchReturnsEvidence() throws Exception {
        when(evidenceSearchService.search("rag", null, 8))
                .thenReturn(List.of(new EvidenceChunk(1L, 2L, "Paper", 0, "Text", 0.91)));

        mockMvc.perform(post("/api/search/semantic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SemanticSearchRequest("rag", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence[0].paperTitle").value("Paper"))
                .andExpect(jsonPath("$.evidence[0].similarity").value(0.91));
    }
}
