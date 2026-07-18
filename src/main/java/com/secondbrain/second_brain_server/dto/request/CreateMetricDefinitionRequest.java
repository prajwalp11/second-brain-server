package com.secondbrain.second_brain_server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "metricKey must be snake_case (lowercase letters, numbers, and underscores only, starting with a letter)")
    private String metricKey;

    @NotBlank
    private String label;

    private String unit;

    private boolean isTrackedPerSession;

    private boolean isPR;

    private boolean isHigherBetter;

    private Integer displayOrder;
}
