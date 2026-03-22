package com.secondbrain.second_brain_server.service.ai;

import com.secondbrain.second_brain_server.dto.response.DomainDto;
import com.secondbrain.second_brain_server.dto.response.MilestoneDto;
import com.secondbrain.second_brain_server.dto.response.PersonalRecordDto;
import com.secondbrain.second_brain_server.dto.response.SessionLogDto;
import com.secondbrain.second_brain_server.dto.response.StreakDto;
import com.secondbrain.second_brain_server.dto.response.TaskDto;
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
    private List<DomainDto> domains;
    private List<SessionLogDto> recentLogs;
    private List<PersonalRecordDto> prs;
    private List<MilestoneDto> milestones;
    private List<TaskDto> pendingTasks;
    private List<WeeklyStatDto> weeklyStats;
    private Map<UUID, StreakDto> streaks;

    public String toPromptString() {
        // Placeholder for converting context to a prompt string
        return null;
    }
}
