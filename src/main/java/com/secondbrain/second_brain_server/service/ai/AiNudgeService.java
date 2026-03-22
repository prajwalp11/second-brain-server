package com.secondbrain.second_brain_server.service.ai;

import com.secondbrain.second_brain_server.dto.response.AiNudgeDto;
import com.secondbrain.second_brain_server.entities.AiNudge;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.SessionLog;
import com.secondbrain.second_brain_server.entities.User;
import com.secondbrain.second_brain_server.enums.DomainStatus;
import com.secondbrain.second_brain_server.enums.NudgeType;
import com.secondbrain.second_brain_server.exception.ForbiddenException;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.external.GeminiClient;
import com.secondbrain.second_brain_server.external.GeminiMessage;
import com.secondbrain.second_brain_server.repository.AiNudgeRepository;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import com.secondbrain.second_brain_server.services.StreakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiNudgeService {

    private final GeminiClient geminiClient;
    private final PromptBuilder promptBuilder;
    private final UserContextAssembler contextAssembler; // Not directly used in current implementation, but kept for future context
    private final AiNudgeRepository aiNudgeRepository;
    private final DomainRepository domainRepository;
    private final SessionLogRepository sessionLogRepository;
    private final StreakService streakService;

    @Transactional
    public void generateNudgesForAllDomains(UUID userId) {
        List<Domain> domains = domainRepository.findByUserId(userId);
        LocalDate today = LocalDate.now();

        for (Domain domain : domains) {
            if (alreadySentToday(userId, domain.getId())) {
                log.debug("Nudge already sent for domain {} today. Skipping.", domain.getId());
                continue;
            }

            List<SessionLog> recentLogs = sessionLogRepository.findTopNByDomainIdOrderByLogDateDesc(domain.getId(),  PageRequest.of(0, 5));
            Optional<NudgeType> nudgeType = evaluateNudgeType(domain, recentLogs);

            if (nudgeType.isPresent()) {
                try {
                    String systemPrompt = promptBuilder.nudge(domain, recentLogs, nudgeType.get());
                    List<GeminiMessage> messages = List.of(new GeminiMessage("user", List.of(Map.of("text", "Generate nudge message."))));
                    String nudgeMessage = geminiClient.complete(systemPrompt, messages);

                    AiNudge newNudge = AiNudge.builder()
                            .user(new User(userId))
                            .domain(domain)
                            .message(nudgeMessage)
                            .nudgeType(nudgeType.get())
                            .isRead(false)
                            .generatedAt(LocalDateTime.now())
                            .build();
                    aiNudgeRepository.save(newNudge);
                    log.info("Generated AI Nudge for user {} domain {}: {}", userId, domain.getId(), nudgeType.get());
                } catch (Exception e) {
                    log.error("Failed to generate AI Nudge for user {} domain {}: {}", userId, domain.getId(), e.getMessage());
                }
            }
        }
    }

    public Optional<AiNudgeDto> getUnreadNudge(UUID userId) {
        return aiNudgeRepository.findFirstByUserIdAndIsReadFalseOrderByGeneratedAtDesc(userId)
                .map(AiNudge::toDto); // Assuming AiNudge has a toDto method
    }

    @Transactional
    public void markNudgeRead(UUID nudgeId, UUID userId) {
        AiNudge nudge = aiNudgeRepository.findById(nudgeId)
                .orElseThrow(() -> new ResourceNotFoundException("AiNudge", nudgeId));

        if (!nudge.getUser().getId().equals(userId)) {
            throw new ForbiddenException("User is not authorized to mark this nudge as read.");
        }

        nudge.setRead(true);
        nudge.setReadAt(LocalDateTime.now());
        aiNudgeRepository.save(nudge);
    }

    private Optional<NudgeType> evaluateNudgeType(Domain domain, List<SessionLog> recentLogs) {
        // Simplified logic for prototype. More complex rules would go here.
        LocalDate today = LocalDate.now();

        // MISSING_LOG: If domain is active and last log date is more than 2 days ago
        if (domain.getStatus() == DomainStatus.ACTIVE && domain.getLastLogDate() != null && domain.getLastLogDate().isBefore(today.minusDays(2))) {
            return Optional.of(NudgeType.MISSING_LOG);
        }

        // STREAK_AT_RISK: If domain has a streak and last log date is yesterday, and no log today
        if (domain.getCurrentStreak() > 0 && domain.getLastLogDate() != null && domain.getLastLogDate().isEqual(today.minusDays(1))) {
            // Check if there's a log today
            boolean loggedToday = sessionLogRepository.countByDomainIdAndLogDateBetween(domain.getId(), today, today) > 0;
            if (!loggedToday) {
                return Optional.of(NudgeType.STREAK_AT_RISK);
            }
        }

        // Add more nudge type evaluations here (PR_OPPORTUNITY, MILESTONE_CLOSE, etc.)
        // For now, just basic ones.

        return Optional.empty();
    }

    private boolean alreadySentToday(UUID userId, UUID domainId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        return aiNudgeRepository.existsByUserIdAndDomainIdAndGeneratedAtAfter(userId, domainId, startOfDay);
    }
}
