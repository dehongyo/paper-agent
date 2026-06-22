package com.paperagent.service;

import com.paperagent.dto.EvidenceChunk;
import com.paperagent.dto.TraceableChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    ChatClient chatClient;

    @Mock
    EmbeddingService embeddingService;

    @Mock
    EvidenceSearchService evidenceSearchService;

    @InjectMocks
    ChatService chatService;

    @Test
    void traceableChatReturnsInsufficientEvidenceWithoutCallingModel() {
        when(evidenceSearchService.search("What is the method?", null, 6)).thenReturn(List.of());

        TraceableChatResponse response = chatService.chatWithEvidence("What is the method?", null, "library");

        assertThat(response.answer()).contains("本地文献库中没有足够信息");
        assertThat(response.evidence()).isEmpty();
        verifyNoInteractions(chatClient);
    }

    @Test
    void traceableChatKeepsEvidenceInResponse() {
        EvidenceChunk chunk = new EvidenceChunk(1L, 2L, "Paper", 0, "The method uses RAG.", 0.9);
        when(evidenceSearchService.search("method", 2L, 6)).thenReturn(List.of(chunk));
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("It uses RAG [1].");

        TraceableChatResponse response = chatService.chatWithEvidence("method", 2L, "paper");

        assertThat(response.answer()).isEqualTo("It uses RAG [1].");
        assertThat(response.evidence()).containsExactly(chunk);
    }
}
