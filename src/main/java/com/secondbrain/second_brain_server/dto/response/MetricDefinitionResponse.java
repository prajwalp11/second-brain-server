package com.secondbrain.second_brain_server.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetricDefinitionResponse {

    private UUID id;
    private String metricKey;
    private String label;
    private String unit;

    @JsonProperty("isTrackedPerSession")
    private boolean isTrackedPerSession;

    @JsonProperty("isPR")
    private boolean isPR;

    @JsonProperty("isHigherBetter")
    private boolean isHigherBetter;

    private Double minValue;
    private Double maxValue;

    private Integer displayOrder;
}
