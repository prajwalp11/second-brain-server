package com.secondbrain.second_brain_server.service;

import com.secondbrain.second_brain_server.dto.response.WeeklyStatResponse;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.DomainMetricDefinition;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.repository.DomainMetricDefinitionRepository;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import com.secondbrain.second_brain_server.repository.SessionMetricValueRepository;
import com.secondbrain.second_brain_server.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class WeeklyStatService {

    private final SessionMetricValueRepository sessionMetricValueRepository;
    private final SessionLogRepository sessionLogRepository;
    private final DomainRepository domainRepository;
    private final DomainMetricDefinitionRepository metricDefinitionRepository;

    public List<WeeklyStatResponse> getWeeklyStats(UUID userId, LocalDate weekStart) {
        List<Domain> domains = domainRepository.findByUserId(userId);
        List<WeeklyStatResponse> allWeeklyStats = new ArrayList<>();

        for (Domain domain : domains) {
            allWeeklyStats.addAll(getWeeklyStatsForDomain(domain, weekStart));
        }
        return allWeeklyStats;
    }

    public List<WeeklyStatResponse> getWeeklyStatsForDomain(Domain domain, LocalDate weekStart) {
        List<WeeklyStatResponse> domainWeeklyStats = new ArrayList<>();
        LocalDate weekEnd = DateUtil.getWeekEnd(weekStart);

        List<DomainMetricDefinition> metrics = metricDefinitionRepository.findByDomainIdOrderByDisplayOrder(domain.getId());

        for (DomainMetricDefinition metric : metrics) {
            Double aggregatedValue = aggregateMetric(domain.getId(), metric, weekStart, weekEnd);
            Double target = resolveTarget(domain, metric.getMetricKey());

            domainWeeklyStats.add(WeeklyStatResponse.builder()
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

    /**
     * How a metric is summarized over the week, driven ONLY by its flags (domain-agnostic):
     * - Bounded/score metrics (maxValue set, e.g. a 0-100 score): AVERAGED + clamped.
     * - Higher-is-better open-ended (distance, reps, minutes): SUMMED (a weekly total makes sense).
     * - Lower-is-better open-ended (time, pace, SWOLF): AVERAGED — summing per-session times is
     *   meaningless; the weekly average reflects typical performance. (Best-ever is tracked by PRs.)
     */
    private Double aggregateMetric(UUID domainId, DomainMetricDefinition metric, LocalDate from, LocalDate to) {
        String metricKey = metric.getMetricKey();

        if (metric.getMaxValue() != null) {
            Double avg = sessionMetricValueRepository.avgMetricForPeriod(domainId, metricKey, from, to).orElse(0.0);
            double min = metric.getMinValue() != null ? metric.getMinValue() : 0.0;
            double max = metric.getMaxValue();
            return Math.max(min, Math.min(max, avg));
        }

        if (!metric.isHigherBetter()) {
            // Lower-is-better, open-ended: weekly average (summing times/SWOLF is nonsense).
            return sessionMetricValueRepository.avgMetricForPeriod(domainId, metricKey, from, to).orElse(0.0);
        }

        // Higher-is-better, open-ended cumulative quantity: sum.
        return sessionMetricValueRepository.sumMetricForPeriod(domainId, metricKey, from, to).orElse(0.0);
    }

    private Double resolveTarget(Domain domain, String metricKey) {
        return null;
    }
}
