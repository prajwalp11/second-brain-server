package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.response.MilestoneDto;
import com.secondbrain.second_brain_server.dto.response.PersonalRecordDto;
import com.secondbrain.second_brain_server.dto.response.ProgressResponse;
import com.secondbrain.second_brain_server.dto.response.TimeSeriesPointDto;
import com.secondbrain.second_brain_server.repository.PersonalRecordRepository;
import com.secondbrain.second_brain_server.repository.SessionMetricValueRepository;
import com.secondbrain.second_brain_server.repository.SessionMetricValueRepository.TimeSeriesProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgressService {

    private final SessionMetricValueRepository sessionMetricValueRepository;
    private final MilestoneService milestoneService;
    private final PersonalRecordService prService; // Use prService to get DTOs
    private final DomainService domainService; // To assert ownership

    public ProgressResponse getProgress(UUID domainId, UUID userId, String metricKey, LocalDate from, LocalDate to) {
        domainService.assertOwnership(domainId, userId);

        List<TimeSeriesPointDto> timeSeries = buildTimeSeries(domainId, metricKey, from, to);
        List<MilestoneDto> milestones = milestoneService.getMilestonesForDomain(domainId, userId);
        List<PersonalRecordDto> prs = prService.getPrsForDomain(domainId);

        return ProgressResponse.builder()
                .domainId(domainId)
                .metricKey(metricKey)
                .timeSeries(timeSeries)
                .milestones(milestones)
                .prs(prs)
                .build();
    }

    private List<TimeSeriesPointDto> buildTimeSeries(UUID domainId, String metricKey, LocalDate from, LocalDate to) {
        return sessionMetricValueRepository.findMetricTimeSeries(domainId, metricKey, from, to).stream()
                .map(projection -> TimeSeriesPointDto.builder()
                        .date(projection.getDate())
                        .value(projection.getValue())
                        .build())
                .collect(Collectors.toList());
    }
}
