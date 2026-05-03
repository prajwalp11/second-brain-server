package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.response.InsightsResponse;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.Milestone;
import com.secondbrain.second_brain_server.enums.MilestoneStatus;
import com.secondbrain.second_brain_server.external.GeminiClient;
import com.secondbrain.second_brain_server.external.GeminiMessage;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.MilestoneRepository;
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import com.secondbrain.second_brain_server.service.ai.UserContext;
import com.secondbrain.second_brain_server.service.ai.UserContextAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InsightsService {

    private final DomainRepository domainRepository;
    private final SessionLogRepository sessionLogRepository;
    private final MilestoneRepository milestoneRepository;
    private final GeminiClient geminiClient;
    private final UserContextAssembler userContextAssembler;

    public InsightsResponse getInsights(UUID userId) {
        List<Domain> domains = domainRepository.findByUserId(userId);
        
        try {
            return generateAiInsights(userId, domains);
        } catch (Exception e) {
            return generateFallbackInsights(domains);
        }
    }

    private InsightsResponse generateAiInsights(UUID userId, List<Domain> domains) {
        UserContext userContext = userContextAssembler.assemble(userId);
        
        String prompt = "Based on this user's activity data, provide insights:\n\n" +
                "Domains: " + userContext.getDomains().size() + "\n" +
                "Recent logs: " + userContext.getRecentLogs().size() + "\n" +
                "Personal records: " + userContext.getPrs().size() + "\n\n" +
                "Provide 3 highlights, 3 patterns, and 3 suggestions based on their activity.";
        
        List<GeminiMessage> messages = List.of(
                GeminiMessage.builder()
                        .role("user")
                        .parts(List.of(Map.of("text", prompt)))
                        .build()
        );
        
        String response = geminiClient.completeWithJson(
                "You are an insights generator. Return ONLY valid JSON with structure: " +
                "{\"highlights\": [\"string\"], \"patterns\": [\"string\"], \"suggestions\": [\"string\"]}", 
                messages);
        
        return parseAiResponse(response, domains);
    }

    private InsightsResponse parseAiResponse(String response, List<Domain> domains) {
        try {
            // Extract JSON from response (remove markdown code blocks if present)
            String json = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            
            // Simple JSON parsing (you could use Jackson for production)
            List<String> highlights = extractJsonArray(json, "highlights");
            List<String> patterns = extractJsonArray(json, "patterns");
            List<String> suggestions = extractJsonArray(json, "suggestions");
            
            return InsightsResponse.builder()
                    .highlights(highlights.isEmpty() ? generateFallbackHighlights(domains) : highlights)
                    .patterns(patterns.isEmpty() ? generateFallbackPatterns(domains) : patterns)
                    .suggestions(suggestions.isEmpty() ? generateFallbackSuggestions(domains) : suggestions)
                    .build();
        } catch (Exception e) {
            return generateFallbackInsights(domains);
        }
    }

    private List<String> extractJsonArray(String json, String key) {
        List<String> result = new ArrayList<>();
        try {
            int start = json.indexOf("\"" + key + "\"");
            if (start == -1) return result;
            
            int arrayStart = json.indexOf("[", start);
            int arrayEnd = json.indexOf("]", arrayStart);
            if (arrayStart == -1 || arrayEnd == -1) return result;
            
            String arrayContent = json.substring(arrayStart + 1, arrayEnd);
            String[] items = arrayContent.split("\",\\s*\"");
            
            for (String item : items) {
                String cleaned = item.replaceAll("^\"|\"$", "").trim();
                if (!cleaned.isEmpty()) {
                    result.add(cleaned);
                }
            }
        } catch (Exception e) {
            // Return empty list on parse error
        }
        return result;
    }

    private InsightsResponse generateFallbackInsights(List<Domain> domains) {
        return InsightsResponse.builder()
                .highlights(generateFallbackHighlights(domains))
                .patterns(generateFallbackPatterns(domains))
                .suggestions(generateFallbackSuggestions(domains))
                .build();
    }

    private List<String> generateFallbackHighlights(List<Domain> domains) {
        List<String> highlights = new ArrayList<>();
        
        int totalStreak = domains.stream().mapToInt(Domain::getCurrentStreak).sum();
        if (totalStreak >= 7) {
            highlights.add(totalStreak + "-day streak across all domains!");
        }

        LocalDate weekAgo = LocalDate.now().minusDays(7);
        for (Domain domain : domains) {
            List<Milestone> recentMilestones = milestoneRepository.findByDomainId(domain.getId()).stream()
                    .filter(m -> m.getStatus() == MilestoneStatus.DONE)
                    .filter(m -> m.getCompletedAt() != null && m.getCompletedAt().toLocalDate().isAfter(weekAgo))
                    .toList();
            
            if (!recentMilestones.isEmpty()) {
                highlights.add("Completed " + recentMilestones.size() + " milestone(s) in " + 
                    (domain.getCustomName() != null ? domain.getCustomName() : domain.getDomainType().toString()));
            }
        }

        if (highlights.isEmpty()) {
            highlights.add("Keep logging to see your highlights!");
        }
        return highlights;
    }

    private List<String> generateFallbackPatterns(List<Domain> domains) {
        List<String> patterns = new ArrayList<>();
        
        for (Domain domain : domains) {
            if (domain.getLastLogDate() != null && ChronoUnit.DAYS.between(domain.getLastLogDate(), LocalDate.now()) == 0) {
                patterns.add("You're most consistent with " + 
                    (domain.getCustomName() != null ? domain.getCustomName() : domain.getDomainType().toString()) + 
                    " - logged today!");
            }
        }

        if (patterns.isEmpty()) {
            patterns.add("Keep tracking to discover your patterns!");
        }
        return patterns;
    }

    private List<String> generateFallbackSuggestions(List<Domain> domains) {
        List<String> suggestions = new ArrayList<>();
        
        for (Domain domain : domains) {
            if (domain.getLastLogDate() != null) {
                long daysSinceLog = ChronoUnit.DAYS.between(domain.getLastLogDate(), LocalDate.now());
                if (daysSinceLog >= 3) {
                    suggestions.add("You haven't logged " + 
                        (domain.getCustomName() != null ? domain.getCustomName() : domain.getDomainType().toString()) + 
                        " in " + daysSinceLog + " days. Ready to get back?");
                }
            }
        }

        if (suggestions.isEmpty()) {
            suggestions.add("You're doing great! Keep up the momentum.");
        }
        return suggestions;
    }
}
