package com.paperagent.repository;

import com.paperagent.entity.AutonomousWritingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AutonomousWritingSessionRepository extends JpaRepository<AutonomousWritingSession, Long> {
    List<AutonomousWritingSession> findAllByOrderByCreatedAtDesc();
}
