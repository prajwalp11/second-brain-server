package com.secondbrain.second_brain_server.controllers;

import com.secondbrain.second_brain_server.dto.response.PersonalRecordDto;
import com.secondbrain.second_brain_server.dto.response.ProgressResponse;
import com.secondbrain.second_brain_server.security.CurrentUser;
import com.secondbrain.second_brain_server.services.PersonalRecordService;
import com.secondbrain.second_brain_server.services.ProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;
    private final PersonalRecordService prService;

    @GetMapping("/{domainId}/metric/{metricKey}")
    public ResponseEntity<ProgressResponse> getProgress(@PathVariable UUID domainId,
                                                        @PathVariable String metricKey,
                                                        @RequestParam LocalDate from,
                                                        @RequestParam LocalDate to,
                                                        @CurrentUser UUID userId) {
        return ResponseEntity.ok(progressService.getProgress(domainId, userId, metricKey, from, to));
    }

    @GetMapping("/{domainId}/prs")
    public ResponseEntity<List<PersonalRecordDto>> getPrs(@PathVariable UUID domainId) {
        return ResponseEntity.ok(prService.getPrsForDomain(domainId));
    }
}
