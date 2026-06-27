package com.paperagent.service;

import com.paperagent.dto.ChatSessionCreateRequest;
import com.paperagent.dto.ConversationContext;
import com.paperagent.entity.ChatMessage;
import com.paperagent.entity.ChatSession;
import com.paperagent.repository.ChatMessageRepository;
import com.paperagent.repository.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

    @Mock
    ChatSessionRepository sessionRepository;

    @Mock
    ChatMessageRepository messageRepository;

    @InjectMocks
    ChatSessionService chatSessionService;

    @Test
    void listsPaperSessionsByUpdatedTime() {
        ChatSession session = ChatSession.builder()
                .id(1L)
                .title("Paper discussion")
                .scope(ChatSession.Scope.PAPER)
                .paperId(2L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(sessionRepository.findByScopeAndPaperIdOrderByUpdatedAtDesc(ChatSession.Scope.PAPER, 2L))
                .thenReturn(List.of(session));

        assertThat(chatSessionService.listSessions("paper", 2L))
                .extracting("id")
                .containsExactly(1L);
    }

    @Test
    void listsAllPaperSessionsWhenPaperScopeHasNoSelectedPaper() {
        ChatSession first = ChatSession.builder()
                .id(1L)
                .title("Paper one discussion")
                .scope(ChatSession.Scope.PAPER)
                .paperId(2L)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now())
                .build();
        ChatSession second = ChatSession.builder()
                .id(2L)
                .title("Paper two discussion")
                .scope(ChatSession.Scope.PAPER)
                .paperId(9L)
                .createdAt(LocalDateTime.now().minusDays(2))
                .updatedAt(LocalDateTime.now().minusHours(1))
                .build();
        when(sessionRepository.findByScopeOrderByUpdatedAtDesc(ChatSession.Scope.PAPER))
                .thenReturn(List.of(first, second));

        assertThat(chatSessionService.listSessions("paper", null))
                .extracting("paperId")
                .containsExactly(2L, 9L);
    }

    @Test
    void createsPaperSessionWithDefaultTitle() {
        when(sessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(3L);
            return session;
        });

        var response = chatSessionService.createSession(new ChatSessionCreateRequest(null, "paper", 8L));

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.title()).isEqualTo("论文 #8 对话");
        assertThat(response.paperId()).isEqualTo(8L);
        assertThat(response.scope()).isEqualTo("paper");
    }

    @Test
    void returnsRecentHistoryInConversationOrder() {
        ChatMessage user = ChatMessage.builder()
                .id(10L)
                .sessionId(1L)
                .role(ChatMessage.Role.USER)
                .content("What is the method?")
                .messageOrder(1)
                .createdAt(LocalDateTime.now())
                .build();
        ChatMessage assistant = ChatMessage.builder()
                .id(11L)
                .sessionId(1L)
                .role(ChatMessage.Role.ASSISTANT)
                .content("It uses RAG.")
                .messageOrder(2)
                .createdAt(LocalDateTime.now())
                .build();
        when(messageRepository.findTop12BySessionIdOrderByMessageOrderDesc(1L))
                .thenReturn(List.of(assistant, user));

        assertThat(chatSessionService.getRecentHistory(1L))
                .extracting("content")
                .containsExactly("What is the method?", "It uses RAG.");
    }

    @Test
    void returnsConversationContextWithSummaryStateAndRecentHistory() {
        ChatSession session = ChatSession.builder()
                .id(1L)
                .title("Session")
                .scope(ChatSession.Scope.LIBRARY)
                .rollingSummary("User chose hybrid RAG as the first upgrade.")
                .stateJson("{\"current_goal\":\"add memory\"}")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        ChatMessage user = ChatMessage.builder()
                .id(10L)
                .sessionId(1L)
                .role(ChatMessage.Role.USER)
                .content("Start the first step")
                .messageOrder(3)
                .createdAt(LocalDateTime.now())
                .build();
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(messageRepository.findTop12BySessionIdOrderByMessageOrderDesc(1L))
                .thenReturn(List.of(user));

        ConversationContext context = chatSessionService.getConversationContext(1L);

        assertThat(context.rollingSummary()).isEqualTo("User chose hybrid RAG as the first upgrade.");
        assertThat(context.stateJson()).isEqualTo("{\"current_goal\":\"add memory\"}");
        assertThat(context.recentMessages()).extracting("content").containsExactly("Start the first step");
    }

    @Test
    void updatesConversationMemoryFields() {
        ChatSession session = ChatSession.builder()
                .id(1L)
                .title("Session")
                .scope(ChatSession.Scope.LIBRARY)
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chatSessionService.updateConversationMemory(
                1L,
                "The session is about memory.",
                "{\"confirmed_decisions\":[\"use rolling summary\"]}"
        );

        assertThat(session.getRollingSummary()).isEqualTo("The session is about memory.");
        assertThat(session.getStateJson()).contains("rolling summary");
    }

    @Test
    void appendsCompletedTurnToRollingSummary() {
        ChatSession session = ChatSession.builder()
                .id(1L)
                .title("Session")
                .scope(ChatSession.Scope.LIBRARY)
                .rollingSummary("Earlier summary.")
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chatSessionService.appendTurnToRollingSummary(
                1L,
                "How should memory work?",
                "Use rolling summary and state JSON."
        );

        assertThat(session.getRollingSummary()).contains("Earlier summary.");
        assertThat(session.getRollingSummary()).contains("How should memory work?");
        assertThat(session.getRollingSummary()).contains("Use rolling summary and state JSON.");
    }

    @Test
    void appendsMessageWithNextOrderAndTouchesSession() {
        ChatSession session = ChatSession.builder()
                .id(1L)
                .title("Session")
                .scope(ChatSession.Scope.LIBRARY)
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(messageRepository.findMaxMessageOrderBySessionId(1L)).thenReturn(Optional.of(4));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chatSessionService.appendMessage(1L, ChatMessage.Role.USER, "hello", null);

        assertThat(session.getUpdatedAt()).isAfter(LocalDateTime.now().minusMinutes(1));
    }
}
