package com.secondbrain.second_brain_server.entities;

import com.secondbrain.second_brain_server.enums.StepStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One ordered step in a domain's AI-generated course (D1 — one ordered list per domain).
 * The video is NOT stored here (D2): only `videoQuery` (what to resolve) and `videoQueryKey`
 * (pointer into video_cache) live here; the actual URL/title/confidence come from VideoCache.
 */
@Entity
@Table(name = "curriculum_step",
        uniqueConstraints = @UniqueConstraint(name = "uq_curriculum_step_order", columnNames = {"domain_id", "order_index"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurriculumStep {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id")
    private Domain domain;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    private String phase;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepStatus status;

    @Column(name = "est_minutes")
    private Integer estMinutes;

    @Column(name = "video_query")
    private String videoQuery;

    @Column(name = "video_query_key")
    private String videoQueryKey;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = StepStatus.LOCKED;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
