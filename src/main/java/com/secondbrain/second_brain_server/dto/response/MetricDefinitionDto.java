package com.secondbrain.second_brain_server.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricDefinitionDto {

    private String metricKey;
    private String label;
    private String unit;
    private boolean isTrackedPerSession;
    private boolean isPR;
    private boolean isHigherBetter;
    private Integer displayOrder;
}
