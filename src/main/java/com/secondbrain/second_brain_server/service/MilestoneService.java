package com.secondbrain.second_brain_server.service;

import com.secondbrain.second_brain_server.dto.request.CreateMilestoneRequest;
import com.secondbrain.second_brain_server.dto.response.MilestoneResponse;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final PersonalRecordRepository prRepository;
    private final SessionMetricValueRepository sessionMetricValueRepository;
    private final DomainService domainService;
    private final DomainMetricDefinitionRepository metricDefinitionRepository;
    private final com.secondbrain.second_brain_server.service.ai.AiSystemGeneratorService aiSystemGeneratorService;

    @Transactional
    public MilestoneResponse createMilestone(UUID userId, CreateMilestoneRequest request) {
        log.info("Creating milestone for user: {}, label: {}", userId, request.getLabel());
        Domain domain = domainService.assertOwnership(request.getDomainId(), userId);

        Milestone newMilestone = Milestone.builder()
                .domain(domain)
                .label(request.getLabel())
                .metricKey(request.getMetricKey())
                .targetValue(request.getTargetValue())
                .unit(request.getUnit())
                .status(MilestoneStatus.UPCOMING)
                .deadline(request.getDeadline())
                .aiGenerated(false)
                .createdAt(LocalDateTime.now())
                .build();

        Milestone savedMilestone = milestoneRepository.save(newMilestone);
        updateProgress(domain.getId());
        return savedMilestone.toResponse();
    }

    public List<MilestoneResponse> getMilestonesForDomain(UUID domainId, UUID userId) {
        domainService.assertOwnership(domainId, userId);
        return milestoneRepository.findByDomainId(domainId).stream()
                .map(Milestone::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public MilestoneResponse updateStatus(UUID milestoneId, UUID userId, MilestoneStatus status) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));

        domainService.assertOwnership(milestone.getDomain().getId(), userId);

        milestone.setStatus(status);
        if (status == MilestoneStatus.DONE) {
            milestone.setCompletedAt(LocalDateTime.now());
        } else {
            milestone.setCompletedAt(null);
        }
        milestoneRepository.save(milestone);
        return milestone.toResponse();
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

    /**
     * If a domain has completed all its active milestones (none UPCOMING/IN_PROGRESS)
     * but has at least one DONE milestone, generate ONE progressive next milestone via AI.
     * Guard: skips domains with no DONE history (i.e. milestones cleared deliberately).
     *
     * @return true if a new milestone was created.
     */
    @Transactional
    public boolean generateNextMilestone(Domain domain) {
        List<Milestone> milestones = milestoneRepository.findByDomainId(domain.getId());

        boolean hasActive = milestones.stream().anyMatch(m ->
                m.getStatus() == MilestoneStatus.UPCOMING || m.getStatus() == MilestoneStatus.IN_PROGRESS);
        if (hasActive) {
            return false; // still has something to work toward
        }

        // Guard: only top up domains the user COMPLETED their way to empty (>=1 DONE),
        // not ones they cleared on purpose.
        List<Milestone> done = milestones.stream()
                .filter(m -> m.getStatus() == MilestoneStatus.DONE)
                .collect(Collectors.toList());
        if (done.isEmpty()) {
            return false;
        }

        Milestone lastCompleted = done.stream()
                .max((a, b) -> {
                    LocalDateTime ca = a.getCompletedAt() != null ? a.getCompletedAt() : a.getCreatedAt();
                    LocalDateTime cb = b.getCompletedAt() != null ? b.getCompletedAt() : b.getCreatedAt();
                    return ca.compareTo(cb);
                })
                .orElse(null);

        List<DomainMetricDefinition> metrics =
                metricDefinitionRepository.findByDomainIdOrderByDisplayOrder(domain.getId());

        MilestoneResponse generated = aiSystemGeneratorService.generateNextMilestone(domain, metrics, lastCompleted);
        if (generated == null || generated.getMetricKey() == null || generated.getTargetValue() == null) {
            return false; // AI failed or returned unusable data — skip, try again next run
        }

        Milestone next = Milestone.builder()
                .domain(domain)
                .label(generated.getLabel())
                .metricKey(generated.getMetricKey())
                .targetValue(generated.getTargetValue())
                .unit(generated.getUnit())
                .status(MilestoneStatus.UPCOMING)
                .deadline(generated.getDeadline())
                .aiGenerated(true)
                .createdAt(LocalDateTime.now())
                .build();
        milestoneRepository.save(next);
        updateProgress(domain.getId());
        log.info("Auto-generated next milestone '{}' for domain {}", generated.getLabel(), domain.getId());
        return true;
    }

    public Optional<MilestoneResponse> getNextMilestone(UUID domainId) {
        return milestoneRepository.findFirstByDomainIdAndStatusOrderByDeadlineAsc(domainId, MilestoneStatus.UPCOMING)
                .or(() -> milestoneRepository.findFirstByDomainIdAndStatusOrderByDeadlineAsc(domainId, MilestoneStatus.IN_PROGRESS))
                .map(Milestone::toResponse);
    }

    private Double resolveCurrentValue(Milestone milestone) {
        boolean isPrMetric = metricDefinitionRepository.findByDomainIdAndMetricKey(milestone.getDomain().getId(), milestone.getMetricKey())
                .map(DomainMetricDefinition::isPR)
                .orElse(false);

        if (isPrMetric) {
            return prRepository.findByDomainIdAndMetricKey(milestone.getDomain().getId(), milestone.getMetricKey())
                    .map(pr -> pr.getValue())
                    .orElse(0.0);
        } else {
            return sessionMetricValueRepository.findMaxValueForMetric(milestone.getDomain().getId(), milestone.getMetricKey())
                    .orElse(0.0);
        }
    }

    private boolean checkAndComplete(Milestone milestone) {
        if (milestone.getStatus() != MilestoneStatus.DONE && milestone.getCurrentValue() != null && milestone.getTargetValue() != null) {
            if (milestone.getCurrentValue() >= milestone.getTargetValue()) {
                milestone.setStatus(MilestoneStatus.DONE);
                milestone.setCompletedAt(milestone.getDomain().getLastLogDate() != null ? milestone.getDomain().getLastLogDate().atStartOfDay() : LocalDateTime.now());
                return true;
            }
        }
        return false;
    }
}
