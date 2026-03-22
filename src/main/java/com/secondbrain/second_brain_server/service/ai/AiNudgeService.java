package com.secondbrain.second_brain_server.service.ai;

import com.secondbrain.second_brain_server.dto.response.AiNudgeDto;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.SessionLog;
import com.secondbrain.second_brain_server.enums.NudgeType;
import com.secondbrain.second_brain_server.external.GeminiClient;
import com.secondbrain.second_brain_server.repository.AiNudgeRepository;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import com.secondbrain.second_brain_server.services.StreakService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiNudgeService {

    private final GeminiClient geminiClient;
    private final PromptBuilder promptBuilder;
    private final UserContextAssembler contextAssembler;
    private final AiNudgeRepository aiNudgeRepository;
    private final DomainRepository domainRepository;
    private final SessionLogRepository sessionLogRepository;
    private final StreakService streakService;

    public void generateNudgesForAllDomains(UUID userId) {
        // Placeholder for AI nudge generation logic
    }

    public Optional<AiNudgeDto> getUnreadNudge(UUID userId) {
        // Placeholder
        return Optional.empty();
    }

    public void markNudgeRead(UUID nudgeId, UUID userId) {
        // Placeholder
    }

    private Optional<NudgeType> evaluateNudgeType(Domain domain, List<SessionLog> recentLogs) {
        // Placeholder
        return Optional.empty();
    }

    private boolean alreadySentToday(UUID userId, UUID domainId) {
        // Placeholder
        return false;
    }
}
