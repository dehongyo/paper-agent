package com.paperagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "autonomous_writing_session")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutonomousWritingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String topic;

    @Column(name = "current_phase", nullable = false, length = 30)
    private String currentPhase;

    @Column(name = "topic_analysis_json", columnDefinition = "TEXT")
    private String topicAnalysisJson;

    @Column(name = "search_results_json", columnDefinition = "TEXT")
    private String searchResultsJson;

    @Column(name = "imported_paper_ids", columnDefinition = "TEXT")
    private String importedPaperIds;

    @Column(columnDefinition = "TEXT")
    private String outline;

    @Column(columnDefinition = "TEXT")
    private String draft;

    @Column(name = "final_draft", columnDefinition = "TEXT")
    private String finalDraft;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "in_progress";
        if (currentPhase == null) currentPhase = "topic_analysis";
    }
}
