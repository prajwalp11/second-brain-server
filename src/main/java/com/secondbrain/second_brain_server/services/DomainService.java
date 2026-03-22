package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.request.CreateDomainRequest;
import com.secondbrain.second_brain_server.dto.request.UpdateDomainRequest;
import com.secondbrain.second_brain_server.dto.response.DomainDto;
import com.secondbrain.second_brain_server.dto.response.GeneratedSystemDto;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.DomainMetricDefinition;
import com.secondbrain.second_brain_server.entities.Milestone;
import com.secondbrain.second_brain_server.entities.Task;
import com.secondbrain.second_brain_server.enums.DomainStatus;
import com.secondbrain.second_brain_server.enums.DomainType;
import com.secondbrain.second_brain_server.enums.MilestoneStatus;
import com.secondbrain.second_brain_server.enums.SkillLevel;
import com.secondbrain.second_brain_server.enums.TaskStatus;
import com.secondbrain.second_brain_server.exception.DomainAlreadyExistsException;
import com.secondbrain.second_brain_server.exception.ForbiddenException;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.exception.ValidationException;
import com.secondbrain.second_brain_server.repository.DomainMetricDefinitionRepository;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.MilestoneRepository;
import com.secondbrain.second_brain_server.repository.TaskRepository;
import com.secondbrain.second_brain_server.service.ai.AiSystemGeneratorService;
import com.secondbrain.second_brain_server.util.MetricValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DomainService {

    private final DomainRepository domainRepository;
    private final DomainMetricDefinitionRepository metricDefinitionRepository;
    private final MilestoneRepository milestoneRepository;
    private final TaskRepository taskRepository;
    private final AiSystemGeneratorService aiSystemGeneratorService;

    public List<DomainDto> getDomainsForUser(UUID userId) {
        return domainRepository.findByUserId(userId).stream()
                .map(Domain::toDto)
                .collect(Collectors.toList());
    }

    public DomainDto getDomainById(UUID domainId, UUID userId) {
        Domain domain = assertOwnership(domainId, userId);
        return domain.toDto();
    }

    @Transactional
    public DomainDto createDomain(UUID userId, CreateDomainRequest request) {
        if (domainRepository.existsByUserIdAndDomainType(userId, request.getDomainType())) {
            throw new DomainAlreadyExistsException(request.getDomainType());
        }

        Domain newDomain = Domain.builder()
                .user(new com.secondbrain.second_brain_server.entities.User(userId))
                .domainType(request.getDomainType())
                .customName(request.getCustomName())
                .skillLevel(request.getSkillLevel())
                .status(DomainStatus.ACTIVE)
                .currentStreak(0)
                .longestStreak(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .linkedResourceUrl(request.getLinkedResourceUrl())
                .build();

        Domain savedDomain = domainRepository.save(newDomain);

        // Generate and apply AI system
        GeneratedSystemDto generatedSystem = aiSystemGeneratorService.generateSystem(
                request.getDomainType(), request.getSkillLevel(), request.getLinkedResourceUrl());
        applyGeneratedSystem(savedDomain, generatedSystem);

        return savedDomain.toDto();
    }

    @Transactional
    public DomainDto updateDomain(UUID domainId, UUID userId, UpdateDomainRequest request) {
        Domain domain = assertOwnership(domainId, userId);

        Optional.ofNullable(request.getCustomName()).ifPresent(domain::setCustomName);
        Optional.ofNullable(request.getSkillLevel()).ifPresent(domain::setSkillLevel);
        Optional.ofNullable(request.getPlanDescription()).ifPresent(domain::setPlanDescription);
        Optional.ofNullable(request.getWeeklySchedule()).ifPresent(domain::setWeeklySchedule);
        Optional.ofNullable(request.getLinkedResourceUrl()).ifPresent(domain::setLinkedResourceUrl);
        Optional.ofNullable(request.getStatus()).ifPresent(domain::setStatus);
        domain.setUpdatedAt(LocalDateTime.now());

        return domainRepository.save(domain).toDto();
    }

    @Transactional
    public void pauseDomain(UUID domainId, UUID userId) {
        Domain domain = assertOwnership(domainId, userId);
        domain.setStatus(DomainStatus.PAUSED);
        domain.setUpdatedAt(LocalDateTime.now());
        domainRepository.save(domain);
    }

    @Transactional
    public void archiveDomain(UUID domainId, UUID userId) {
        Domain domain = assertOwnership(domainId, userId);
        domain.setStatus(DomainStatus.ARCHIVED);
        domain.setUpdatedAt(LocalDateTime.now());
        domainRepository.save(domain);
    }

    @Transactional
    public GeneratedSystemDto generateAndApplySystem(UUID domainId, UUID userId) {
        Domain domain = assertOwnership(domainId, userId);
        GeneratedSystemDto generatedSystem = aiSystemGeneratorService.regenerateSystem(domain);
        applyGeneratedSystem(domain, generatedSystem);
        return generatedSystem;
    }

    @Transactional
    public void updateStreakForDomain(Domain domain, LocalDate logDate) {
        // This is a simplified placeholder. Full streak calculation requires SessionLogService and StreakCalculator.
        // It will be properly implemented when SessionLogService is built.
        if (domain.getLastLogDate() == null || logDate.isAfter(domain.getLastLogDate())) {
            domain.setLastLogDate(logDate);
            domain.setCurrentStreak(domain.getCurrentStreak() + 1);
            if (domain.getCurrentStreak() > domain.getLongestStreak()) {
                domain.setLongestStreak(domain.getCurrentStreak());
            }
        } else if (logDate.isBefore(domain.getLastLogDate())) {
            // Logged for a past date, no change to current streak
        } else { // logDate is same as lastLogDate, means multiple logs on same day, no streak change
            // No change
        }
        domain.setUpdatedAt(LocalDateTime.now());
        domainRepository.save(domain);
    }

    public void validateMetricKeys(UUID domainId, Set<String> submittedKeys) {
        List<DomainMetricDefinition> definedMetrics =
                metricDefinitionRepository.findByDomainIdOrderByDisplayOrder(domainId);

        MetricValidator.validateKeys(submittedKeys, definedMetrics);
    }

    private void applyGeneratedSystem(Domain domain, GeneratedSystemDto generated) {
        // Update domain details
        Optional.ofNullable(generated.getPlanDescription()).ifPresent(domain::setPlanDescription);
        Optional.ofNullable(generated.getWeeklySchedule()).ifPresent(domain::setWeeklySchedule);
        Optional.ofNullable(generated.getLinkedResourceUrl()).ifPresent(domain::setLinkedResourceUrl);
        Optional.ofNullable(generated.getLinkedResourceTitle()).ifPresent(domain::setLinkedResourceTitle);
        domain.setUpdatedAt(LocalDateTime.now());
        domainRepository.save(domain);

        // Delete existing metrics, milestones, tasks for regeneration
        metricDefinitionRepository.deleteByDomainId(domain.getId());
        milestoneRepository.findByDomainId(domain.getId()).forEach(milestoneRepository::delete);
        taskRepository.findByDomainId(domain.getId()).forEach(taskRepository::delete);


        // Save generated metrics
        if (generated.getMetrics() != null) {
            List<DomainMetricDefinition> metrics = generated.getMetrics().stream()
                    .map(dto -> DomainMetricDefinition.builder()
                            .domain(domain)
                            .metricKey(dto.getMetricKey())
                            .label(dto.getLabel())
                            .unit(dto.getUnit())
                            .isTrackedPerSession(dto.isTrackedPerSession())
                            .isPR(dto.isPR())
                            .isHigherBetter(dto.isHigherBetter())
                            .displayOrder(dto.getDisplayOrder())
                            .build())
                    .collect(Collectors.toList());
            metricDefinitionRepository.saveAll(metrics);
        }

        // Save generated milestones
        if (generated.getMilestones() != null) {
            List<Milestone> milestones = generated.getMilestones().stream()
                    .map(dto -> Milestone.builder()
                            .domain(domain)
                            .label(dto.getLabel())
                            .metricKey(dto.getMetricKey())
                            .targetValue(dto.getTargetValue())
                            .unit(dto.getUnit())
                            .status(MilestoneStatus.UPCOMING)
                            .deadline(dto.getDeadline())
                            .aiGenerated(true)
                            .createdAt(LocalDateTime.now())
                            .build())
                    .collect(Collectors.toList());
            milestoneRepository.saveAll(milestones);
        }

        // Save generated tasks
        if (generated.getTasks() != null) {
            List<Task> tasks = generated.getTasks().stream()
                    .map(dto -> Task.builder()
                            .user(domain.getUser())
                            .domain(domain)
                            .title(dto.getTitle())
                            .description(dto.getDescription())
                            .status(TaskStatus.TODO)
                            .dueDate(dto.getDueDate())
                            .aiGenerated(true)
                            .createdAt(LocalDateTime.now())
                            .build())
                    .collect(Collectors.toList());
            taskRepository.saveAll(tasks);
        }
    }

    public Domain assertOwnership(UUID domainId, UUID userId) {
        return domainRepository.findById(domainId)
                .orElseThrow(() -> new ResourceNotFoundException("Domain", domainId))
                .checkOwnership(userId);
    }
}


