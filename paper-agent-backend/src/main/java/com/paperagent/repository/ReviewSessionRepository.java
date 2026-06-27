package com.paperagent.repository;

import com.paperagent.entity.ReviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewSessionRepository extends JpaRepository<ReviewSession, Long> {
    List<ReviewSession> findAllByOrderByCreatedAtDesc();
    List<ReviewSession> findByPaperIdOrderByCreatedAtDesc(Long paperId);
}
