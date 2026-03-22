package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.response.PersonalRecordDto;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.DomainMetricDefinition;
import com.secondbrain.second_brain_server.entities.PersonalRecord;
import com.secondbrain.second_brain_server.entities.SessionLog;
import com.secondbrain.second_brain_server.entities.User;
import com.secondbrain.second_brain_server.repository.DomainMetricDefinitionRepository;
import com.secondbrain.second_brain_server.repository.PersonalRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PersonalRecordService {

    private final PersonalRecordRepository prRepository;
    private final DomainMetricDefinitionRepository metricDefinitionRepository;

    @Transactional
    public List<PersonalRecordDto> checkAndUpdatePrs(SessionLog log, Map<String, Double> metrics) {
        List<PersonalRecordDto> newPrs = new ArrayList<>();
        List<DomainMetricDefinition> prTrackableMetrics = metricDefinitionRepository.findPrMetricsByDomainId(log.getDomain().getId());

        for (DomainMetricDefinition metricDef : prTrackableMetrics) {
            if (metrics.containsKey(metricDef.getMetricKey())) {
                Double newMetricValue = metrics.get(metricDef.getMetricKey());
                Optional<PersonalRecord> existingPrOpt = prRepository.findByDomainIdAndMetricKey(log.getDomain().getId(), metricDef.getMetricKey());

                Double oldMetricValue = existingPrOpt.map(PersonalRecord::getValue).orElse(null);

                if (oldMetricValue == null || isBetter(newMetricValue, oldMetricValue, metricDef.isHigherBetter())) {
                    PersonalRecordDto prDto = upsertPr(log, metricDef.getMetricKey(), newMetricValue, metricDef.getUnit(), oldMetricValue);
                    // Set label for DTO
                    prDto.setLabel(metricDef.getLabel());
                    newPrs.add(prDto);
                }
            }
        }
        return newPrs;
    }

    public List<PersonalRecordDto> getPrsForDomain(UUID domainId) {
        return prRepository.findByDomainId(domainId).stream()
                .map(pr -> {
                    PersonalRecordDto dto = pr.toDto();
                    // Populate label from metric definition
                    metricDefinitionRepository.findByDomainIdAndMetricKey(domainId, pr.getMetricKey())
                            .ifPresent(def -> dto.setLabel(def.getLabel()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public Page<PersonalRecordDto> getPrsForUser(UUID userId, Pageable pageable) {
        return prRepository.findByUserIdOrderByAchievedAtDesc(userId, pageable)
                .map(pr -> {
                    PersonalRecordDto dto = pr.toDto();
                    // Populate label from metric definition (requires fetching domainId from PR)
                    if (pr.getDomain() != null) {
                        metricDefinitionRepository.findByDomainIdAndMetricKey(pr.getDomain().getId(), pr.getMetricKey())
                                .ifPresent(def -> dto.setLabel(def.getLabel()));
                    }
                    return dto;
                });
    }

    private boolean isBetter(Double newVal, Double oldVal, boolean isHigherBetter) {
        if (isHigherBetter) {
            return newVal > oldVal;
        } else {
            return newVal < oldVal;
        }
    }

    private PersonalRecordDto upsertPr(SessionLog log, String metricKey, Double newVal, String unit, Double prevVal) {
        Optional<PersonalRecord> existingPrOpt = prRepository.findByDomainIdAndMetricKey(log.getDomain().getId(), metricKey);
        PersonalRecord pr;

        if (existingPrOpt.isPresent()) {
            pr = existingPrOpt.get();
            pr.setPreviousValue(pr.getValue()); // Store current value as previous
            pr.setValue(newVal);
            pr.setAchievedAt(log.getLogDate());
            pr.setSessionLog(log);
        } else {
            pr = PersonalRecord.builder()
                    .user(log.getUser())
                    .domain(log.getDomain())
                    .sessionLog(log)
                    .metricKey(metricKey)
                    .value(newVal)
                    .unit(unit)
                    .achievedAt(log.getLogDate())
                    .previousValue(prevVal) // For first PR, previousValue is null or 0
                    .build();
        }
        return prRepository.save(pr).toDto();
    }
}
