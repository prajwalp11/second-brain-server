package com.secondbrain.second_brain_server.controllers;

import com.secondbrain.second_brain_server.dto.request.CreateMetricDefinitionRequest;
import com.secondbrain.second_brain_server.dto.response.MetricDefinitionDto;
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
    public ResponseEntity<List<MetricDefinitionDto>> getMetrics(@PathVariable UUID domainId, @CurrentUser UUID userId) {
        return ResponseEntity.ok(metricDefinitionService.getMetricsForDomain(domainId, userId));
    }

    @PostMapping
    public ResponseEntity<MetricDefinitionDto> createMetric(@RequestBody CreateMetricDefinitionRequest request, @CurrentUser UUID userId) {
        return ResponseEntity.ok(metricDefinitionService.createMetric(request, userId));
    }

    @DeleteMapping("/{metricId}")
    public ResponseEntity<Void> deleteMetric(@PathVariable UUID metricId, @CurrentUser UUID userId) {
        metricDefinitionService.deleteMetric(metricId, userId);
        return ResponseEntity.ok().build();
    }
}
