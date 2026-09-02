package com.secondbrain.second_brain_server.scheduler;

import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.enums.DomainStatus;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.service.JobLogService;
import com.secondbrain.second_brain_server.service.StreakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class StreakCheckScheduler {

    private final DomainRepository domainRepository;
    private final StreakService streakService;
    private final JobLogService jobLogService;

    /**
     * Runs daily at midnight to recalculate streaks for all active domains.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void runMidnight() {
        jobLogService.run("StreakCheckScheduler", () -> {
            List<Domain> activeDomains = domainRepository.findAllByStatus(DomainStatus.ACTIVE);
            int updated = 0;
            for (Domain domain : activeDomains) {
                try {
                    streakService.recalculateStreak(domain);
                    updated++;
                } catch (Exception e) {
                    log.error("Failed to recalculate streak for domain {}: {}", domain.getId(), e.getMessage());
                }
            }
            return "Recalculated " + updated + " of " + activeDomains.size() + " active domains";
        });
    }
}
