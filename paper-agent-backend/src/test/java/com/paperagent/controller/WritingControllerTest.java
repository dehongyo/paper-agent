package com.paperagent.controller;

import com.paperagent.dto.ReferenceItem;
import com.paperagent.dto.WritingResponse;
import com.paperagent.service.WritingOrchestratorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WritingController.class)
class WritingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    WritingOrchestratorService writingOrchestratorService;

    @Test
    void outlineReturnsWritingResponse() throws Exception {
        when(writingOrchestratorService.generateOutline(any()))
                .thenReturn(new WritingResponse(
                        "RAG",
                        "1. Background [1]",
                        "",
                        List.of(new ReferenceItem(1, 1L, "Paper", "Alice", "2025", null, null, "[1] Alice. Paper[J/OL]. 2025.")),
                        List.of(),
                        "# RAG",
                        "\\documentclass[UTF8]{ctexart}"
                ));

        mockMvc.perform(post("/api/writing/outline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"RAG\",\"citationStyle\":\"gbt7714\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("RAG"))
                .andExpect(jsonPath("$.outline").value("1. Background [1]"))
                .andExpect(jsonPath("$.references[0].formatted").value("[1] Alice. Paper[J/OL]. 2025."));
    }

    @Test
    void validationErrorsReturnBadRequest() throws Exception {
        when(writingOrchestratorService.generateDraft(any()))
                .thenThrow(new IllegalArgumentException("Topic cannot be empty."));

        mockMvc.perform(post("/api/writing/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Topic cannot be empty."));
    }
}
