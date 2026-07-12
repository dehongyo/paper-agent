package com.paperagent.service;

import com.paperagent.dto.ConversationContext;
import com.paperagent.entity.ConversationMemory;
import com.paperagent.repository.ConversationMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationMemoryServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    ChatClient chatClient;

    @Mock
    ChatSessionService chatSessionService;

    @Mock
    ConversationMemoryRepository memoryRepository;

    @Mock
    EmbeddingService embeddingService;

    ConversationMemoryService memoryService;

    @BeforeEach
    void setUp() {
        memoryService = new ConversationMemoryService(
                chatClient,
                chatSessionService,
                memoryRepository,
                embeddingService,
                new ObjectMapper()
        );
    }

    @Test
    void updatesSessionSummaryAndStateFromModelJson() {
        when(chatSessionService.getConversationContext(1L)).thenReturn(new ConversationContext(
                "Earlier summary",
                "{\"schemaVersion\":1}",
                List.of()
        ));
        when(chatClient.prompt().user(contains("STRICTLY valid JSON")).call().content()).thenReturn("""
                {
                  "rollingSummary": "User is adding enterprise memory to the RAG chat.",
                  "state": {
                    "schemaVersion": 1,
                    "currentGoal": "add conversation memory",
                    "confirmedDecisions": ["use rolling summary"],
                    "openQuestions": [],
                    "activePaperIds": [],
                    "userPreferences": []
                  },
                  "memoryCandidates": []
                }
                """);

        memoryService.updateAfterTurn(1L, "Start memory", "Use summary and state.");

        verify(chatSessionService).updateConversationMemory(
                1L,
                "User is adding enterprise memory to the RAG chat.",
                "{\"schemaVersion\":1,\"currentGoal\":\"add conversation memory\",\"confirmedDecisions\":[\"use rolling summary\"],\"openQuestions\":[],\"activePaperIds\":[],\"userPreferences\":[]}"
        );
    }

    @Test
    void writesOnlyHighValueMemoryCandidates() {
        when(chatSessionService.getConversationContext(2L)).thenReturn(ConversationContext.empty());
        when(chatClient.prompt().user(contains("memoryCandidates")).call().content()).thenReturn("""
                {
                  "rollingSummary": "Summary",
                  "state": {
                    "schemaVersion": 1,
                    "currentGoal": "test memory gate",
                    "confirmedDecisions": [],
                    "openQuestions": [],
                    "activePaperIds": [],
                    "userPreferences": []
                  },
                  "memoryCandidates": [
                    {
                      "shouldRemember": true,
                      "scope": "session",
                      "memoryType": "decision",
                      "content": "User chose rolling summaries for chat memory.",
                      "importance": 0.9,
                      "confidence": 0.85
                    },
                    {
                      "shouldRemember": false,
                      "scope": "session",
                      "memoryType": "temporary",
                      "content": "User said hello.",
                      "importance": 0.2,
                      "confidence": 0.9
                    }
                  ]
                }
                """);
        when(memoryRepository.save(any(ConversationMemory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        memoryService.updateAfterTurn(2L, "Start", "Done");

        verify(memoryRepository).save(any(ConversationMemory.class));
    }

    @Test
    void fallsBackToRollingAppendWhenModelReturnsInvalidJson() {
        when(chatSessionService.getConversationContext(3L)).thenReturn(ConversationContext.empty());
        when(chatClient.prompt().user(contains("STRICTLY valid JSON")).call().content()).thenReturn("not json");

        memoryService.updateAfterTurn(3L, "Question", "Answer");

        verify(chatSessionService).appendTurnToRollingSummary(3L, "Question", "Answer");
        verify(chatSessionService, never()).updateConversationMemory(any(), any(), any());
        verify(memoryRepository, never()).save(any());
    }

    // ── Recall tests ──

    @Test
    void recallsMemoriesSortedByImportanceConfidenceAndRecency() {
        ConversationMemory high = memoryWith(1L, "user_preference", "Prefer Chinese", 0.95, 0.9, hoursAgo(1));
        ConversationMemory mid = memoryWith(2L, "project_goal", "Implement RAG", 0.8, 0.85, hoursAgo(5));
        ConversationMemory low = memoryWith(3L, "paper_fact", "Paper uses BERT", 0.5, 0.6, hoursAgo(48));

        when(memoryRepository.findRelevantBySession(1L)).thenReturn(List.of(low, high, mid));

        List<String> result = memoryService.recallMemories(1L);

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isEqualTo("Prefer Chinese");   // highest score
        assertThat(result.get(1)).isEqualTo("Implement RAG");
        assertThat(result.get(2)).isEqualTo("Paper uses BERT");
    }

    @Test
    void recallBoostsByQueryKeywordOverlap() {
        // Give both equal base scores — the query boost should break the tie
        ConversationMemory relevant = memoryWith(1L, "project_goal",
                "Implement advanced RAG retrieval pipeline", 0.9, 0.9, hoursAgo(1));
        ConversationMemory unrelated = memoryWith(2L, "user_preference",
                "Prefer dark theme for the IDE", 0.9, 0.9, hoursAgo(1));

        when(memoryRepository.findRelevantBySession(1L)).thenReturn(List.of(unrelated, relevant));

        List<String> result = memoryService.recallMemories(1L, "RAG retrieval query enhancement", 5);

        // "Implement advanced RAG" should rank higher due to keyword overlap boost
        assertThat(result.get(0)).contains("RAG");
    }

    @Test
    void recallReturnsEmptyForNullSession() {
        assertThat(memoryService.recallMemories(null)).isEmpty();
        assertThat(memoryService.recallMemories(null, "query", 5)).isEmpty();
    }

    @Test
    void recallRespectsLimit() {
        when(memoryRepository.findRelevantBySession(1L)).thenReturn(List.of(
                memoryWith(1L, "t1", "M1", 0.9, 0.9, hoursAgo(1)),
                memoryWith(2L, "t2", "M2", 0.8, 0.8, hoursAgo(2)),
                memoryWith(3L, "t3", "M3", 0.7, 0.7, hoursAgo(3)),
                memoryWith(4L, "t4", "M4", 0.6, 0.6, hoursAgo(4)),
                memoryWith(5L, "t5", "M5", 0.5, 0.5, hoursAgo(5))
        ));

        List<String> result = memoryService.recallMemories(1L, null, 3);

        assertThat(result).hasSize(3);
    }

    // ── Dedup / Update tests ──

    @Test
    void updatesSimilarMemoryInsteadOfInsertingDuplicate() {
        when(chatSessionService.getConversationContext(4L)).thenReturn(ConversationContext.empty());
        when(chatClient.prompt().user(contains("memoryCandidates")).call().content()).thenReturn("""
                {
                  "rollingSummary": "Summary",
                  "state": {
                    "schemaVersion": 1,
                    "currentGoal": "test dedup",
                    "confirmedDecisions": [],
                    "openQuestions": [],
                    "activePaperIds": [],
                    "userPreferences": []
                  },
                  "memoryCandidates": [
                    {
                      "shouldRemember": true,
                      "scope": "session",
                      "memoryType": "user_preference",
                      "content": "User prefers Chinese answers for all technical questions.",
                      "importance": 0.95,
                      "confidence": 0.9,
                      "reason": "important language preference"
                    }
                  ]
                }
                """);

        ConversationMemory existing = memoryWith(5L, "user_preference",
                "User prefers Chinese answers.", 0.80, 0.85, hoursAgo(24));
        when(memoryRepository.findBySessionIdOrderByUpdatedAtDesc(4L)).thenReturn(List.of(existing));
        when(memoryRepository.save(any(ConversationMemory.class))).thenAnswer(inv -> inv.getArgument(0));

        memoryService.updateAfterTurn(4L, "Please answer in Chinese", "好的");

        ArgumentCaptor<ConversationMemory> captor = ArgumentCaptor.forClass(ConversationMemory.class);
        verify(memoryRepository, atLeastOnce()).save(captor.capture());
        ConversationMemory saved = captor.getValue();
        // Should update existing, so importance should be max(0.80, 0.95) = 0.95
        assertThat(saved.getImportance()).isEqualTo(0.95);
        assertThat(saved.getContent()).contains("prefers Chinese");
    }

    // ── Content filter tests ──

    @Test
    void rejectsMemoryContainingApiKey() {
        when(chatSessionService.getConversationContext(5L)).thenReturn(ConversationContext.empty());
        when(chatClient.prompt().user(contains("memoryCandidates")).call().content()).thenReturn("""
                {
                  "rollingSummary": "Summary",
                  "state": {
                    "schemaVersion": 1,
                    "currentGoal": "test",
                    "confirmedDecisions": [],
                    "openQuestions": [],
                    "activePaperIds": [],
                    "userPreferences": []
                  },
                  "memoryCandidates": [
                    {
                      "shouldRemember": true,
                      "scope": "session",
                      "memoryType": "workflow_rule",
                      "content": "API key is sk-abc123def456ghi789 for DashScope",
                      "importance": 0.9,
                      "confidence": 0.9,
                      "reason": "test"
                    }
                  ]
                }
                """);
        when(memoryRepository.findBySessionIdOrderByUpdatedAtDesc(5L)).thenReturn(List.of());

        memoryService.updateAfterTurn(5L, "Here is my key", "Got it");

        verify(memoryRepository, never()).save(any(ConversationMemory.class));
    }

    @Test
    void rejectsLowValueGreetingMemory() {
        when(chatSessionService.getConversationContext(6L)).thenReturn(ConversationContext.empty());
        when(chatClient.prompt().user(contains("memoryCandidates")).call().content()).thenReturn("""
                {
                  "rollingSummary": "Summary",
                  "state": {
                    "schemaVersion": 1,
                    "currentGoal": "test",
                    "confirmedDecisions": [],
                    "openQuestions": [],
                    "activePaperIds": [],
                    "userPreferences": []
                  },
                  "memoryCandidates": [
                    {
                      "shouldRemember": true,
                      "scope": "session",
                      "memoryType": "project_fact",
                      "content": "OK",
                      "importance": 0.75,
                      "confidence": 0.75,
                      "reason": "test"
                    }
                  ]
                }
                """);

        memoryService.updateAfterTurn(6L, "Hello", "Hi there");

        verify(memoryRepository, never()).save(any(ConversationMemory.class));
    }

    // ── Reason field test ──

    @Test
    void writesReasonFieldWhenProvided() {
        when(chatSessionService.getConversationContext(7L)).thenReturn(ConversationContext.empty());
        when(chatClient.prompt().user(contains("memoryCandidates")).call().content()).thenReturn("""
                {
                  "rollingSummary": "Summary",
                  "state": {
                    "schemaVersion": 1,
                    "currentGoal": "test reason",
                    "confirmedDecisions": [],
                    "openQuestions": [],
                    "activePaperIds": [],
                    "userPreferences": []
                  },
                  "memoryCandidates": [
                    {
                      "shouldRemember": true,
                      "scope": "project",
                      "memoryType": "research_topic",
                      "content": "User is building a RAG-based academic literature assistant",
                      "importance": 0.85,
                      "confidence": 0.9,
                      "reason": "Core project definition — needed for all future context"
                    }
                  ]
                }
                """);
        when(memoryRepository.findBySessionIdOrderByUpdatedAtDesc(7L)).thenReturn(List.of());
        when(memoryRepository.save(any(ConversationMemory.class))).thenAnswer(inv -> inv.getArgument(0));

        memoryService.updateAfterTurn(7L, "I'm building a RAG assistant", "Great project");

        ArgumentCaptor<ConversationMemory> captor = ArgumentCaptor.forClass(ConversationMemory.class);
        verify(memoryRepository).save(captor.capture());
        assertThat(captor.getValue().getReason()).contains("Core project definition");
    }

    // ── Deletion test ──

    @Test
    void deleteBySessionIdDelegatesToRepository() {
        memoryService.deleteBySessionId(8L);
        verify(memoryRepository).deleteBySessionId(8L);
    }

    @Test
    void getMemoriesReturnsEmptyForNullSession() {
        assertThat(memoryService.getMemories(null)).isEmpty();
    }

    // ── Helpers ──

    private static ConversationMemory memoryWith(Long id, String type, String content, double imp, double conf, LocalDateTime updatedAt) {
        ConversationMemory m = new ConversationMemory();
        m.setId(id);
        m.setSessionId(1L);
        m.setMemoryType(type);
        m.setContent(content);
        m.setImportance(imp);
        m.setConfidence(conf);
        m.setScope("session");
        m.setUpdatedAt(updatedAt);
        m.setCreatedAt(updatedAt.minusHours(1));
        return m;
    }

    private static LocalDateTime hoursAgo(int hours) {
        return LocalDateTime.now().minusHours(hours);
    }
}
