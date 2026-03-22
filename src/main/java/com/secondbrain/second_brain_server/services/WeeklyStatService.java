package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.response.WeeklyStatDto;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.repository.DomainMetricDefinitionRepository;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import com.secondbrain.second_brain_server.repository.SessionMetricValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WeeklyStatService {

    private final SessionMetricValueRepository sessionMetricValueRepository;
    private final SessionLogRepository sessionLogRepository;
    private final DomainRepository domainRepository;
    private final DomainMetricDefinitionRepository metricDefinitionRepository;

    public List<WeeklyStatDto> getWeeklyStats(UUID userId, LocalDate weekStart) {
        // Placeholder
        return null;
    }

    public WeeklyStatDto getWeeklyStatForDomain(UUID domainId, LocalDate weekStart) {
        // Placeholder
        return null;
    }

    private Double aggregateMetric(UUID domainId, String metricKey, LocalDate from, LocalDate to) {
        // Placeholder
        return null;
    }

    private Double resolveTarget(Domain domain, String metricKey) {
        // Placeholder
        return null;
    }
}
