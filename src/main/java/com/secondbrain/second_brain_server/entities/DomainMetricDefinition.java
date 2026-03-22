package com.secondbrain.second_brain_server.entities;

import com.secondbrain.second_brain_server.dto.response.MetricDefinitionDto;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "domain_metric_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainMetricDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id")
    private Domain domain;

    private String metricKey;

    private String label;

    private String unit;

    private boolean isTrackedPerSession;

    private boolean isPR;

    private boolean isHigherBetter;

    private Integer displayOrder;

    public MetricDefinitionDto toDto() {
        return MetricDefinitionDto.builder()
                .metricKey(this.metricKey)
                .label(this.label)
                .unit(this.unit)
                .isTrackedPerSession(this.isTrackedPerSession)
                .isPR(this.isPR)
                .isHigherBetter(this.isHigherBetter)
                .displayOrder(this.displayOrder)
                .build();
    }
}
