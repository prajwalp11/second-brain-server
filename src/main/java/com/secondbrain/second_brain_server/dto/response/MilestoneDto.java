package com.secondbrain.second_brain_server.dto.response;

import com.secondbrain.second_brain_server.entities.Milestone;
import com.secondbrain.second_brain_server.enums.MilestoneStatus;
import lombok.*;

import java.time.LocalDateTime;
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
    private LocalDateTime deadline;
    private LocalDateTime completedAt;

}
