package com.secondbrain.second_brain_server.service;

import com.secondbrain.second_brain_server.dto.request.CreateDomainRequest;
import com.secondbrain.second_brain_server.dto.request.UpdateDomainRequest;
import com.secondbrain.second_brain_server.dto.response.DomainResponse;
import com.secondbrain.second_brain_server.dto.response.GeneratedSystemResponse;
import com.secondbrain.second_brain_server.dto.response.TimeSeriesPointResponse;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.DomainMetricDefinition;
import com.secondbrain.second_brain_server.entities.Milestone;
import com.secondbrain.second_brain_server.entities.SessionLog;
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
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import com.secondbrain.second_brain_server.repository.TaskRepository;
import com.secondbrain.second_brain_server.service.ai.AiSystemGeneratorService;
import com.secondbrain.second_brain_server.util.MetricValidator;
import com.secondbrain.second_brain_server.util.StreakCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DomainService {

    private final DomainRepository domainRepository;
    private final DomainMetricDefinitionRepository metricDefinitionRepository;
    private final MilestoneRepository milestoneRepository;
    private final SessionLogRepository sessionLogRepository;
    private final TaskRepository taskRepository;
    private final AiSystemGeneratorService aiSystemGeneratorService;
    private final com.secondbrain.second_brain_server.external.YouTubeService youTubeService;

    public List<DomainResponse> getDomainsForUser(UUID userId) {
        return domainRepository.findByUserId(userId).stream()
                .map(Domain::toResponse)
                .collect(Collectors.toList());
    }

    public DomainResponse getDomainById(UUID domainId, UUID userId) {
        Domain domain = assertOwnership(domainId, userId);
        return domain.toResponse();
    }

    @Transactional
    public DomainResponse createDomain(UUID userId, CreateDomainRequest request) {
        log.info("Creating domain for user: {}, type: {}", userId, request.getDomainType());

        if (request.getDomainType() == DomainType.CUSTOM) {
            // CUSTOM domains: require customName, enforce unique customName per user
            if (request.getCustomName() == null || request.getCustomName().isBlank()) {
                throw new ValidationException("Custom domain requires a customName.");
            }
            if (domainRepository.existsByUserIdAndCustomName(userId, request.getCustomName())) {
                throw new DomainAlreadyExistsException(
                        "A custom domain named '" + request.getCustomName() + "' already exists.");
            }
        } else {
            // Non-custom types: only one per type allowed
            if (domainRepository.existsByUserIdAndDomainType(userId, request.getDomainType())) {
                throw new DomainAlreadyExistsException(request.getDomainType());
            }
        }

        Domain newDomain = Domain.builder()
                .user(new com.secondbrain.second_brain_server.entities.User(userId))
                .domainType(request.getDomainType())
                .customName(request.getCustomName())
                .context(request.getContext())
                .skillLevel(request.getSkillLevel())
                .status(DomainStatus.ACTIVE)
                .currentStreak(0)
                .longestStreak(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Domain savedDomain = domainRepository.save(newDomain);

        // Gather existing domain schedules so AI can avoid conflicts
        List<String> existingSchedules = domainRepository.findByUserId(userId).stream()
                .filter(d -> d.getWeeklySchedule() != null && !d.getId().equals(savedDomain.getId()))
                .map(d -> (d.getCustomName() != null ? d.getCustomName() : d.getDomainType().name()) + ": " + d.getWeeklySchedule())
                .collect(Collectors.toList());

        GeneratedSystemResponse generatedSystem = aiSystemGeneratorService.generateSystem(
                request.getDomainType(), request.getSkillLevel(), null, request.getCustomName(), request.getContext(), existingSchedules);
        applyGeneratedSystem(savedDomain, generatedSystem);

        return savedDomain.toResponse();
    }

    @Transactional
    public DomainResponse updateDomain(UUID domainId, UUID userId, UpdateDomainRequest request) {
        Domain domain = assertOwnership(domainId, userId);

        Optional.ofNullable(request.getCustomName()).ifPresent(domain::setCustomName);
        Optional.ofNullable(request.getSkillLevel()).ifPresent(domain::setSkillLevel);
        Optional.ofNullable(request.getPlanDescription()).ifPresent(domain::setPlanDescription);
        Optional.ofNullable(request.getContext()).ifPresent(domain::setContext);
        Optional.ofNullable(request.getWeeklySchedule()).ifPresent(domain::setWeeklySchedule);
        Optional.ofNullable(request.getStatus()).ifPresent(domain::setStatus);
        domain.setUpdatedAt(LocalDateTime.now());

        return domainRepository.save(domain).toResponse();
    }

    @Transactional
    public void pauseDomain(UUID domainId, UUID userId) {
        Domain domain = assertOwnership(domainId, userId);
        domain.setStatus(DomainStatus.PAUSED);
        domain.setUpdatedAt(LocalDateTime.now());
        domainRepository.save(domain);
    }

    @Transactional
    public void resumeDomain(UUID domainId, UUID userId) {
        Domain domain = assertOwnership(domainId, userId);
        domain.setStatus(DomainStatus.ACTIVE);
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
    public GeneratedSystemResponse generateAndApplySystem(UUID domainId, UUID userId) {
        Domain domain = assertOwnership(domainId, userId);
        GeneratedSystemResponse generatedSystem = aiSystemGeneratorService.regenerateSystem(domain);
        applyGeneratedSystem(domain, generatedSystem);
        return generatedSystem;
    }

    @Transactional
    public void updateStreakForDomain(Domain domain, LocalDate logDate) {
        // Update lastLogDate if this is the most recent log
        if (domain.getLastLogDate() == null || logDate.isAfter(domain.getLastLogDate())) {
            domain.setLastLogDate(logDate);
        }

        // Recalculate streak properly using all log dates + schedule
        List<LocalDate> logDates = sessionLogRepository.findLogDatesByDomainId(domain.getId());
        if (!logDates.contains(logDate)) {
            logDates.add(logDate);
        }
        java.util.Collections.sort(logDates);

        Integer currentStreak = StreakCalculator.compute(logDates, domain.getWeeklySchedule(), LocalDate.now());
        domain.setCurrentStreak(currentStreak);
        if (currentStreak > domain.getLongestStreak()) {
            domain.setLongestStreak(currentStreak);
        }

        domain.setUpdatedAt(LocalDateTime.now());
        domainRepository.save(domain);
    }

    /**
     * Recalculates streaks for all active domains of a user.
     * Called by the manual API endpoint.
     */
    @Transactional
    public void recalculateAllStreaksForUser(UUID userId) {
        List<Domain> activeDomains = domainRepository.findByUserIdAndStatus(userId, DomainStatus.ACTIVE);
        for (Domain domain : activeDomains) {
            List<LocalDate> logDates = sessionLogRepository.findLogDatesByDomainId(domain.getId());
            java.util.Collections.sort(logDates);

            Integer currentStreak = StreakCalculator.compute(logDates, domain.getWeeklySchedule(), LocalDate.now());
            domain.setCurrentStreak(currentStreak);
            if (currentStreak > domain.getLongestStreak()) {
                domain.setLongestStreak(currentStreak);
            }
            if (!logDates.isEmpty()) {
                domain.setLastLogDate(logDates.get(logDates.size() - 1));
            }
            domain.setUpdatedAt(LocalDateTime.now());
            domainRepository.save(domain);
        }
    }

    public void validateMetricKeys(UUID domainId, Set<String> submittedKeys) {
        List<DomainMetricDefinition> definedMetrics =
                metricDefinitionRepository.findByDomainIdOrderByDisplayOrder(domainId);

        MetricValidator.validateKeys(submittedKeys, definedMetrics);
    }

    /**
     * Clamps submitted metric values to each metric's (declared or inferred) bounds.
     * Percentage/score metrics can't exceed 100; all values are floored at 0.
     */
    public java.util.Map<String, Double> clampMetricValues(UUID domainId, java.util.Map<String, Double> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return metrics;
        }
        List<DomainMetricDefinition> definedMetrics =
                metricDefinitionRepository.findByDomainIdOrderByDisplayOrder(domainId);
        return MetricValidator.clampValues(metrics, definedMetrics);
    }

    /** Persisted min for an AI-generated metric: use AI value if given, else default floor of 0. */
    private Double resolveMinValue(com.secondbrain.second_brain_server.dto.response.MetricDefinitionResponse dto) {
        return dto.getMinValue() != null ? dto.getMinValue() : 0.0;
    }

    /** Persisted max: use AI value if given, else 100 for percentage metrics, else null (open-ended). */
    private Double resolveMaxValue(com.secondbrain.second_brain_server.dto.response.MetricDefinitionResponse dto) {
        if (dto.getMaxValue() != null) {
            return dto.getMaxValue();
        }
        String unit = dto.getUnit();
        if (unit != null) {
            String u = unit.trim().toLowerCase();
            if (u.equals("%") || u.contains("percent")) {
                return 100.0;
            }
        }
        return null;
    }

    private void applyGeneratedSystem(Domain domain, GeneratedSystemResponse generated) {
        Optional.ofNullable(generated.getPlanDescription()).ifPresent(domain::setPlanDescription);
        Optional.ofNullable(generated.getWeeklySchedule()).ifPresent(domain::setWeeklySchedule);
        domain.setUpdatedAt(LocalDateTime.now());
        domainRepository.save(domain);

        metricDefinitionRepository.deleteByDomainId(domain.getId());
        milestoneRepository.findByDomainId(domain.getId()).forEach(milestoneRepository::delete);
        taskRepository.findByDomainId(domain.getId()).forEach(taskRepository::delete);

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
                            .minValue(resolveMinValue(dto))
                            .maxValue(resolveMaxValue(dto))
                            .displayOrder(dto.getDisplayOrder())
                            .build())
                    .collect(Collectors.toList());
            metricDefinitionRepository.saveAll(metrics);
        }

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

        if (generated.getTasks() != null) {
            String domainName = domain.getCustomName() != null ? domain.getCustomName() : domain.getDomainType().name();
            List<Task> tasks = generated.getTasks().stream()
                    .map(dto -> {
                        var video = youTubeService.findVideo(dto.getTitle(), domainName);
                        return Task.builder()
                            .user(domain.getUser())
                            .domain(domain)
                            .title(dto.getTitle())
                            .description(dto.getDescription())
                            .status(TaskStatus.TODO)
                            .dueDate(dto.getDueDate())
                            .aiGenerated(true)
                            .linkedResourceUrl(video.url())
                            .linkedResourceTitle(video.title())
                            .createdAt(LocalDateTime.now())
                            .build();
                    })
                    .collect(Collectors.toList());
            taskRepository.saveAll(tasks);
        }
    }

    private String buildYouTubeSearchQuery(String taskTitle, String domainName) {
        String query = (taskTitle + " " + domainName + " tutorial")
                .replaceAll("[^a-zA-Z0-9\\s]", "")
                .trim()
                .replaceAll("\\s+", "+");
        return "https://www.youtube.com/results?search_query=" + query;
    }

    public Domain assertOwnership(UUID domainId, UUID userId) {
        return domainRepository.findById(domainId)
                .orElseThrow(() -> new ResourceNotFoundException("Domain", domainId))
                .checkOwnership(userId);
    }

    @Transactional(readOnly = true)
    public List<TimeSeriesPointResponse> getChartData(UUID domainId, UUID userId, int days) {
        assertOwnership(domainId, userId);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        List<SessionLog> logs = sessionLogRepository.findByDomainIdAndLogDateBetweenWithMetrics(domainId, startDate, endDate);

        return logs.stream()
                .collect(Collectors.groupingBy(
                        SessionLog::getLogDate,
                        Collectors.summingDouble(log ->
                                log.getMetricValues().stream()
                                        .mapToDouble(mv -> mv.getNumericValue() != null ? mv.getNumericValue() : 0.0)
                                        .sum()
                        )
                ))
                .entrySet().stream()
                .map(entry -> new TimeSeriesPointResponse(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> a.getDate().compareTo(b.getDate()))
                .collect(Collectors.toList());
    }
}
