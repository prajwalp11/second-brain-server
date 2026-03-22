package com.secondbrain.second_brain_server.scheduler;

import com.secondbrain.second_brain_server.repository.NotificationPreferenceRepository;
import com.secondbrain.second_brain_server.service.ai.AiInsightService;
import com.secondbrain.second_brain_server.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class WeeklyReviewScheduler {

    private final NotificationPreferenceRepository notificationPrefRepository;
    private final NotificationService notificationService;
    private final AiInsightService aiInsightService;

    @Scheduled(cron = "0 0 1 * * SUN") // Run every Sunday at 1 AM
    public void runWeekly() {
        notificationPrefRepository.findAllByWeeklyReviewEnabledTrueAndWeeklyReviewDayOfWeek(DayOfWeek.SUNDAY.getValue())
                .forEach(pref -> {
                    // Placeholder for generating and sending weekly review
                    notificationService.sendWeeklyReview(pref.getUser().getId());
                });
    }
}
