package com.secondbrain.second_brain_server.controllers;

import com.secondbrain.second_brain_server.dto.response.InsightsResponse;
import com.secondbrain.second_brain_server.security.CurrentUser;
import com.secondbrain.second_brain_server.service.InsightsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Slf4j
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final InsightsService insightsService;

    /** Read cached insights for a domain (no AI call). */
    @GetMapping
    public ResponseEntity<InsightsResponse> getInsights(@RequestParam UUID domainId, @CurrentUser UUID userId) {
        return ResponseEntity.ok(insightsService.getInsights(userId, domainId));
    }

    /** Manually regenerate + cache insights for a domain (triggers the Gemini call). */
    @PostMapping("/refresh")
    public ResponseEntity<InsightsResponse> refreshInsights(@RequestParam UUID domainId, @CurrentUser UUID userId) {
        return ResponseEntity.ok(insightsService.generateAndStore(userId, domainId));
    }
}
