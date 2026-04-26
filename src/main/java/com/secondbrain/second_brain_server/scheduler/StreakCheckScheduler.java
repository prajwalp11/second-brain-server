package com.secondbrain.second_brain_server.scheduler;

import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.repository.DomainRepository;

import com.secondbrain.second_brain_server.services.StreakService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StreakCheckScheduler {

    private final DomainRepository domainRepository;
    private final StreakService streakService;
    

    @Scheduled(cron = "0 0 0 * * ?") // Run daily at midnight
    public void runMidnight() {
        List<Domain> domainsAtRisk = streakService.getStreakAtRiskDomains(null); // userId is null for all users
        
    }
}
