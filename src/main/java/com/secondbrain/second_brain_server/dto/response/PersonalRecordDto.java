package com.secondbrain.second_brain_server.dto.response;

import lombok.*;


import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalRecordDto {

    private UUID domainId;
    private String metricKey;
    private String label;
    private Double value;
    private String unit;
    private LocalDateTime achievedAt;
    private Double previousValue;
    private Double delta;
}
