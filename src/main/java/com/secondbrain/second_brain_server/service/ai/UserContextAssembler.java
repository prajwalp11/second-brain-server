package com.secondbrain.second_brain_server.service.ai;

import com.secondbrain.second_brain_server.dto.response.DomainDto;
import com.secondbrain.second_brain_server.dto.response.MilestoneDto;
import com.secondbrain.second_brain_server.dto.response.PersonalRecordDto;
import com.secondbrain.second_brain_server.dto.response.SessionLogDto;
import com.secondbrain.second_brain_server.dto.response.StreakDto;
import com.secondbrain.second_brain_server.dto.response.TaskDto;
import com.secondbrain.second_brain_server.dto.response.WeeklyStatDto;
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
        List<DomainDto> domainDtos = domains.stream()
                .map(Domain::toDto)
                .collect(Collectors.toList());

        // Recent logs (last 30) with hydrated metrics
        Pageable pageable = PageRequest.of(0, 30);
        List<SessionLogDto> recentLogDtos = sessionLogRepository
                .findByUserIdOrderByLogDateDesc(userId, pageable)
                .getContent()
                .stream()
                .map(log -> {
                    SessionLogDto dto = log.toDto();
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
        List<PersonalRecordDto> prs = prRepository.findByUserId(userId)
                .stream()
                .map(PersonalRecord::toDto)
                .collect(Collectors.toList());

        // Milestones — fetch across all user's domains, not a single null domainId
        List<UUID> domainIds = domains.stream()
                .map(Domain::getId)
                .collect(Collectors.toList());

        List<MilestoneDto> milestones = milestoneRepository
                .findByDomainIdInAndStatus(domainIds, MilestoneStatus.IN_PROGRESS)
                .stream()
                .map(Milestone::toDto)
                .collect(Collectors.toList());

        // Pending tasks
        List<TaskDto> pendingTasks = taskRepository
                .findByUserIdAndStatusIn(userId, Arrays.asList(TaskStatus.TODO, TaskStatus.IN_PROGRESS))
                .stream()
                .map(Task::toDto)
                .collect(Collectors.toList());

        // Streaks — read directly from Domain entity (already maintained by StreakService)
        Map<UUID, StreakDto> streaks = new HashMap<>();
        domains.forEach(domain -> streaks.put(domain.getId(), StreakDto.builder()
                .domainId(domain.getId())
                .domainName(domain.getCustomName() != null
                        ? domain.getCustomName()
                        : domain.getDomainType().name())
                .currentStreak(domain.getCurrentStreak())
                .longestStreak(domain.getLongestStreak())
                .lastLogDate(domain.getLastLogDate())
                .build()));

        // Weekly stats for current week
        LocalDate weekStart = DateUtil.getWeekStart(LocalDate.now());
        List<WeeklyStatDto> weeklyStats = new ArrayList<>();
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

        List<DomainDto> domainSpecific = fullContext.getDomains().stream()
                .filter(d -> d.getId().equals(domainId))
                .collect(Collectors.toList());

        List<SessionLogDto> logsSpecific = fullContext.getRecentLogs().stream()
                .filter(sl -> sl.getDomainId().equals(domainId))
                .collect(Collectors.toList());

        List<PersonalRecordDto> prsSpecific = fullContext.getPrs().stream()
                .filter(pr -> pr.getDomainId() != null && pr.getDomainId().equals(domainId))
                .collect(Collectors.toList());

        List<MilestoneDto> milestonesSpecific = fullContext.getMilestones().stream()
                .filter(m -> m.getDomainId() != null && m.getDomainId().equals(domainId))
                .collect(Collectors.toList());

        List<TaskDto> tasksSpecific = fullContext.getPendingTasks().stream()
                .filter(t -> t.getDomainId() != null && t.getDomainId().equals(domainId))
                .collect(Collectors.toList());

        Map<UUID, StreakDto> streaksSpecific = new HashMap<>();
        if (fullContext.getStreaks().containsKey(domainId)) {
            streaksSpecific.put(domainId, fullContext.getStreaks().get(domainId));
        }

        List<WeeklyStatDto> weeklyStatsSpecific = fullContext.getWeeklyStats().stream()
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