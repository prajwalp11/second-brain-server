package com.secondbrain.second_brain_server.scheduler;


import com.secondbrain.second_brain_server.service.ai.AiInsightService;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class WeeklyReviewScheduler {

    
    private final AiInsightService aiInsightService;

    @Scheduled(cron = "0 0 1 * * SUN") // Run every Sunday at 1 AM
    public void runWeekly() {
        
    }
}
