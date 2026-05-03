package com.secondbrain.second_brain_server.service.ai;

import com.secondbrain.second_brain_server.dto.response.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
public class UserContext {
    private UUID userId;
    private String userName;
    private List<DomainResponse> domains;
    private List<SessionLogResponse> recentLogs;
    private List<PersonalRecordResponse> prs;
    private List<MilestoneResponse> milestones;
    private List<TaskResponse> pendingTasks;
    private List<WeeklyStatResponse> weeklyStats;
    private Map<UUID, StreakResponse> streaks;
}
