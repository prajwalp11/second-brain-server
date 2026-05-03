package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.request.CreateMetricDefinitionRequest;
import com.secondbrain.second_brain_server.dto.response.MetricDefinitionResponse;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.DomainMetricDefinition;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.repository.DomainMetricDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetricDefinitionService {

    private final DomainMetricDefinitionRepository metricDefinitionRepository;
    private final DomainService domainService;

    public List<MetricDefinitionResponse> getMetricsForDomain(UUID domainId, UUID userId) {
        domainService.assertOwnership(domainId, userId);
        return metricDefinitionRepository.findByDomainIdOrderByDisplayOrder(domainId)
                .stream()
                .map(DomainMetricDefinition::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public MetricDefinitionResponse createMetric(CreateMetricDefinitionRequest request, UUID userId) {
        Domain domain = domainService.assertOwnership(request.getDomainId(), userId);

        DomainMetricDefinition metric = DomainMetricDefinition.builder()
                .domain(domain)
                .metricKey(request.getMetricKey())
                .label(request.getLabel())
                .unit(request.getUnit())
                .isTrackedPerSession(request.isTrackedPerSession())
                .isPR(request.isPR())
                .isHigherBetter(request.isHigherBetter())
                .displayOrder(request.getDisplayOrder())
                .build();

        return metricDefinitionRepository.save(metric).toResponse();
    }

    @Transactional
    public void deleteMetric(UUID metricId, UUID userId) {
        DomainMetricDefinition metric = metricDefinitionRepository.findById(metricId)
                .orElseThrow(() -> new ResourceNotFoundException("Metric", metricId));

        domainService.assertOwnership(metric.getDomain().getId(), userId);
        metricDefinitionRepository.delete(metric);
    }

    @Transactional
    public void reorderMetrics(UUID domainId, List<UUID> metricIds, UUID userId) {
        domainService.assertOwnership(domainId, userId);
        
        for (int i = 0; i < metricIds.size(); i++) {
            UUID metricId = metricIds.get(i);
            DomainMetricDefinition metric = metricDefinitionRepository.findById(metricId)
                    .orElseThrow(() -> new ResourceNotFoundException("Metric", metricId));
            metric.setDisplayOrder(i);
            metricDefinitionRepository.save(metric);
        }
    }
}
