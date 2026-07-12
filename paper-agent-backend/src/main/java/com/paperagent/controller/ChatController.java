package com.paperagent.controller;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.paperagent.dto.ChatMessageResponse;
import com.paperagent.dto.ChatRequest;
import com.paperagent.dto.ChatSessionCreateRequest;
import com.paperagent.dto.ChatSessionResponse;
import com.paperagent.dto.SessionMemoryResponse;
import com.paperagent.dto.TraceableChatRequest;
import com.paperagent.dto.TraceableChatResponse;
import com.paperagent.dto.TraceableChatStreamEvent;
import com.paperagent.entity.ChatMessage;
import com.paperagent.service.ChatService;
import com.paperagent.service.ChatSessionService;
import com.paperagent.service.ConversationMemoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final int MEMORY_RECALL_LIMIT = 8;

    private final ChatService chatService;
    private final ChatSessionService chatSessionService;
    private final ConversationMemoryService conversationMemoryService;
    private final ObjectMapper objectMapper;

    @GetMapping("/sessions")
    public List<ChatSessionResponse> listSessions(
            @RequestParam(defaultValue = "paper") String scope,
            @RequestParam(required = false) Long paperId
    ) {
        return chatSessionService.listSessions(scope, paperId);
    }

    @PostMapping("/sessions")
    public ChatSessionResponse createSession(@RequestBody ChatSessionCreateRequest request) {
        return chatSessionService.createSession(request);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<ChatMessageResponse> getMessages(@PathVariable Long sessionId) {
        return chatSessionService.getMessages(sessionId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public void deleteSession(@PathVariable Long sessionId) {
        chatSessionService.deleteSession(sessionId);
        conversationMemoryService.deleteBySessionId(sessionId);
    }

    @DeleteMapping("/sessions/{sessionId}/memory/{memoryId}")
    public java.util.Map<String, String> deleteMemory(
            @PathVariable Long sessionId,
            @PathVariable Long memoryId
    ) {
        conversationMemoryService.deleteMemory(memoryId);
        return java.util.Map.of("status", "deleted");
    }

    @GetMapping("/sessions/{sessionId}/memory")
    public SessionMemoryResponse getSessionMemory(@PathVariable Long sessionId) {
        var context = buildConversationContext(sessionId, null);
        var memories = conversationMemoryService.getMemories(sessionId);
        var entries = memories.stream()
                .map(m -> new SessionMemoryResponse.MemoryEntry(
                        m.getId(),
                        m.getScope(),
                        m.getMemoryType(),
                        m.getContent(),
                        m.getImportance(),
                        m.getConfidence(),
                        m.getUpdatedAt() != null ? m.getUpdatedAt().toString() : null
                ))
                .toList();
        return new SessionMemoryResponse(
                sessionId,
                context.rollingSummary(),
                context.stateJson(),
                entries,
                context.safeRecentMessages().size()
        );
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest request) {
        log.info("Stream chat: paperId={}, sessionId={}, message={}",
                request.paperId(), request.sessionId(), request.message());

        var context = buildConversationContext(request.sessionId(), request.message());
        saveUserMessage(request.sessionId(), request.message());

        StringBuilder assistant = new StringBuilder();
        Flux<String> stream = request.paperId() != null
                ? chatService.chatWithPaperStream(request.paperId(), request.message(), context)
                : chatService.chatStream(request.message(), context);

        return stream
                .doOnNext(assistant::append)
                .doOnComplete(() -> {
                    saveAssistantMessage(request.sessionId(), assistant.toString(), null);
                    updateConversationMemory(request.sessionId(), request.message(), assistant.toString());
                });
    }

    @PostMapping
    public String chat(@Valid @RequestBody ChatRequest request) {
        log.info("Chat: paperId={}, message={}", request.paperId(), request.message());

        if (request.paperId() != null) {
            return chatService.chatWithPaperStream(request.paperId(), request.message())
                    .collectList()
                    .map(list -> String.join("", list))
                    .block();
        }

        return chatService.chat(request.message());
    }

    @PostMapping("/rag")
    public TraceableChatResponse chatRag(@Valid @RequestBody TraceableChatRequest request) {
        var context = buildConversationContext(request.sessionId(), request.message());
        return chatService.chatWithEvidence(request.message(), request.filters(), request.safeScope(), context);
    }

    @PostMapping(value = "/rag/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<TraceableChatStreamEvent> chatRagStream(@Valid @RequestBody TraceableChatRequest request) {
        log.info("Stream RAG chat: paperId={}, scope={}, sessionId={}, message={}",
                request.paperId(), request.safeScope(), request.sessionId(), request.message());

        var context = buildConversationContext(request.sessionId(), request.message());
        saveUserMessage(request.sessionId(), request.message());

        StringBuilder assistant = new StringBuilder();
        AtomicReference<String> evidenceJson = new AtomicReference<>();
        return chatService.chatWithEvidenceStream(request.message(), request.filters(), request.safeScope(), context)
                .doOnNext(event -> {
                    if ("answer".equals(event.type())) assistant.append(event.content());
                    if ("evidence".equals(event.type())) evidenceJson.set(toJson(event.evidence()));
                })
                .doOnComplete(() -> {
                    saveAssistantMessage(request.sessionId(), assistant.toString(), evidenceJson.get());
                    updateConversationMemory(request.sessionId(), request.message(), assistant.toString());
                });
    }

    private com.paperagent.dto.ConversationContext buildConversationContext(Long sessionId, String currentQuery) {
        if (sessionId == null) {
            return com.paperagent.dto.ConversationContext.empty();
        }
        var base = chatSessionService.getConversationContext(sessionId);
        List<String> longTermMemories = conversationMemoryService.recallMemories(sessionId, currentQuery, MEMORY_RECALL_LIMIT);
        if (longTermMemories.isEmpty()) {
            return base;
        }
        return com.paperagent.dto.ConversationContext.withMemories(
                base.rollingSummary(),
                base.stateJson(),
                base.safeRecentMessages(),
                longTermMemories
        );
    }

    private void saveUserMessage(Long sessionId, String content) {
        if (sessionId == null) return;
        chatSessionService.appendMessage(sessionId, ChatMessage.Role.USER, content, null);
    }

    private void saveAssistantMessage(Long sessionId, String content, String evidenceJson) {
        if (sessionId == null || content == null || content.isBlank()) return;
        chatSessionService.appendMessage(sessionId, ChatMessage.Role.ASSISTANT, content, evidenceJson);
    }

    private void updateConversationMemory(Long sessionId, String userMessage, String assistantMessage) {
        if (sessionId == null || assistantMessage == null || assistantMessage.isBlank()) return;
        conversationMemoryService.updateAfterTurn(sessionId, userMessage, assistantMessage);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize chat evidence", e);
        }
    }
}
