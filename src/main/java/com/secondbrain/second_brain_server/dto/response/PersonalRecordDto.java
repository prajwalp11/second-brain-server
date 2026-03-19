package com.secondbrain.second_brain_server.dto.response;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalRecordDto {

    private String metricKey;
    private String label;
    private Double value;
    private String unit;
    private LocalDate achievedAt;
    private Double previousValue;
    private Double delta;
}
