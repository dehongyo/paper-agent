package com.paperagent.repository;

import com.paperagent.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByScopeOrderByUpdatedAtDesc(ChatSession.Scope scope);

    List<ChatSession> findByScopeAndPaperIdOrderByUpdatedAtDesc(ChatSession.Scope scope, Long paperId);
}
