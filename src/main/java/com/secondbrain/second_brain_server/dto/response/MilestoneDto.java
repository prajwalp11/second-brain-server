package com.secondbrain.second_brain_server.dto.response;

import com.secondbrain.second_brain_server.entities.Milestone;
import com.secondbrain.second_brain_server.enums.MilestoneStatus;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MilestoneDto {

    private UUID id;
    private UUID domainId;
    private String label;
    private String metricKey;
    private Double targetValue;
    private Double currentValue;
    private String unit;
    private Double progressPercent;
    private MilestoneStatus status;
    private LocalDate deadline;
    private LocalDate completedAt;

    public MilestoneDto(Milestone milestone) {
        this.id = milestone.getId();
        this.domainId = milestone.getDomain() != null ? milestone.getDomain().getId() : null;
        this.label = milestone.getLabel();
        this.metricKey = milestone.getMetricKey();
        this.targetValue = milestone.getTargetValue();
        this.currentValue = milestone.getCurrentValue();
        this.unit = milestone.getUnit();
        this.progressPercent = milestone.getTargetValue() != null && milestone.getTargetValue() != 0
                ? (milestone.getCurrentValue() / milestone.getTargetValue()) * 100
                : 0.0;
        this.status = milestone.getStatus();
        this.deadline = milestone.getDeadline();
        this.completedAt = milestone.getCompletedAt();
    }
}
