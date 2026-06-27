package com.paperagent.repository;

import com.paperagent.entity.ConversationMemory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationMemoryRepository extends JpaRepository<ConversationMemory, Long> {

    List<ConversationMemory> findBySessionIdOrderByUpdatedAtDesc(Long sessionId);
}
