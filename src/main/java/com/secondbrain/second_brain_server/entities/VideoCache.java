package com.secondbrain.second_brain_server.entities;

import com.secondbrain.second_brain_server.enums.Confidence;
import com.secondbrain.second_brain_server.enums.VideoSource;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One resolved video per normalized query, shared across all users (D2 — single source of truth).
 * Curriculum steps point here via query_key; the feedback loop demotes/replaces entries here so
 * every pointing step reflects the change automatically.
 */
@Entity
@Table(name = "video_cache")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoCache {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "query_key", unique = true, nullable = false)
    private String queryKey;

    @Column(name = "video_url")
    private String videoUrl;

    @Column(name = "video_title")
    private String videoTitle;

    @Column(name = "channel_id")
    private String channelId;

    @Enumerated(EnumType.STRING)
    private VideoSource source;

    /** YouTube-stats confidence at resolve time. */
    @Enumerated(EnumType.STRING)
    private Confidence confidence;

    /** Recomputed on each vote: blend of stats confidence + Wilson(feedback), weighted by vote volume. */
    @Enumerated(EnumType.STRING)
    @Column(name = "effective_confidence")
    private Confidence effectiveConfidence;

    @Column(name = "helpful_count")
    private int helpfulCount;

    @Column(name = "unhelpful_count")
    private int unhelpfulCount;

    private boolean demoted;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (resolvedAt == null) resolvedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
