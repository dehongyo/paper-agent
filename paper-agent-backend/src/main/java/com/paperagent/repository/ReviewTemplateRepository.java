package com.paperagent.repository;

import com.paperagent.entity.ReviewTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewTemplateRepository extends JpaRepository<ReviewTemplate, Long> {
    List<ReviewTemplate> findAllByOrderByCreatedAtDesc();
    List<ReviewTemplate> findByType(String type);
    long countByType(String type);
}
