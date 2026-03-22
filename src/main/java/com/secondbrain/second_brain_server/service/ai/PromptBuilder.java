package com.secondbrain.second_brain_server.service.ai;

import com.secondbrain.second_brain_server.dto.response.PersonalRecordDto;
import com.secondbrain.second_brain_server.dto.response.WeeklyStatDto;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.SessionLog;
import com.secondbrain.second_brain_server.enums.DomainType;
import com.secondbrain.second_brain_server.enums.NudgeType;
import com.secondbrain.second_brain_server.enums.SkillLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PromptBuilder {

    public String systemGenerator(DomainType type, SkillLevel level, String url) {
        // Placeholder for building system generation prompt
        return null;
    }

    public String sessionInsight(SessionLog log, List<SessionLog> recentLogs, List<PersonalRecordDto> prs) {
        // Placeholder for building session insight prompt
        return null;
    }

    public String nudge(Domain domain, List<SessionLog> logs, NudgeType type) {
        // Placeholder for building nudge prompt
        return null;
    }

    public String chat(UserContext context) {
        // Placeholder for building chat prompt
        return null;
    }

    public String weeklyInsight(List<WeeklyStatDto> stats, List<PersonalRecordDto> prs) {
        // Placeholder for building weekly insight prompt
        return null;
    }
}
