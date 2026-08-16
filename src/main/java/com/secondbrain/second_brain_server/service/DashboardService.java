package com.secondbrain.second_brain_server.service;

import com.secondbrain.second_brain_server.dto.response.AiNudgeResponse;
import com.secondbrain.second_brain_server.dto.response.DashboardResponse;
import com.secondbrain.second_brain_server.dto.response.StreakResponse;
import com.secondbrain.second_brain_server.dto.response.TaskResponse;
import com.secondbrain.second_brain_server.dto.response.WeeklyStatResponse;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.User;
import com.secondbrain.second_brain_server.util.DateUtil;
import com.secondbrain.second_brain_server.enums.DomainStatus;
import com.secondbrain.second_brain_server.enums.TaskStatus;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.repository.AiNudgeRepository;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.UserRepository;
import com.secondbrain.second_brain_server.service.ai.AiNudgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DomainRepository domainRepository;
    private final TaskService taskService;
    private final WeeklyStatService weeklyStatService;
    private final AiNudgeService aiNudgeService;
    private final UserRepository userRepository;

    public DashboardResponse getDashboard(UUID userId, LocalDate date) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        String greeting = buildGreeting(user.getFirstName(), date);

        // Only active domains
        List<Domain> activeDomains = domainRepository.findByUserIdAndStatus(userId, DomainStatus.ACTIVE);
        List<UUID> activeDomainIds = activeDomains.stream().map(Domain::getId).collect(Collectors.toList());

        // Only tasks due today for active domains
        List<TaskResponse> todayFocus = taskService.getUpcomingTasks(userId).stream()
                .filter(t -> t.getDueDate() != null && t.getDueDate().equals(date))
                .filter(t -> t.getDomainId() == null || activeDomainIds.contains(t.getDomainId()))
                .collect(Collectors.toList());

        Map<UUID, StreakResponse> streaks = buildStreakMap(activeDomains);
        List<WeeklyStatResponse> weeklyStats = weeklyStatService.getWeeklyStats(userId, DateUtil.getWeekStart(date));
        // Filter weekly stats to active domains only
        weeklyStats = weeklyStats.stream()
                .filter(ws -> activeDomainIds.contains(ws.getDomainId()))
                .collect(Collectors.toList());

        Optional<AiNudgeResponse> aiNudge = aiNudgeService.getUnreadNudge(userId);

        // Upcoming tasks — only for active domains
        List<TaskResponse> upcomingTasks = taskService.getTasksForUser(userId, List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS), null)
                .stream()
                .filter(t -> t.getDomainId() == null || activeDomainIds.contains(t.getDomainId()))
                .collect(Collectors.toList());

        return DashboardResponse.builder()
                .greeting(greeting)
                .date(date.atStartOfDay())
                .todayFocus(todayFocus)
                .streaks(streaks)
                .weeklyStats(weeklyStats)
                .aiNudge(aiNudge.orElse(null))
                .upcomingTasks(upcomingTasks)
                .build();
    }

    private String buildGreeting(String name, LocalDate date) {
        LocalTime now = LocalTime.now();
        String timeOfDay;
        if (now.isBefore(LocalTime.NOON)) {
            timeOfDay = "Good morning";
        } else if (now.isBefore(LocalTime.of(18, 0))) {
            timeOfDay = "Good afternoon";
        } else {
            timeOfDay = "Good evening";
        }
        return String.format("%s, %s!", timeOfDay, name);
    }

    private Map<UUID, StreakResponse> buildStreakMap(List<Domain> domains) {
        return domains.stream()
                .map(domain -> StreakResponse.builder()
                        .domainId(domain.getId())
                        .domainName(domain.getCustomName() != null ? domain.getCustomName() : domain.getDomainType().name())
                        .currentStreak(domain.getCurrentStreak())
                        .longestStreak(domain.getLongestStreak())
                        .lastLogDate(domain.getLastLogDate() != null ? domain.getLastLogDate().atStartOfDay() : null)
                        .build())
                .collect(Collectors.toMap(StreakResponse::getDomainId, dto -> dto));
    }
}
