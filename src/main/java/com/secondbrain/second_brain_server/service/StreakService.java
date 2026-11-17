package com.secondbrain.second_brain_server.service;

import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.enums.DomainStatus;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import com.secondbrain.second_brain_server.util.DateUtil;
import com.secondbrain.second_brain_server.util.StreakCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StreakService {

    private final DomainRepository domainRepository;
    private final SessionLogRepository sessionLogRepository;
    private final ClockService clockService;

    @Transactional
    public void recalculateStreak(Domain domain) {
        List<LocalDate> logDates = sessionLogRepository.findLogDatesByDomainId(domain.getId());
        Collections.sort(logDates);

        LocalDate today = clockService.today(domain.getUser());
        Integer currentStreak = StreakCalculator.compute(logDates, domain.getWeeklySchedule(), today);

        domain.setCurrentStreak(currentStreak);
        if (currentStreak > domain.getLongestStreak()) {
            domain.setLongestStreak(currentStreak);
        }
        domainRepository.save(domain);
    }

    @Transactional(readOnly = true)
    public List<Domain> getStreakAtRiskDomains(UUID userId) {
        return domainRepository.findByUserIdAndStatus(userId, DomainStatus.ACTIVE).stream()
                .filter(domain -> {
                    LocalDate today = clockService.today(domain.getUser());
                    LocalDate yesterday = today.minusDays(1);
                    if (domain.getLastLogDate() != null && domain.getLastLogDate().isEqual(yesterday)) {
                        boolean isScheduledToday = DateUtil.isScheduledDay(domain.getWeeklySchedule(), today);
                        if (isScheduledToday) {
                            return sessionLogRepository.countByDomainIdAndLogDateBetween(domain.getId(), today, today) == 0;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }
}
