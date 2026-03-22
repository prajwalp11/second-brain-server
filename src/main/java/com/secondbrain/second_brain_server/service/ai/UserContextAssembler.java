package com.secondbrain.second_brain_server.service.ai;

import com.secondbrain.second_brain_server.dto.response.DomainDto;
import com.secondbrain.second_brain_server.dto.response.MilestoneDto;
import com.secondbrain.second_brain_server.dto.response.PersonalRecordDto;
import com.secondbrain.second_brain_server.dto.response.SessionLogDto;
import com.secondbrain.second_brain_server.dto.response.StreakDto;
import com.secondbrain.second_brain_server.dto.response.TaskDto;
import com.secondbrain.second_brain_server.dto.response.WeeklyStatDto;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.SessionLog;
import com.secondbrain.second_brain_server.enums.MilestoneStatus;
import com.secondbrain.second_brain_server.enums.TaskStatus;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.MilestoneRepository;
import com.secondbrain.second_brain_server.repository.PersonalRecordRepository;
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import com.secondbrain.second_brain_server.repository.SessionMetricValueRepository;
import com.secondbrain.second_brain_server.repository.TaskRepository;
import com.secondbrain.second_brain_server.services.StreakService;
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
    private final StreakService streakService; // Assuming StreakService can provide streak info
    private final WeeklyStatService weeklyStatService; // Assuming WeeklyStatService can provide weekly stats

    public UserContext assemble(UUID userId) {
        List<Domain> domains = domainRepository.findByUserId(userId);
        List<DomainDto> domainDtos = domains.stream().map(Domain::toDto).collect(Collectors.toList());

        // Fetch recent logs (e.g., last 30)
        Pageable pageable = PageRequest.of(0, 30);
        List<SessionLog> recentLogsEntities = sessionLogRepository.findByUserIdOrderByLogDateDesc(userId, pageable).getContent();
        List<SessionLogDto> recentLogDtos = recentLogsEntities.stream()
                .map(log -> {
                    SessionLogDto dto = log.toDto();
                    // Hydrate metrics for each log
                    dto.setMetrics(sessionMetricValueRepository.findBySessionLogId(log.getId()).stream()
                            .collect(Collectors.toMap(
                                    smv -> smv.getMetricKey(),
                                    smv -> smv.getNumericValue()
                            )));
                    return dto;
                })
                .collect(Collectors.toList());

        List<PersonalRecordDto> prs = prRepository.findByUserId(userId).stream()
                .map(pr -> pr.toDto()) // Assuming PersonalRecord has a toDto method
                .collect(Collectors.toList());

        List<MilestoneDto> milestones = milestoneRepository.findByDomainIdAndStatus(null, MilestoneStatus.IN_PROGRESS).stream() // Need to fetch for user's domains
                .map(MilestoneDto::new) // Assuming MilestoneDto has a constructor from Milestone
                .collect(Collectors.toList());

        List<TaskDto> pendingTasks = taskRepository.findByUserIdAndStatusIn(userId, Arrays.asList(TaskStatus.TODO, TaskStatus.IN_PROGRESS)).stream()
                .map(TaskDto::new) // Assuming TaskDto has a constructor from Task
                .collect(Collectors.toList());

        // Streaks
        Map<UUID, StreakDto> streaks = new HashMap<>();
        domains.forEach(domain -> {
            // This might be inefficient if StreakService recalculates every time.
            // Ideally, streak info would be stored on the Domain entity or fetched efficiently.
            // For context, we'll use a simplified approach.
            streaks.put(domain.getId(), StreakDto.builder()
                    .domainId(domain.getId())
                    .domainName(domain.getCustomName() != null ? domain.getCustomName() : domain.getDomainType().name())
                    .currentStreak(domain.getCurrentStreak())
                    .longestStreak(domain.getLongestStreak())
                    .lastLogDate(domain.getLastLogDate())
                    .build());
        });


        // Weekly Stats (for current week)
        LocalDate weekStart = DateUtil.getWeekStart(LocalDate.now());
        List<WeeklyStatDto> weeklyStats = new ArrayList<>();
        for (Domain domain : domains) {
            weeklyStats.addAll(weeklyStatService.getWeeklyStatsForDomain(domain, weekStart));
        }


        return UserContext.builder()
                .userId(userId)
                // .userName(userRepository.findById(userId).map(User::getName).orElse("User")) // Requires UserRepository
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
        // This is a simplified version, could be optimized to fetch only relevant data for the domain
        UserContext fullContext = assemble(userId);
        List<DomainDto> domainSpecific = fullContext.getDomains().stream()
                .filter(d -> d.getId().equals(domainId))
                .collect(Collectors.toList());

        List<SessionLogDto> logsSpecific = fullContext.getRecentLogs().stream()
                .filter(sl -> sl.getDomainId().equals(domainId))
                .collect(Collectors.toList());

        List<PersonalRecordDto> prsSpecific = fullContext.getPrs().stream()
                .filter(pr -> pr.getDomainId().equals(domainId)) // Assuming PersonalRecordDto has domainId
                .collect(Collectors.toList());

        List<MilestoneDto> milestonesSpecific = fullContext.getMilestones().stream()
                .filter(m -> m.getDomainId().equals(domainId)) // Assuming MilestoneDto has domainId
                .collect(Collectors.toList());

        List<TaskDto> tasksSpecific = fullContext.getPendingTasks().stream()
                .filter(t -> t.getDomainId().equals(domainId)) // Assuming TaskDto has domainId
                .collect(Collectors.toList());

        Map<UUID, StreakDto> streaksSpecific = new HashMap<>();
        if (fullContext.getStreaks().containsKey(domainId)) {
            streaksSpecific.put(domainId, fullContext.getStreaks().get(domainId));
        }

        List<WeeklyStatDto> weeklyStatsSpecific = fullContext.getWeeklyStats().stream()
                .filter(ws -> ws.getDomainId().equals(domainId)) // Assuming WeeklyStatDto has domainId
                .collect(Collectors.toList());


        return UserContext.builder()
                .userId(userId)
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
