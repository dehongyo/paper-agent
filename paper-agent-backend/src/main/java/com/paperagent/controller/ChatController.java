package com.paperagent.controller;

import com.paperagent.dto.ChatRequest;
import com.paperagent.dto.TraceableChatRequest;
import com.paperagent.dto.TraceableChatResponse;
import com.paperagent.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 流式对话（SSE）
     * POST /api/chat/stream
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest request) {
        log.info("Stream chat: paperId={}, message={}", request.paperId(), request.message());

        if (request.paperId() != null) {
            return chatService.chatWithPaperStream(request.paperId(), request.message());
        }

        // 普通对话也用流式返回单条消息
        return Flux.just(chatService.chat(request.message()));
    }

    /**
     * 非流式对话
     * POST /api/chat
     */
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
}
