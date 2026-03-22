package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.request.CreateMilestoneRequest;
import com.secondbrain.second_brain_server.dto.response.MilestoneDto;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.DomainMetricDefinition;
import com.secondbrain.second_brain_server.entities.Milestone;
import com.secondbrain.second_brain_server.entities.User;
import com.secondbrain.second_brain_server.enums.MilestoneStatus;
import com.secondbrain.second_brain_server.exception.ForbiddenException;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.repository.DomainMetricDefinitionRepository;
import com.secondbrain.second_brain_server.repository.MilestoneRepository;
import com.secondbrain.second_brain_server.repository.PersonalRecordRepository;
import com.secondbrain.second_brain_server.repository.SessionMetricValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final PersonalRecordRepository prRepository;
    private final SessionMetricValueRepository sessionMetricValueRepository;
    private final DomainService domainService; // To assert ownership
    private final DomainMetricDefinitionRepository metricDefinitionRepository; // To check if metric is PR

    @Transactional
    public MilestoneDto createMilestone(UUID userId, CreateMilestoneRequest request) {
        Domain domain = domainService.assertOwnership(request.getDomainId(), userId);

        Milestone newMilestone = Milestone.builder()
                .domain(domain)
                .label(request.getLabel())
                .metricKey(request.getMetricKey())
                .targetValue(request.getTargetValue())
                .unit(request.getUnit())
                .status(MilestoneStatus.UPCOMING)
                .deadline(request.getDeadline())
                .aiGenerated(false) // User created
                .createdAt(LocalDateTime.now())
                .build();

        Milestone savedMilestone = milestoneRepository.save(newMilestone);
        updateProgress(domain.getId()); // Update progress immediately after creation
        return new MilestoneDto(savedMilestone);
    }

    public List<MilestoneDto> getMilestonesForDomain(UUID domainId, UUID userId) {
        domainService.assertOwnership(domainId, userId); // Ensure user owns domain
        return milestoneRepository.findByDomainId(domainId).stream()
                .map(MilestoneDto::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public MilestoneDto updateStatus(UUID milestoneId, UUID userId, MilestoneStatus status) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));

        // Assert ownership via domain
        domainService.assertOwnership(milestone.getDomain().getId(), userId);

        milestone.setStatus(status);
        if (status == MilestoneStatus.DONE) {
            milestone.setCompletedAt(LocalDate.now());
        } else {
            milestone.setCompletedAt(null); // Clear completed date if status changes from DONE
        }
        milestoneRepository.save(milestone);
        return new MilestoneDto(milestone);
    }

    @Transactional
    public void updateProgress(UUID domainId) {
        List<Milestone> milestones = milestoneRepository.findByDomainId(domainId);
        for (Milestone milestone : milestones) {
            Double currentValue = resolveCurrentValue(milestone);
            milestone.setCurrentValue(currentValue);
            checkAndComplete(milestone);
            milestoneRepository.save(milestone);
        }
    }

    public Optional<MilestoneDto> getNextMilestone(UUID domainId) {
        return milestoneRepository.findFirstByDomainIdAndStatusOrderByDeadlineAsc(domainId, MilestoneStatus.UPCOMING)
                .or(() -> milestoneRepository.findFirstByDomainIdAndStatusOrderByDeadlineAsc(domainId, MilestoneStatus.IN_PROGRESS))
                .map(MilestoneDto::new);
    }

    private Double resolveCurrentValue(Milestone milestone) {
        // Check if the metric is PR-trackable
        boolean isPrMetric = metricDefinitionRepository.findByDomainIdAndMetricKey(milestone.getDomain().getId(), milestone.getMetricKey())
                .map(DomainMetricDefinition::isPR)
                .orElse(false);

        if (isPrMetric) {
            return prRepository.findByDomainIdAndMetricKey(milestone.getDomain().getId(), milestone.getMetricKey())
                    .map(pr -> pr.getValue())
                    .orElse(0.0);
        } else {
            // For non-PR metrics, get the latest value or sum over a period if applicable
            // For simplicity, we'll get the max value recorded for this metric in the domain
            return sessionMetricValueRepository.findMaxValueForMetric(milestone.getDomain().getId(), milestone.getMetricKey())
                    .orElse(0.0);
        }
    }

    private boolean checkAndComplete(Milestone milestone) {
        if (milestone.getStatus() != MilestoneStatus.DONE && milestone.getCurrentValue() != null && milestone.getTargetValue() != null) {
            if (milestone.getCurrentValue() >= milestone.getTargetValue()) {
                milestone.setStatus(MilestoneStatus.DONE);
                milestone.setCompletedAt(milestone.getDomain().getLastLogDate() != null ? milestone.getDomain().getLastLogDate() : LocalDate.now());
                return true;
            }
        }
        return false;
    }
}
