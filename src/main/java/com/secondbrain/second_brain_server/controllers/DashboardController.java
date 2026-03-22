package com.secondbrain.second_brain_server.controllers;

import com.secondbrain.second_brain_server.dto.response.DashboardResponse;
import com.secondbrain.second_brain_server.dto.response.WeeklyStatDto;
import com.secondbrain.second_brain_server.security.CurrentUser;
import com.secondbrain.second_brain_server.services.DashboardService;
import com.secondbrain.second_brain_server.services.WeeklyStatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final WeeklyStatService weeklyStatService;

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard(@CurrentUser UUID userId) {
        return ResponseEntity.ok(dashboardService.getDashboard(userId, LocalDate.now()));
    }

    @GetMapping("/weekly-snapshot")
    public ResponseEntity<List<WeeklyStatDto>> getWeeklySnapshot(@RequestParam LocalDate weekStart, @CurrentUser UUID userId) {
        return ResponseEntity.ok(weeklyStatService.getWeeklyStats(userId, weekStart));
    }
}
