package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.response.PersonalRecordDto;
import com.secondbrain.second_brain_server.entities.SessionLog;
import com.secondbrain.second_brain_server.repository.DomainMetricDefinitionRepository;
import com.secondbrain.second_brain_server.repository.PersonalRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PersonalRecordService {

    private final PersonalRecordRepository prRepository;
    private final DomainMetricDefinitionRepository metricDefinitionRepository;

    public List<PersonalRecordDto> checkAndUpdatePrs(SessionLog log, Map<String, Double> metrics) {
        // Placeholder
        return null;
    }

    public List<PersonalRecordDto> getPrsForDomain(UUID domainId) {
        // Placeholder
        return null;
    }

    public Page<PersonalRecordDto> getPrsForUser(UUID userId, Pageable pageable) {
        // Placeholder
        return null;
    }

    private boolean isBetter(Double newVal, Double oldVal, boolean isHigherBetter) {
        // Placeholder
        return false;
    }

    private PersonalRecordDto upsertPr(SessionLog log, String metricKey, Double newVal, String unit, Double prevVal) {
        // Placeholder
        return null;
    }
}
