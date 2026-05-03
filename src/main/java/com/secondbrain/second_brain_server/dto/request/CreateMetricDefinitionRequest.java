package com.secondbrain.second_brain_server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMetricDefinitionRequest {

    @NotNull
    private UUID domainId;

    @NotBlank
    private String metricKey;

    @NotBlank
    private String label;

    private String unit;

    private boolean isTrackedPerSession;

    private boolean isPR;

    private boolean isHigherBetter;

    private Integer displayOrder;
}
