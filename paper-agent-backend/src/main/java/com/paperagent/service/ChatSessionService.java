package com.paperagent.service;

import com.paperagent.dto.ChatHistoryMessage;
import com.paperagent.dto.ChatMessageResponse;
import com.paperagent.dto.ChatSessionCreateRequest;
import com.paperagent.dto.ChatSessionResponse;
import com.paperagent.entity.ChatMessage;
import com.paperagent.entity.ChatSession;
import com.paperagent.repository.ChatMessageRepository;
import com.paperagent.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private static final int HISTORY_LIMIT = 12;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(String scope, Long paperId) {
        ChatSession.Scope safeScope = parseScope(scope);
        List<ChatSession> sessions = safeScope == ChatSession.Scope.PAPER && paperId != null
                ? sessionRepository.findByScopeAndPaperIdOrderByUpdatedAtDesc(safeScope, paperId)
                : sessionRepository.findByScopeOrderByUpdatedAtDesc(safeScope);
        return sessions.stream().map(ChatSessionResponse::from).toList();
    }

    @Transactional
    public ChatSessionResponse createSession(ChatSessionCreateRequest request) {
        ChatSession.Scope scope = parseScope(request.safeScope());
        Long paperId = scope == ChatSession.Scope.PAPER ? request.paperId() : null;
        ChatSession session = ChatSession.builder()
                .title(resolveTitle(request.title(), scope, paperId))
                .scope(scope)
                .paperId(paperId)
                .build();
        return ChatSessionResponse.from(sessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(Long sessionId) {
        return messageRepository.findBySessionIdOrderByMessageOrderAsc(sessionId)
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatHistoryMessage> getRecentHistory(Long sessionId) {
        List<ChatMessage> messages = new ArrayList<>(
                messageRepository.findTop12BySessionIdOrderByMessageOrderDesc(sessionId)
        );
        Collections.reverse(messages);
        return messages.stream()
                .map(message -> new ChatHistoryMessage(
                        message.getRole().name().toLowerCase(),
                        message.getContent()
                ))
                .toList();
    }

    @Transactional
    public ChatMessageResponse appendMessage(Long sessionId, ChatMessage.Role role, String content, String evidenceJson) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Chat session not found: " + sessionId));
        int nextOrder = messageRepository.findMaxMessageOrderBySessionId(sessionId).orElse(0) + 1;
        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .evidenceJson(evidenceJson)
                .messageOrder(nextOrder)
                .build();
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
        return ChatMessageResponse.from(messageRepository.save(message));
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        messageRepository.deleteBySessionId(sessionId);
        sessionRepository.deleteById(sessionId);
    }

    private ChatSession.Scope parseScope(String scope) {
        return "library".equalsIgnoreCase(scope) ? ChatSession.Scope.LIBRARY : ChatSession.Scope.PAPER;
    }

    private String resolveTitle(String title, ChatSession.Scope scope, Long paperId) {
        if (title != null && !title.isBlank()) return title.trim();
        if (scope == ChatSession.Scope.LIBRARY) return "文献库对话";
        return paperId == null ? "论文对话" : "论文 #" + paperId + " 对话";
    }
}
