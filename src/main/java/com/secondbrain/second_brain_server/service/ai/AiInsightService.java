package com.secondbrain.second_brain_server.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.second_brain_server.dto.response.PersonalRecordResponse;
import com.secondbrain.second_brain_server.dto.response.WeeklyStatResponse;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.PersonalRecord;
import com.secondbrain.second_brain_server.entities.SessionLog;
import com.secondbrain.second_brain_server.external.GeminiClient;
import com.secondbrain.second_brain_server.external.GeminiMessage;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.PersonalRecordRepository;
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import com.secondbrain.second_brain_server.service.WeeklyStatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiInsightService {

    private final GeminiClient geminiClient;
    private final PromptBuilder promptBuilder;
    private final SessionLogRepository sessionLogRepository;
    private final PersonalRecordRepository prRepository;
    private final DomainRepository domainRepository;
    private final WeeklyStatService weeklyStatService;
    private final ObjectMapper objectMapper;

    @Async
    public void generateSessionInsight(SessionLog sessionLog, List<PersonalRecordResponse> newPrs) {
        try {
            // Fetch last 5 logs for context, excluding the current one
            List<SessionLog> recentLogs = sessionLogRepository.findTopNByDomainIdOrderByLogDateDesc(sessionLog.getDomain().getId(), PageRequest.of(0, 5));
            recentLogs.removeIf(sl -> sl.getId().equals(sessionLog.getId()));

            String systemPrompt = promptBuilder.sessionInsight(sessionLog, recentLogs, newPrs);
            List<GeminiMessage> messages = List.of(new GeminiMessage("user", List.of(Map.of("text", "Generate insight."))));
            String insight = geminiClient.complete(systemPrompt, messages);

            sessionLog.setAiInsight(insight);
            sessionLogRepository.save(sessionLog);
        } catch (Exception e) {
            log.error("Failed to generate AI session insight for log {}: {}", sessionLog.getId(), e.getMessage());
        }

        // After insight, regenerate the linked resource for next session
        try {
            regenerateLinkedResource(sessionLog.getDomain(), sessionLog);
        } catch (Exception e) {
            log.error("Failed to regenerate linked resource for domain {}: {}", sessionLog.getDomain().getId(), e.getMessage());
        }
    }

    private void regenerateLinkedResource(Domain domain, SessionLog latestLog) {
        String domainName = domain.getCustomName() != null ? domain.getCustomName() : domain.getDomainType().name();

        // Fetch recent logs for progress context
        List<SessionLog> recentLogs = sessionLogRepository.findTopNByDomainIdOrderByLogDateDesc(domain.getId(), PageRequest.of(0, 5));
        int totalSessions = sessionLogRepository.countByDomainIdAndLogDateBetween(domain.getId(), domain.getCreatedAt().toLocalDate(), LocalDate.now()).intValue();

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a resource recommendation AI for a personal growth app called 'Second Brain'.\n");
        prompt.append("Based on the user's progress, recommend the NEXT best learning resource for their upcoming session.\n\n");
        prompt.append("Domain: ").append(domainName).append("\n");
        prompt.append("Skill Level: ").append(domain.getSkillLevel()).append("\n");
        prompt.append("Total sessions completed: ").append(totalSessions).append("\n");
        prompt.append("Current streak: ").append(domain.getCurrentStreak()).append(" days\n");
        prompt.append("Current plan: ").append(domain.getPlanDescription() != null ? domain.getPlanDescription() : "None").append("\n");

        if (!recentLogs.isEmpty()) {
            prompt.append("\nRecent sessions:\n");
            recentLogs.forEach(rl -> {
                prompt.append("  - ").append(rl.getLogDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
                prompt.append(", Duration: ").append(rl.getDurationMinutes()).append(" min");
                prompt.append(", Feel: ").append(rl.getFeelLabel());
                if (rl.getNotes() != null && !rl.getNotes().isBlank()) {
                    prompt.append(", Notes: ").append(rl.getNotes().length() > 100 ? rl.getNotes().substring(0, 100) : rl.getNotes());
                }
                prompt.append("\n");
            });
        }

        prompt.append("\nCurrent linked resource: ").append(domain.getLinkedResourceUrl() != null ? domain.getLinkedResourceUrl() : "None").append("\n");
        prompt.append("\nRecommend a NEW, DIFFERENT resource that builds on their current progress. ");
        prompt.append("The resource should be appropriate for someone who has completed ").append(totalSessions).append(" sessions.\n");
        prompt.append("Respond with ONLY a JSON object in this format:\n");
        prompt.append("{\"url\": \"string\", \"title\": \"string\"}\n");
        prompt.append("The URL should be a real, well-known resource (YouTube, official guides, reputable tutorial sites).\n");

        try {
            List<GeminiMessage> messages = List.of(new GeminiMessage("user", List.of(Map.of("text", "Recommend the next resource."))));
            String response = geminiClient.completeWithJson(prompt.toString(), messages);

            JsonNode json = objectMapper.readTree(response);
            String newUrl = json.has("url") ? json.get("url").asText() : null;
            String newTitle = json.has("title") ? json.get("title").asText() : null;

            if (newUrl != null && !newUrl.isBlank()) {
                domain.setLinkedResourceUrl(newUrl);
                domain.setLinkedResourceTitle(newTitle);
                domainRepository.save(domain);
                log.info("Updated linked resource for domain {}: {} - {}", domain.getId(), newTitle, newUrl);
            }
        } catch (Exception e) {
            log.warn("Could not regenerate linked resource for domain {}: {}", domain.getId(), e.getMessage());
        }
    }

    public String generateWeeklyInsight(UUID userId, UUID domainId) {
        try {
            LocalDate weekStart = LocalDate.now().minusWeeks(1).with(java.time.DayOfWeek.MONDAY);
            List<WeeklyStatResponse> weeklyStats = weeklyStatService.getWeeklyStats(userId, weekStart);
            List<PersonalRecordResponse> prs = prRepository.findByUserId(userId).stream()
                    .filter(pr -> pr.getAchievedAt().isAfter(weekStart))
                    .map(PersonalRecord::toResponse)
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
