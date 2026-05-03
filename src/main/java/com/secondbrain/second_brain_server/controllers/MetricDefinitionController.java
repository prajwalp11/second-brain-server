package com.secondbrain.second_brain_server.controllers;

import com.secondbrain.second_brain_server.dto.request.CreateMetricDefinitionRequest;
import com.secondbrain.second_brain_server.dto.response.MetricDefinitionResponse;
import com.secondbrain.second_brain_server.security.CurrentUser;
import com.secondbrain.second_brain_server.services.MetricDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricDefinitionController {

    private final MetricDefinitionService metricDefinitionService;

    @GetMapping("/domain/{domainId}")
    public ResponseEntity<List<MetricDefinitionResponse>> getMetrics(@PathVariable UUID domainId, @CurrentUser UUID userId) {
        return ResponseEntity.ok(metricDefinitionService.getMetricsForDomain(domainId, userId));
    }

    @PostMapping
    public ResponseEntity<MetricDefinitionResponse> createMetric(@RequestBody CreateMetricDefinitionRequest request, @CurrentUser UUID userId) {
        return ResponseEntity.ok(metricDefinitionService.createMetric(request, userId));
    }

    @DeleteMapping("/{metricId}")
    public ResponseEntity<Void> deleteMetric(@PathVariable UUID metricId, @CurrentUser UUID userId) {
        metricDefinitionService.deleteMetric(metricId, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/domain/{domainId}/reorder")
    public ResponseEntity<Void> reorderMetrics(
            @PathVariable UUID domainId,
            @RequestBody List<UUID> metricIds,
            @CurrentUser UUID userId) {
        metricDefinitionService.reorderMetrics(domainId, metricIds, userId);
        return ResponseEntity.ok().build();
    }
}
