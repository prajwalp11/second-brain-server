package com.secondbrain.second_brain_server.entities;

import com.secondbrain.second_brain_server.dto.response.MilestoneResponse;
import com.secondbrain.second_brain_server.enums.MilestoneStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "milestones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id")
    private Domain domain;

    private String label;

    private String metricKey;

    private Double targetValue;

    private Double currentValue;

    private String unit;

    @Enumerated(EnumType.STRING)
    private MilestoneStatus status;

    private LocalDate deadline;

    private LocalDateTime completedAt;

    private boolean aiGenerated;

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

    public MilestoneResponse toResponse() {
        double progress = (targetValue != null && targetValue > 0 && currentValue != null)
                ? Math.min(100.0, (currentValue / targetValue) * 100.0)
                : 0.0;
        return MilestoneResponse.builder()
                .id(this.id)
                .label(this.label)
                .metricKey(this.metricKey)
                .targetValue(this.targetValue)
                .currentValue(this.currentValue)
                .unit(this.unit)
                .progressPercent(progress)
                .status(this.status)
                .deadline(this.deadline)
                .completedAt(this.completedAt)
                .build();
    }

}
