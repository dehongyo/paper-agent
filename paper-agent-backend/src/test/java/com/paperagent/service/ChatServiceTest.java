package com.paperagent.service;

import com.paperagent.dto.EvidenceChunk;
import com.paperagent.dto.ChatHistoryMessage;
import com.paperagent.dto.TraceableChatResponse;
import com.paperagent.dto.TraceableChatStreamEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

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

    @Test
    void traceableChatStreamReturnsInsufficientEvidenceWithoutCallingModel() {
        when(evidenceSearchService.search("What is the method?", null, 6)).thenReturn(List.of());

        List<TraceableChatStreamEvent> events = chatService
                .chatWithEvidenceStream("What is the method?", null, "library")
                .collectList()
                .block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(TraceableChatStreamEvent::type).containsExactly("answer", "done");
        assertThat(events.get(0).content()).isNotBlank();
        assertThat(events.get(0).evidence()).isEmpty();
        verifyNoInteractions(chatClient);
    }

    @Test
    void traceableChatStreamEmitsAnswerChunksThenEvidence() {
        EvidenceChunk chunk = new EvidenceChunk(1L, 2L, "Paper", 0, "The method uses RAG.", 0.9);
        when(evidenceSearchService.search("method", 2L, 6)).thenReturn(List.of(chunk));
        when(chatClient.prompt().user(anyString()).stream().content()).thenReturn(Flux.just("It ", "uses RAG [1]."));

        List<TraceableChatStreamEvent> events = chatService
                .chatWithEvidenceStream("method", 2L, "paper")
                .collectList()
                .block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(TraceableChatStreamEvent::type)
                .containsExactly("answer", "answer", "evidence", "done");
        assertThat(events.get(0).content()).isEqualTo("It ");
        assertThat(events.get(1).content()).isEqualTo("uses RAG [1].");
        assertThat(events.get(2).evidence()).containsExactly(chunk);
    }

    @Test
    void traceableChatStreamIncludesHistoryInPrompt() {
        EvidenceChunk chunk = new EvidenceChunk(1L, 2L, "Paper", 0, "The method uses RAG.", 0.9);
        when(evidenceSearchService.search("method", 2L, 6)).thenReturn(List.of(chunk));
        when(chatClient.prompt().user(org.mockito.ArgumentMatchers.contains("上一轮回答")).stream().content())
                .thenReturn(Flux.just("answer"));

        List<TraceableChatStreamEvent> events = chatService
                .chatWithEvidenceStream(
                        "method",
                        2L,
                        "paper",
                        List.of(new ChatHistoryMessage("assistant", "上一轮回答"))
                )
                .collectList()
                .block();

        assertThat(events).isNotNull();
        assertThat(events.get(0).content()).isEqualTo("answer");
    }
}
