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

    /** Whether a higher value is better (default) or lower is better (e.g. time, SWOLF). */
    @Column(name = "higher_better")
    private boolean higherBetter;

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
        double progress = computeProgress();
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

    /**
     * Progress toward the target as 0-100, direction-aware.
     * - Higher-is-better: current / target (e.g. 60 of 100 reps = 60%).
     * - Lower-is-better: progress grows as the value FALLS toward the target. Measured from a
     *   baseline of 2x target (a reasonable "starting" value) down to the target, so a value at or
     *   below target is 100% and a value well above it is near 0% — never a misleading half-full bar.
     * DONE is always 100%.
     */
    private double computeProgress() {
        if (status == MilestoneStatus.DONE) {
            return 100.0;
        }
        if (targetValue == null || currentValue == null || targetValue <= 0) {
            return 0.0;
        }
        if (higherBetter) {
            return Math.max(0.0, Math.min(100.0, (currentValue / targetValue) * 100.0));
        }
        // Lower-is-better: at/under target => 100%; baseline 2x target => 0%.
        if (currentValue <= targetValue) {
            return 100.0;
        }
        double baseline = targetValue * 2.0;
        if (currentValue >= baseline) {
            return 0.0;
        }
        double pct = (baseline - currentValue) / (baseline - targetValue) * 100.0;
        return Math.max(0.0, Math.min(100.0, pct));
    }

}
