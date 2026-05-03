package com.secondbrain.second_brain_server.service.ai;

import com.secondbrain.second_brain_server.dto.response.DomainResponse;
import com.secondbrain.second_brain_server.dto.response.MilestoneResponse;
import com.secondbrain.second_brain_server.dto.response.PersonalRecordResponse;
import com.secondbrain.second_brain_server.dto.response.SessionLogResponse;
import com.secondbrain.second_brain_server.dto.response.StreakResponse;
import com.secondbrain.second_brain_server.dto.response.TaskResponse;
import com.secondbrain.second_brain_server.dto.response.WeeklyStatResponse;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.Milestone;
import com.secondbrain.second_brain_server.entities.PersonalRecord;
import com.secondbrain.second_brain_server.entities.SessionMetricValue;
import com.secondbrain.second_brain_server.entities.Task;
import com.secondbrain.second_brain_server.entities.User;
import com.secondbrain.second_brain_server.enums.MilestoneStatus;
import com.secondbrain.second_brain_server.enums.TaskStatus;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.MilestoneRepository;
import com.secondbrain.second_brain_server.repository.PersonalRecordRepository;
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import com.secondbrain.second_brain_server.repository.SessionMetricValueRepository;
import com.secondbrain.second_brain_server.repository.TaskRepository;
import com.secondbrain.second_brain_server.repository.UserRepository;
import com.secondbrain.second_brain_server.services.WeeklyStatService;
import com.secondbrain.second_brain_server.util.DateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserContextAssembler {

    private final SessionLogRepository sessionLogRepository;
    private final SessionMetricValueRepository sessionMetricValueRepository;
    private final PersonalRecordRepository prRepository;
    private final DomainRepository domainRepository;
    private final MilestoneRepository milestoneRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final WeeklyStatService weeklyStatService;

    public UserContext assemble(UUID userId) {
        // Domains
        List<Domain> domains = domainRepository.findByUserId(userId);
        List<DomainResponse> domainDtos = domains.stream()
                .map(Domain::toResponse)
                .collect(Collectors.toList());

        // Recent logs (last 30) with hydrated metrics
        Pageable pageable = PageRequest.of(0, 30);
        List<SessionLogResponse> recentLogDtos = sessionLogRepository
                .findByUserIdOrderByLogDateDesc(userId, pageable)
                .getContent()
                .stream()
                .map(log -> {
                    SessionLogResponse dto = log.toResponse();
                    dto.setMetrics(sessionMetricValueRepository
                            .findBySessionLogId(log.getId())
                            .stream()
                            .collect(Collectors.toMap(
                                    SessionMetricValue::getMetricKey,
                                    SessionMetricValue::getNumericValue
                            )));
                    return dto;
                })
                .collect(Collectors.toList());

        // Personal records
        List<PersonalRecordResponse> prs = prRepository.findByUserId(userId)
                .stream()
                .map(PersonalRecord::toResponse)
                .collect(Collectors.toList());

        // Milestones — fetch across all user's domains, not a single null domainId
        List<UUID> domainIds = domains.stream()
                .map(Domain::getId)
                .collect(Collectors.toList());

        List<MilestoneResponse> milestones = milestoneRepository
                .findByDomainIdInAndStatus(domainIds, MilestoneStatus.IN_PROGRESS)
                .stream()
                .map(Milestone::toResponse)
                .collect(Collectors.toList());

        // Pending tasks
        List<TaskResponse> pendingTasks = taskRepository
                .findByUserIdAndStatusIn(userId, Arrays.asList(TaskStatus.TODO, TaskStatus.IN_PROGRESS))
                .stream()
                .map(Task::toResponse)
                .collect(Collectors.toList());

        // Streaks — read directly from Domain entity (already maintained by StreakService)
        Map<UUID, StreakResponse> streaks = new HashMap<>();
        domains.forEach(domain -> streaks.put(domain.getId(), StreakResponse.builder()
                .domainId(domain.getId())
                .domainName(domain.getCustomName() != null
                        ? domain.getCustomName()
                        : domain.getDomainType().name())
                .currentStreak(domain.getCurrentStreak())
                .longestStreak(domain.getLongestStreak())
                .lastLogDate(domain.getLastLogDate() != null ? domain.getLastLogDate().atStartOfDay() : null)
                .build()));

        // Weekly stats for current week
        LocalDate weekStart = DateUtil.getWeekStart(LocalDate.now());
        List<WeeklyStatResponse> weeklyStats = new ArrayList<>();
        for (Domain domain : domains) {
            weeklyStats.addAll(weeklyStatService.getWeeklyStatsForDomain(domain, weekStart));
        }

        // User name for prompt personalisation
        String userName = userRepository.findById(userId)
                .map(User::getFirstName)
                .orElse("User");

        return UserContext.builder()
                .userId(userId)
                .userName(userName)
                .domains(domainDtos)
                .recentLogs(recentLogDtos)
                .prs(prs)
                .milestones(milestones)
                .pendingTasks(pendingTasks)
                .streaks(streaks)
                .weeklyStats(weeklyStats)
                .build();
    }

    public UserContext assembleForDomain(UUID userId, UUID domainId) {
        // TODO: optimise — currently fetches full user context then filters.
        // Should query directly by domainId for each repo call to avoid over-fetching.
        UserContext fullContext = assemble(userId);

        List<DomainResponse> domainSpecific = fullContext.getDomains().stream()
                .filter(d -> d.getId().equals(domainId))
                .collect(Collectors.toList());

        List<SessionLogResponse> logsSpecific = fullContext.getRecentLogs().stream()
                .filter(sl -> sl.getDomainId().equals(domainId))
                .collect(Collectors.toList());

        List<PersonalRecordResponse> prsSpecific = fullContext.getPrs().stream()
                .filter(pr -> pr.getDomainId() != null && pr.getDomainId().equals(domainId))
                .collect(Collectors.toList());

        List<MilestoneResponse> milestonesSpecific = fullContext.getMilestones().stream()
                .filter(m -> m.getDomainId() != null && m.getDomainId().equals(domainId))
                .collect(Collectors.toList());

        List<TaskResponse> tasksSpecific = fullContext.getPendingTasks().stream()
                .filter(t -> t.getDomainId() != null && t.getDomainId().equals(domainId))
                .collect(Collectors.toList());

        Map<UUID, StreakResponse> streaksSpecific = new HashMap<>();
        if (fullContext.getStreaks().containsKey(domainId)) {
            streaksSpecific.put(domainId, fullContext.getStreaks().get(domainId));
        }

        List<WeeklyStatResponse> weeklyStatsSpecific = fullContext.getWeeklyStats().stream()
                .filter(ws -> ws.getDomainId() != null && ws.getDomainId().equals(domainId))
                .collect(Collectors.toList());

        return UserContext.builder()
                .userId(userId)
                .userName(fullContext.getUserName())
                .domains(domainSpecific)
                .recentLogs(logsSpecific)
                .prs(prsSpecific)
                .milestones(milestonesSpecific)
                .pendingTasks(tasksSpecific)
                .streaks(streaksSpecific)
                .weeklyStats(weeklyStatsSpecific)
                .build();
    }
}