package com.secondbrain.second_brain_server.service.ai;

import com.secondbrain.second_brain_server.dto.response.PersonalRecordDto;
import com.secondbrain.second_brain_server.dto.response.WeeklyStatDto;
import com.secondbrain.second_brain_server.entities.PersonalRecord;
import com.secondbrain.second_brain_server.entities.SessionLog;
import com.secondbrain.second_brain_server.external.GeminiClient;
import com.secondbrain.second_brain_server.external.GeminiMessage;
import com.secondbrain.second_brain_server.repository.PersonalRecordRepository;
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import com.secondbrain.second_brain_server.services.WeeklyStatService; // Needed for weekly insight
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDate; // Added for weekly insight
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiInsightService {

    private final GeminiClient geminiClient;
    private final PromptBuilder promptBuilder;
    private final SessionLogRepository sessionLogRepository;
    private final PersonalRecordRepository prRepository;
    private final WeeklyStatService weeklyStatService; // Inject WeeklyStatService

    @Async
    public void generateSessionInsight(SessionLog sessionLog, List<PersonalRecordDto> newPrs) {
        try {
            // Fetch last 5 logs for context, excluding the current one
            List<SessionLog> recentLogs = sessionLogRepository.findTopNByDomainIdOrderByLogDateDesc(sessionLog.getDomain().getId(), PageRequest.of(0, 5));
            recentLogs.removeIf(sl -> sl.getId().equals(sessionLog.getId())); // Ensure current log is not in recent

            String systemPrompt = promptBuilder.sessionInsight(sessionLog, recentLogs, newPrs);
            List<GeminiMessage> messages = List.of(new GeminiMessage("user", List.of(Map.of("text", "Generate insight."))));
            String insight = geminiClient.complete(systemPrompt, messages);

            sessionLog.setAiInsight(insight);
            sessionLogRepository.save(sessionLog);
        } catch (Exception e) {
            log.error("Failed to generate AI session insight for log {}: {}", sessionLog.getId(), e.getMessage());
            // Optionally, rethrow or handle more gracefully
        }
    }

    public String generateWeeklyInsight(UUID userId, UUID domainId) {
        try {
            LocalDate weekStart = LocalDate.now().minusWeeks(1).with(java.time.DayOfWeek.MONDAY); // Last week's start
            List<WeeklyStatDto> weeklyStats = weeklyStatService.getWeeklyStats(userId, weekStart); // Assuming this fetches for a specific user/domain
            List<PersonalRecordDto> prs = prRepository.findByUserId(userId).stream()
                    .filter(pr -> pr.getAchievedAt().isAfter(weekStart))
                    .map(PersonalRecord::toDto)
                    .collect(Collectors.toList());

            String systemPrompt = promptBuilder.weeklyInsight(weeklyStats, prs);
            List<GeminiMessage> messages = List.of(new GeminiMessage("user", List.of(Map.of("text", "Generate weekly insight."))));
            return geminiClient.complete(systemPrompt, messages);
        } catch (Exception e) {
            log.error("Failed to generate AI weekly insight for user {} and domain {}: {}", userId, domainId, e.getMessage());
            return "Could not generate weekly insight at this time.";
        }
    }
}
