package com.secondbrain.second_brain_server.entities;

import com.secondbrain.second_brain_server.enums.FeelLabel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "session_logs")
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

    private LocalDate logDate;

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

    @OneToMany(mappedBy = "sessionLog", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SessionMetricValue> metricValues;
}
