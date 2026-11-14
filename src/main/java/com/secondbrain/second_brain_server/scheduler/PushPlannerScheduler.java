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
 * PLANNER (every 5 min): for each user, enqueues at most one PushOutbox row per eligible type
 * (prefs + time window + condition), skipping types in cooldown or with a PENDING row. Decides
 * who/what only — it never calls Expo. Wrapped in JobLogService for audit.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PushPlannerScheduler {

    private final UserRepository userRepository;
    private final PushService pushService;
    private final JobLogService jobLogService;

    @Scheduled(cron = "0 */5 * * * ?") // every 5 minutes
    public void plan() {
        jobLogService.run("PushPlannerScheduler", () -> {
            List<UUID> ids = userRepository.findAllIds();
            int queued = 0;
            for (UUID userId : ids) {
                try {
                    queued += pushService.planForUser(userId);
                } catch (Exception e) {
                    log.error("Push planning failed for user {}: {}", userId, e.getMessage());
                }
            }
            return "Queued " + queued + " notifications across " + ids.size() + " users";
        });
    }
}
