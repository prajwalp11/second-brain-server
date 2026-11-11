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

    /**
     * AI-provided scale hint: "BOUNDED_100" (a 0-100 score/rating/percentage) or "OPEN_ENDED"
     * (a cumulative quantity like minutes, reps, distance). Used by MetricBounds to resolve
     * min/max deterministically; a missing/unknown value falls back to server-side inference.
     */
    private String scale;

    private Integer displayOrder;
}
