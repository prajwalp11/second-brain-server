package com.secondbrain.second_brain_server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMilestoneRequest {

    @NotNull
    private UUID domainId;

    @NotBlank
    private String label;

    @NotBlank
    private String metricKey;

    @NotNull
    private Double targetValue;

    private String unit;

    private LocalDate deadline;
}
