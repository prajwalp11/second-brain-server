package com.secondbrain.second_brain_server.controllers;

import com.secondbrain.second_brain_server.dto.request.CreateMilestoneRequest;
import com.secondbrain.second_brain_server.dto.response.MilestoneDto;
import com.secondbrain.second_brain_server.enums.MilestoneStatus;
import com.secondbrain.second_brain_server.security.CurrentUser;
import com.secondbrain.second_brain_server.services.MilestoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/milestones")
@RequiredArgsConstructor
public class MilestoneController {

    private final MilestoneService milestoneService;

    @GetMapping("/{domainId}")
    public ResponseEntity<List<MilestoneDto>> getMilestones(@PathVariable UUID domainId, @CurrentUser UUID userId) {
        return ResponseEntity.ok(milestoneService.getMilestonesForDomain(domainId, userId));
    }

    @PostMapping
    public ResponseEntity<MilestoneDto> createMilestone(@RequestBody CreateMilestoneRequest request, @CurrentUser UUID userId) {
        return ResponseEntity.ok(milestoneService.createMilestone(userId, request));
    }

    @PutMapping("/{milestoneId}/status")
    public ResponseEntity<MilestoneDto> updateStatus(@PathVariable UUID milestoneId, @RequestParam MilestoneStatus status, @CurrentUser UUID userId) {
        return ResponseEntity.ok(milestoneService.updateStatus(milestoneId, userId, status));
    }
}
