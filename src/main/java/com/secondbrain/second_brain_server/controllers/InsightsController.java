package com.secondbrain.second_brain_server.controllers;

import com.secondbrain.second_brain_server.dto.response.InsightsResponse;
import com.secondbrain.second_brain_server.security.CurrentUser;
import com.secondbrain.second_brain_server.services.InsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final InsightsService insightsService;

    @GetMapping
    public ResponseEntity<InsightsResponse> getInsights(@CurrentUser UUID userId) {
        return ResponseEntity.ok(insightsService.getInsights(userId));
    }
}
