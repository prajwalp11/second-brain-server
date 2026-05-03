package com.secondbrain.second_brain_server.entities;

import com.secondbrain.second_brain_server.dto.response.SessionLogDto;
import com.secondbrain.second_brain_server.enums.FeelLabel;
import jakarta.persistence.*;
import lombok.*;


import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "session_logs", indexes = {
        @Index(name = "idx_session_log_domain_date", columnList = "domain_id, log_date"),
        @Index(name = "idx_session_log_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id")
    private Domain domain;

    private String sessionType;

    private LocalDateTime logDate;

    private Integer durationMinutes;

    private Integer feelScore;

    @Enumerated(EnumType.STRING)
    private FeelLabel feelLabel;

    @Lob
    private String notes;

    private String linkedReferenceUrl;

    @Lob
    private String aiInsight;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @OneToMany(mappedBy = "sessionLog", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SessionMetricValue> metricValues;

    public SessionLogDto toDto() {
        return SessionLogDto.builder()
                .id(this.id)
                .domainId(this.domain != null ? this.domain.getId() : null)
                .sessionType(this.sessionType)
                .logDate(this.logDate)
                .durationMinutes(this.durationMinutes)
                .feelScore(this.feelScore)
                .feelLabel(this.feelLabel)
                .notes(this.notes)
                .linkedReferenceUrl(this.linkedReferenceUrl)
                .aiInsight(this.aiInsight)
                .build();
    }
}
