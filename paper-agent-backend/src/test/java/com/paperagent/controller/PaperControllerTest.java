package com.paperagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperagent.dto.PaperUpdateRequest;
import com.paperagent.entity.Paper;
import com.paperagent.service.PaperService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaperController.class)
class PaperControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    PaperService paperService;

    @Test
    void listPassesFiltersToService() throws Exception {
        when(paperService.searchPapers("rag", Paper.PaperStatus.READY, "survey")).thenReturn(List.of());

        mockMvc.perform(get("/api/papers")
                        .param("query", "rag")
                        .param("status", "READY")
                        .param("tag", "survey"))
                .andExpect(status().isOk());

        verify(paperService).searchPapers("rag", Paper.PaperStatus.READY, "survey");
    }

    @Test
    void updateReturnsUpdatedPaperDetail() throws Exception {
        Paper paper = Paper.builder()
                .id(7L)
                .title("New title")
                .authors("Alice")
                .filename("paper.pdf")
                .filePath("uploads/papers/paper.pdf")
                .status(Paper.PaperStatus.READY)
                .doi("10.1234/demo")
                .publishedAt(LocalDate.of(2025, 5, 1))
                .tags("rag,survey")
                .build();
        PaperUpdateRequest request = new PaperUpdateRequest(
                "New title",
                "Alice",
                "10.1234/demo",
                null,
                LocalDate.of(2025, 5, 1),
                null,
                null,
                List.of("rag", "survey")
        );

        when(paperService.updatePaper(eq(7L), any(PaperUpdateRequest.class))).thenReturn(paper);

        mockMvc.perform(patch("/api/papers/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.tags[0]").value("rag"));
    }

    @Test
    void deletePaperReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/papers/7"))
                .andExpect(status().isNoContent());

        verify(paperService).deletePaper(7L);
    }
}
