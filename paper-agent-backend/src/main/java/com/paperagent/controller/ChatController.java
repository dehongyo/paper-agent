package com.paperagent.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperagent.dto.ChatMessageResponse;
import com.paperagent.dto.ChatRequest;
import com.paperagent.dto.ChatSessionCreateRequest;
import com.paperagent.dto.ChatSessionResponse;
import com.paperagent.dto.TraceableChatRequest;
import com.paperagent.dto.TraceableChatResponse;
import com.paperagent.dto.TraceableChatStreamEvent;
import com.paperagent.entity.ChatMessage;
import com.paperagent.service.ChatService;
import com.paperagent.service.ChatSessionService;
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

    private final ChatService chatService;
    private final ChatSessionService chatSessionService;
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
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest request) {
        log.info("Stream chat: paperId={}, sessionId={}, message={}",
                request.paperId(), request.sessionId(), request.message());

        var history = request.sessionId() == null
                ? List.<com.paperagent.dto.ChatHistoryMessage>of()
                : chatSessionService.getRecentHistory(request.sessionId());
        saveUserMessage(request.sessionId(), request.message());

        StringBuilder assistant = new StringBuilder();
        Flux<String> stream = request.paperId() != null
                ? chatService.chatWithPaperStream(request.paperId(), request.message(), history)
                : chatService.chatStream(request.message(), history);

        return stream
                .doOnNext(assistant::append)
                .doOnComplete(() -> saveAssistantMessage(request.sessionId(), assistant.toString(), null));
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
        return chatService.chatWithEvidence(request.message(), request.paperId(), request.safeScope());
    }

    @PostMapping(value = "/rag/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<TraceableChatStreamEvent> chatRagStream(@Valid @RequestBody TraceableChatRequest request) {
        log.info("Stream RAG chat: paperId={}, scope={}, sessionId={}, message={}",
                request.paperId(), request.safeScope(), request.sessionId(), request.message());

        var history = request.sessionId() == null
                ? List.<com.paperagent.dto.ChatHistoryMessage>of()
                : chatSessionService.getRecentHistory(request.sessionId());
        saveUserMessage(request.sessionId(), request.message());

        StringBuilder assistant = new StringBuilder();
        AtomicReference<String> evidenceJson = new AtomicReference<>();
        return chatService.chatWithEvidenceStream(request.message(), request.paperId(), request.safeScope(), history)
                .doOnNext(event -> {
                    if ("answer".equals(event.type())) assistant.append(event.content());
                    if ("evidence".equals(event.type())) evidenceJson.set(toJson(event.evidence()));
                })
                .doOnComplete(() -> saveAssistantMessage(request.sessionId(), assistant.toString(), evidenceJson.get()));
    }

    private void saveUserMessage(Long sessionId, String content) {
        if (sessionId == null) return;
        chatSessionService.appendMessage(sessionId, ChatMessage.Role.USER, content, null);
    }

    private void saveAssistantMessage(Long sessionId, String content, String evidenceJson) {
        if (sessionId == null || content == null || content.isBlank()) return;
        chatSessionService.appendMessage(sessionId, ChatMessage.Role.ASSISTANT, content, evidenceJson);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize chat evidence", e);
        }
    }
}
