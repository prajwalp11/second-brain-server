package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.response.ProgressResponse;
import com.secondbrain.second_brain_server.dto.response.TimeSeriesPointDto;
import com.secondbrain.second_brain_server.repository.PersonalRecordRepository;
import com.secondbrain.second_brain_server.repository.SessionMetricValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProgressService {

    private final SessionMetricValueRepository sessionMetricValueRepository;
    private final MilestoneService milestoneService;
    private final PersonalRecordRepository prRepository;

    public ProgressResponse getProgress(UUID domainId, UUID userId, String metricKey, LocalDate from, LocalDate to) {
        // Placeholder
        return null;
    }

    private List<TimeSeriesPointDto> buildTimeSeries(UUID domainId, String metricKey, LocalDate from, LocalDate to) {
        // Placeholder
        return null;
    }
}
