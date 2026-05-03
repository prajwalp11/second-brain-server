package com.secondbrain.second_brain_server.controllers;

import com.secondbrain.second_brain_server.dto.response.InsightsResponse;
import com.secondbrain.second_brain_server.security.CurrentUser;
import com.secondbrain.second_brain_server.services.InsightsService;
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

    @GetMapping
    public ResponseEntity<InsightsResponse> getInsights(@CurrentUser UUID userId) {
        return ResponseEntity.ok(insightsService.getInsights(userId));
    }
}
