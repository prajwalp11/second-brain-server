package com.secondbrain.second_brain_server.controllers;

import com.secondbrain.second_brain_server.dto.request.CreateDomainRequest;
import com.secondbrain.second_brain_server.dto.request.UpdateDomainRequest;
import com.secondbrain.second_brain_server.dto.response.DomainResponse;
import com.secondbrain.second_brain_server.dto.response.GeneratedSystemResponse;
import com.secondbrain.second_brain_server.dto.response.TimeSeriesPointResponse;
import com.secondbrain.second_brain_server.security.CurrentUser;
import com.secondbrain.second_brain_server.services.DomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Slf4j
@RequestMapping("/api/domains")
@RequiredArgsConstructor
public class DomainController {

    private final DomainService domainService;

    @GetMapping
    public ResponseEntity<List<DomainResponse>> getDomains(@CurrentUser UUID userId) {
        return ResponseEntity.ok(domainService.getDomainsForUser(userId));
    }

    @GetMapping("/{domainId}")
    public ResponseEntity<DomainResponse> getDomain(@PathVariable UUID domainId, @CurrentUser UUID userId) {
        return ResponseEntity.ok(domainService.getDomainById(domainId, userId));
    }

    @GetMapping("/{domainId}/chart-data")
    public ResponseEntity<List<TimeSeriesPointResponse>> getChartData(
            @PathVariable UUID domainId,
            @RequestParam(defaultValue = "30") int days,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(domainService.getChartData(domainId, userId, days));
    }

    @PostMapping
    public ResponseEntity<DomainResponse> createDomain(@RequestBody CreateDomainRequest request, @CurrentUser UUID userId) {
        return ResponseEntity.ok(domainService.createDomain(userId, request));
    }

    @PutMapping("/{domainId}")
    public ResponseEntity<DomainResponse> updateDomain(@PathVariable UUID domainId, @RequestBody UpdateDomainRequest request, @CurrentUser UUID userId) {
        return ResponseEntity.ok(domainService.updateDomain(domainId, userId, request));
    }

    @PostMapping("/{domainId}/pause")
    public ResponseEntity<Void> pauseDomain(@PathVariable UUID domainId, @CurrentUser UUID userId) {
        domainService.pauseDomain(domainId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{domainId}/archive")
    public ResponseEntity<Void> archiveDomain(@PathVariable UUID domainId, @CurrentUser UUID userId) {
        domainService.archiveDomain(domainId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{domainId}/generate-system")
    public ResponseEntity<GeneratedSystemResponse> generateSystem(@PathVariable UUID domainId, @CurrentUser UUID userId) {
        return ResponseEntity.ok(domainService.generateAndApplySystem(domainId, userId));
    }
}
