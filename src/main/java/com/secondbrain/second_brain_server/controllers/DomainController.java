package com.secondbrain.second_brain_server.controllers;

import com.secondbrain.second_brain_server.dto.request.CreateDomainRequest;
import com.secondbrain.second_brain_server.dto.request.UpdateDomainRequest;
import com.secondbrain.second_brain_server.dto.response.DomainDto;
import com.secondbrain.second_brain_server.dto.response.GeneratedSystemDto;
import com.secondbrain.second_brain_server.security.CurrentUser;
import com.secondbrain.second_brain_server.services.DomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/domains")
@RequiredArgsConstructor
public class DomainController {

    private final DomainService domainService;

    @GetMapping
    public ResponseEntity<List<DomainDto>> getDomains(@CurrentUser UUID userId) {
        return ResponseEntity.ok(domainService.getDomainsForUser(userId));
    }

    @GetMapping("/{domainId}")
    public ResponseEntity<DomainDto> getDomain(@PathVariable UUID domainId, @CurrentUser UUID userId) {
        return ResponseEntity.ok(domainService.getDomainById(domainId, userId));
    }

    @PostMapping
    public ResponseEntity<DomainDto> createDomain(@RequestBody CreateDomainRequest request, @CurrentUser UUID userId) {
        return ResponseEntity.ok(domainService.createDomain(userId, request));
    }

    @PutMapping("/{domainId}")
    public ResponseEntity<DomainDto> updateDomain(@PathVariable UUID domainId, @RequestBody UpdateDomainRequest request, @CurrentUser UUID userId) {
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
    public ResponseEntity<GeneratedSystemDto> generateSystem(@PathVariable UUID domainId, @CurrentUser UUID userId) {
        return ResponseEntity.ok(domainService.generateAndApplySystem(domainId, userId));
    }
}
