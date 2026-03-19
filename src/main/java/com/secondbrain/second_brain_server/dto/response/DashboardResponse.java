package com.secondbrain.second_brain_server.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardResponse {

    private String greeting;
    private LocalDate date;
    private List<TaskDto> todayFocus;
    private Map<UUID, StreakDto> streaks;
    private List<WeeklyStatDto> weeklyStats;
    private AiNudgeDto aiNudge;
    private List<TaskDto> upcomingTasks;
}
