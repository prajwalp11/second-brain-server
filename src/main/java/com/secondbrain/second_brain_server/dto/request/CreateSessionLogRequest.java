package com.secondbrain.second_brain_server.dto.request;

import com.secondbrain.second_brain_server.enums.FeelLabel;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSessionLogRequest {

    @NotNull
    private UUID domainId;

    private String sessionType;

    @NotNull
    private LocalDate logDate;

    private Integer durationMinutes;

    private Integer feelScore;

    private FeelLabel feelLabel;

    private String notes;

    private String linkedReferenceUrl;

    @NotEmpty
    private Map<String, Double> metrics;
}
