package com.secondbrain.second_brain_server.dto.response;

import lombok.*;


import java.time.LocalDateTime;
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
    private LocalDateTime date;
    private List<TaskResponse> todayFocus;
    private Map<UUID, StreakResponse> streaks;
    private List<WeeklyStatResponse> weeklyStats;
    private AiNudgeResponse aiNudge;
    private List<TaskResponse> upcomingTasks;
}
