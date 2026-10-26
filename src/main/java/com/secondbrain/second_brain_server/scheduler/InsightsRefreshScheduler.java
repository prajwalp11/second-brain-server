package com.secondbrain.second_brain_server.scheduler;

import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.enums.DomainStatus;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.service.InsightsService;
import com.secondbrain.second_brain_server.service.JobLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Daily regeneration of cached per-domain insights (domain_insights) so the Progress tab
 * never calls Gemini on load. Runs 3 AM (distinct from midnight / 2 AM / 5 AM jobs).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InsightsRefreshScheduler {

    private final DomainRepository domainRepository;
    private final InsightsService insightsService;
    private final JobLogService jobLogService;

    @Scheduled(cron = "0 0 3 * * ?") // daily at 3 AM
    public void refreshInsights() {
        jobLogService.run("InsightsRefreshScheduler", () -> {
            List<Domain> activeDomains = domainRepository.findAllByStatus(DomainStatus.ACTIVE);
            int done = 0;
            for (Domain domain : activeDomains) {
                try {
                    insightsService.generateAndStore(domain.getUser().getId(), domain.getId());
                    done++;
                } catch (Exception e) {
                    log.error("Insights refresh failed for domain {}: {}", domain.getId(), e.getMessage());
                }
            }
            return "Refreshed insights for " + done + " of " + activeDomains.size() + " active domains";
        });
    }
}
