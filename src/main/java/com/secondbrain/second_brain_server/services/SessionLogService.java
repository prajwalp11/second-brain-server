package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.request.CreateSessionLogRequest;
import com.secondbrain.second_brain_server.dto.response.PersonalRecordDto;
import com.secondbrain.second_brain_server.dto.response.SessionLogDto;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.SessionLog;
import com.secondbrain.second_brain_server.entities.SessionMetricValue;
import com.secondbrain.second_brain_server.entities.User;
import com.secondbrain.second_brain_server.exception.ForbiddenException;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import com.secondbrain.second_brain_server.repository.SessionMetricValueRepository;
import com.secondbrain.second_brain_server.service.ai.AiInsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionLogService {

    private final SessionLogRepository sessionLogRepository;
    private final SessionMetricValueRepository sessionMetricValueRepository;
    private final DomainService domainService;
    private final PersonalRecordService prService;
    private final MilestoneService milestoneService;
    private final AiInsightService aiInsightService;

    @Transactional
    public SessionLogDto createLog(UUID userId, CreateSessionLogRequest request) {
        Domain domain = domainService.assertOwnership(request.getDomainId(), userId);
        domainService.validateMetricKeys(request.getDomainId(), request.getMetrics().keySet());

        SessionLog newLog = SessionLog.builder()
                .user(new User(userId))
                .domain(domain)
                .sessionType(request.getSessionType())
                .logDate(request.getLogDate())
                .durationMinutes(request.getDurationMinutes())
                .feelScore(request.getFeelScore())
                .feelLabel(request.getFeelLabel())
                .notes(request.getNotes())
                .linkedReferenceUrl(request.getLinkedReferenceUrl())
                .createdAt(LocalDateTime.now())
                .build();

        SessionLog savedLog = sessionLogRepository.save(newLog);
        persistMetricValues(savedLog, request.getMetrics());

        List<PersonalRecordDto> newPrs = prService.checkAndUpdatePrs(savedLog, request.getMetrics());
        milestoneService.updateProgress(domain.getId());
        domainService.updateStreakForDomain(domain, request.getLogDate());

        aiInsightService.generateSessionInsight(savedLog, newPrs); // Async call

        return buildDto(savedLog, newPrs);
    }

    public Page<SessionLogDto> getLogsForDomain(UUID domainId, UUID userId, Pageable pageable) {
        domainService.assertOwnership(domainId, userId);
        return sessionLogRepository.findByDomainIdOrderByLogDateDesc(domainId, pageable)
                .map(log -> buildDto(log, new ArrayList<>())); // No new PRs on GET
    }

    public Page<SessionLogDto> getLogsForUser(UUID userId, Pageable pageable) {
        return sessionLogRepository.findByUserIdOrderByLogDateDesc(userId, pageable)
                .map(log -> buildDto(log, new ArrayList<>())); // No new PRs on GET
    }

    public SessionLogDto getLogById(UUID logId, UUID userId) {
        SessionLog log = sessionLogRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("SessionLog", logId));

        if (!log.getUser().getId().equals(userId)) {
            throw new ForbiddenException("User is not authorized to access this session log.");
        }
        return buildDto(log, new ArrayList<>());
    }

    @Transactional
    public void deleteLog(UUID logId, UUID userId) {
        SessionLog log = sessionLogRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("SessionLog", logId));

        if (!log.getUser().getId().equals(userId)) {
            throw new ForbiddenException("User is not authorized to delete this session log.");
        }

        sessionLogRepository.delete(log);
        // Re-evaluate milestones and streaks for the domain after deletion
        milestoneService.updateProgress(log.getDomain().getId());
        domainService.updateStreakForDomain(log.getDomain(), log.getLogDate()); // This might need more complex logic for deletion
    }

    private void persistMetricValues(SessionLog log, Map<String, Double> metrics) {
        List<SessionMetricValue> metricValues = metrics.entrySet().stream()
                .map(entry -> SessionMetricValue.builder()
                        .sessionLog(log)
                        .metricKey(entry.getKey())
                        .numericValue(entry.getValue())
                        // Unit would ideally come from DomainMetricDefinition, but not available here directly
                        .unit("") // Placeholder for unit
                        .build())
                .collect(Collectors.toList());
        sessionMetricValueRepository.saveAll(metricValues);
    }

    private Map<String, Double> hydrateMetrics(UUID logId) {
        return sessionMetricValueRepository.findBySessionLogId(logId).stream()
                .collect(Collectors.toMap(
                        SessionMetricValue::getMetricKey,
                        SessionMetricValue::getNumericValue
                ));
    }

    private SessionLogDto buildDto(SessionLog log, List<PersonalRecordDto> newPrs) {
        SessionLogDto dto = log.toDto();
        dto.setMetrics(hydrateMetrics(log.getId()));
        dto.setNewPrs(newPrs);
        return dto;
    }
}
