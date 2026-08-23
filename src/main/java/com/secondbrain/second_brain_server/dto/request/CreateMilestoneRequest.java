package com.secondbrain.second_brain_server.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateMilestoneRequest {

    @NotNull
    private UUID domainId;

    @NotBlank
    private String label;

    @NotBlank
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "metricKey must be snake_case (lowercase letters, numbers, and underscores only, starting with a letter)")
    private String metricKey;

    @NotNull
    private Double targetValue;

    private String unit;

    private LocalDate deadline;
}
