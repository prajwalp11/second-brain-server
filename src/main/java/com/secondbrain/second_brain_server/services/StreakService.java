package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StreakService {

    private final DomainRepository domainRepository;
    private final SessionLogRepository sessionLogRepository;

    public void recalculateStreak(Domain domain) {
        // Placeholder
    }

    public List<Domain> getStreakAtRiskDomains(UUID userId) {
        // Placeholder
        return null;
    }

    private Integer computeStreak(List<LocalDate> logDates, String schedule, LocalDate today) {
        // Placeholder
        return null;
    }
}
