package com.secondbrain.second_brain_server.scheduler;

import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.enums.DomainStatus;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.service.JobLogService;
import com.secondbrain.second_brain_server.service.MilestoneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Daily top-up: for each ACTIVE domain that has completed all its milestones
 * (none active, but >=1 DONE), generate ONE progressive next milestone via AI.
 * Keeps motivated users from running out of goals.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MilestoneTopUpScheduler {

    private final DomainRepository domainRepository;
    private final MilestoneService milestoneService;
    private final JobLogService jobLogService;

    @Scheduled(cron = "0 0 2 * * ?") // daily at 2 AM (distinct from midnight / 5 AM jobs)
    public void topUpMilestones() {
        jobLogService.run("MilestoneTopUpScheduler", () -> {
            List<Domain> activeDomains = domainRepository.findAllByStatus(DomainStatus.ACTIVE);
            int generated = 0;
            for (Domain domain : activeDomains) {
                try {
                    if (milestoneService.generateNextMilestone(domain)) {
                        generated++;
                    }
                } catch (Exception e) {
                    log.error("Milestone top-up failed for domain {}: {}", domain.getId(), e.getMessage());
                }
            }
            return "Generated " + generated + " next milestones across " + activeDomains.size() + " active domains";
        });
    }
}
