package com.paperagent.repository;

import com.paperagent.entity.ConversationMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ConversationMemoryRepository extends JpaRepository<ConversationMemory, Long> {

    List<ConversationMemory> findBySessionIdOrderByUpdatedAtDesc(Long sessionId);

    List<ConversationMemory> findBySessionIdAndMemoryTypeOrderByUpdatedAtDesc(Long sessionId, String memoryType);

    List<ConversationMemory> findByScopeOrderByUpdatedAtDesc(String scope);

    @Query("""
            SELECT m FROM ConversationMemory m
            WHERE m.sessionId = :sessionId
            ORDER BY (m.importance * 0.4 + m.confidence * 0.3) DESC, m.updatedAt DESC
            """)
    List<ConversationMemory> findRelevantBySession(Long sessionId);

    @Modifying
    @Query("DELETE FROM ConversationMemory m WHERE m.sessionId = :sessionId")
    void deleteBySessionId(Long sessionId);
}
