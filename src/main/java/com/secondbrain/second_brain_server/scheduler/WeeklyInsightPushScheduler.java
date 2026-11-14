package com.secondbrain.second_brain_server.scheduler;

import com.secondbrain.second_brain_server.repository.UserRepository;
import com.secondbrain.second_brain_server.service.JobLogService;
import com.secondbrain.second_brain_server.service.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Weekly "how you did this week" push — ONE per user (not per domain). Runs Sunday evening; the
 * actual recap lives on the Progress tab (the notification deep-links there). Enqueues through
 * PushService so prefs + per-type cooldown + the one-pending guard all apply. The insight data
 * itself is already cached daily by InsightsRefreshScheduler, so nothing is generated here.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WeeklyInsightPushScheduler {

    private final UserRepository userRepository;
    private final PushService pushService;
    private final JobLogService jobLogService;

    @Scheduled(cron = "0 0 18 ? * SUN") // Sundays at 6 PM (server-local)
    public void sendWeeklyInsights() {
        jobLogService.run("WeeklyInsightPushScheduler", () -> {
            List<UUID> ids = userRepository.findAllIds();
            int queued = 0;
            for (UUID userId : ids) {
                try {
                    pushService.enqueueWeeklyInsight(
                            userId,
                            "Your week in review",
                            "See how your training went this week.");
                    queued++;
                } catch (Exception e) {
                    log.error("Weekly insight push failed for user {}: {}", userId, e.getMessage());
                }
            }
            return "Evaluated weekly insight for " + ids.size()
                    + " users (" + queued + " without error; prefs/cooldown may skip some)";
        });
    }
}
