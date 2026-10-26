package com.secondbrain.second_brain_server.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cached Progress-tab insights for a single domain (highlights/patterns/suggestions).
 * One row per domain. Populated by InsightsRefreshScheduler (daily) or the manual
 * refresh endpoint; the GET API only reads it. List fields stored as JSON text.
 */
@Entity
@Table(name = "domain_insights")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainInsight {

    @Id
    @Column(name = "domain_id")
    private UUID domainId;

    @Column(columnDefinition = "TEXT")
    private String highlights;

    @Column(columnDefinition = "TEXT")
    private String patterns;

    @Column(columnDefinition = "TEXT")
    private String suggestions;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;
}
