package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.response.AiNudgeDto;
import com.secondbrain.second_brain_server.dto.response.DashboardResponse;
import com.secondbrain.second_brain_server.dto.response.StreakDto;
import com.secondbrain.second_brain_server.dto.response.TaskDto;
import com.secondbrain.second_brain_server.dto.response.WeeklyStatDto;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.User;
import com.secondbrain.second_brain_server.enums.TaskStatus;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.repository.AiNudgeRepository;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.UserRepository;
import com.secondbrain.second_brain_server.service.ai.AiNudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DomainRepository domainRepository;
    private final TaskService taskService;
    private final WeeklyStatService weeklyStatService;
    private final AiNudgeService aiNudgeService; // Use AiNudgeService to get unread nudge
    private final UserRepository userRepository;

    public DashboardResponse getDashboard(UUID userId, LocalDate date) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        String greeting = buildGreeting(user.getName(), date);
        List<TaskDto> todayFocus = taskService.getTodayTasks(userId);
        List<Domain> domains = domainRepository.findByUserId(userId);
        Map<UUID, StreakDto> streaks = buildStreakMap(domains);
        List<WeeklyStatDto> weeklyStats = weeklyStatService.getWeeklyStats(userId, date); // Get stats for current week
        Optional<AiNudgeDto> aiNudge = aiNudgeService.getUnreadNudge(userId);

        return DashboardResponse.builder()
                .greeting(greeting)
                .date(date)
                .todayFocus(todayFocus)
                .streaks(streaks)
                .weeklyStats(weeklyStats)
                .aiNudge(aiNudge.orElse(null))
                .upcomingTasks(taskService.getTasksForUser(userId, List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS), null)) // All pending tasks
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

    private Map<UUID, StreakDto> buildStreakMap(List<Domain> domains) {
        return domains.stream()
                .map(domain -> StreakDto.builder()
                        .domainId(domain.getId())
                        .domainName(domain.getCustomName() != null ? domain.getCustomName() : domain.getDomainType().name())
                        .currentStreak(domain.getCurrentStreak())
                        .longestStreak(domain.getLongestStreak())
                        .lastLogDate(domain.getLastLogDate())
                        .build())
                .collect(Collectors.toMap(StreakDto::getDomainId, dto -> dto));
    }
}
