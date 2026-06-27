package com.paperagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_session")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paper_id", nullable = false)
    private Long paperId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "result_text", columnDefinition = "TEXT")
    private String resultText;

    @Column(nullable = false, length = 20)
    private String status; // "in_progress" or "completed"

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "in_progress";
    }
}
