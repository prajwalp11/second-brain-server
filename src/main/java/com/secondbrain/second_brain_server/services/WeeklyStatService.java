package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.response.WeeklyStatDto;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.DomainMetricDefinition;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.repository.DomainMetricDefinitionRepository;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import com.secondbrain.second_brain_server.repository.SessionMetricValueRepository;
import com.secondbrain.second_brain_server.util.DateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WeeklyStatService {

    private final SessionMetricValueRepository sessionMetricValueRepository;
    private final SessionLogRepository sessionLogRepository;
    private final DomainRepository domainRepository;
    private final DomainMetricDefinitionRepository metricDefinitionRepository;

    public List<WeeklyStatDto> getWeeklyStats(UUID userId, LocalDate weekStart) {
        List<Domain> domains = domainRepository.findByUserId(userId);
        List<WeeklyStatDto> allWeeklyStats = new ArrayList<>();

        for (Domain domain : domains) {
            allWeeklyStats.addAll(getWeeklyStatsForDomain(domain, weekStart));
        }
        return allWeeklyStats;
    }

    public List<WeeklyStatDto> getWeeklyStatsForDomain(Domain domain, LocalDate weekStart) {
        List<WeeklyStatDto> domainWeeklyStats = new ArrayList<>();
        LocalDate weekEnd = DateUtil.getWeekEnd(weekStart);

        List<DomainMetricDefinition> metrics = metricDefinitionRepository.findByDomainIdOrderByDisplayOrder(domain.getId());

        for (DomainMetricDefinition metric : metrics) {
            Double aggregatedValue = aggregateMetric(domain.getId(), metric.getMetricKey(), weekStart, weekEnd);
            Double target = resolveTarget(domain, metric.getMetricKey()); // Placeholder for target resolution

            domainWeeklyStats.add(WeeklyStatDto.builder()
                    .domainId(domain.getId())
                    .domainName(domain.getCustomName() != null ? domain.getCustomName() : domain.getDomainType().name())
                    .metricKey(metric.getMetricKey())
                    .label(metric.getLabel())
                    .value(aggregatedValue != null ? aggregatedValue : 0.0)
                    .target(target)
                    .unit(metric.getUnit())
                    .build());
        }
        return domainWeeklyStats;
    }

    private Double aggregateMetric(UUID domainId, String metricKey, LocalDate from, LocalDate to) {
        return sessionMetricValueRepository.sumMetricForPeriod(domainId, metricKey, from, to).orElse(0.0);
    }

    private Double resolveTarget(Domain domain, String metricKey) {
        // Placeholder: LLD doesn't specify how targets are stored/resolved.
        // This would typically involve parsing domain.getWeeklySchedule() or a dedicated target field.
        return null; // No target for now
    }
}
