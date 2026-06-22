package com.paperagent.controller;

import com.paperagent.dto.DiscoveryResult;
import com.paperagent.service.DiscoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DiscoveryController.class)
class DiscoveryControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    DiscoveryService discoveryService;

    @Test
    void searchReturnsExternalLinks() throws Exception {
        when(discoveryService.search("rag", "all", 10))
                .thenReturn(List.of(new DiscoveryResult(
                        "1",
                        "arXiv",
                        "RAG Paper",
                        List.of("Alice"),
                        "2025",
                        "Abstract",
                        "https://arxiv.org/abs/1",
                        "https://arxiv.org/pdf/1",
                        null
                )));

        mockMvc.perform(get("/api/discovery/search").param("query", "rag"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("RAG Paper"))
                .andExpect(jsonPath("$[0].landingUrl").value("https://arxiv.org/abs/1"));
    }
}
