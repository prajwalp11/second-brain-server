package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.enums.DomainStatus;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import com.secondbrain.second_brain_server.util.DateUtil;
import com.secondbrain.second_brain_server.util.StreakCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StreakService {

    private final DomainRepository domainRepository;
    private final SessionLogRepository sessionLogRepository;

    @Transactional
    public void recalculateStreak(Domain domain) {
        List<LocalDate> logDates = sessionLogRepository.findLogDatesByDomainId(domain.getId());
        Collections.sort(logDates); // Ensure dates are sorted for StreakCalculator

        LocalDate today = LocalDate.now();
        Integer currentStreak = StreakCalculator.compute(logDates, domain.getWeeklySchedule(), today);

        domain.setCurrentStreak(currentStreak);
        if (currentStreak > domain.getLongestStreak()) {
            domain.setLongestStreak(currentStreak);
        }
        domainRepository.save(domain);
    }

    public List<Domain> getStreakAtRiskDomains(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        return domainRepository.findByUserIdAndStatus(userId, DomainStatus.ACTIVE).stream()
                .filter(domain -> {
                    // A streak is at risk if the last log was yesterday and no log today,
                    // AND the domain is scheduled for today.
                    if (domain.getLastLogDate() != null && domain.getLastLogDate().isEqual(yesterday)) {
                        boolean isScheduledToday = DateUtil.isScheduledDay(domain.getWeeklySchedule(), today);
                        if (isScheduledToday) {
                            // Check if there's actually no log today
                            return sessionLogRepository.countByDomainIdAndLogDateBetween(domain.getId(), today, today) == 0;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    // The computeStreak method is now delegated to StreakCalculator utility class
}
