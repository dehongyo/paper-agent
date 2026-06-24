package com.paperagent.repository;

import com.paperagent.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByMessageOrderAsc(Long sessionId);

    List<ChatMessage> findTop12BySessionIdOrderByMessageOrderDesc(Long sessionId);

    void deleteBySessionId(Long sessionId);

    @Query("SELECT MAX(m.messageOrder) FROM ChatMessage m WHERE m.sessionId = :sessionId")
    Optional<Integer> findMaxMessageOrderBySessionId(@Param("sessionId") Long sessionId);
}
