package com.secondbrain.second_brain_server.dto.response;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklyStatDto {

    private UUID domainId;
    private String domainName;
    private String metricKey;
    private String label;
    private Double value;
    private Double target;
    private String unit;
}
