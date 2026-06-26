package com.paperagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "writing_versions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WritingVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500)
    private String topic;

    @Column(columnDefinition = "TEXT")
    private String outline;

    @Column(columnDefinition = "TEXT")
    private String draft;

    @Column(name = "references_json", columnDefinition = "TEXT")
    private String referencesJson;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (versionNumber == null) versionNumber = 1;
    }
}
