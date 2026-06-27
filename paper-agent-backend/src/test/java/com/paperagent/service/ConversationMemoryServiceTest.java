package com.paperagent.service;

import com.paperagent.dto.ConversationContext;
import com.paperagent.entity.ConversationMemory;
import com.paperagent.repository.ConversationMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
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

    ConversationMemoryService memoryService;

    @BeforeEach
    void setUp() {
        memoryService = new ConversationMemoryService(
                chatClient,
                chatSessionService,
                memoryRepository,
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
}
