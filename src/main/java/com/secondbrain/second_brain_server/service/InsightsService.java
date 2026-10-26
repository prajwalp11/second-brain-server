package com.secondbrain.second_brain_server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.second_brain_server.dto.response.InsightsResponse;
import com.secondbrain.second_brain_server.dto.response.MilestoneResponse;
import com.secondbrain.second_brain_server.dto.response.PersonalRecordResponse;
import com.secondbrain.second_brain_server.dto.response.SessionLogResponse;
import com.secondbrain.second_brain_server.dto.response.StreakResponse;
import com.secondbrain.second_brain_server.dto.response.WeeklyStatResponse;
import com.secondbrain.second_brain_server.entities.DomainInsight;
import com.secondbrain.second_brain_server.external.GeminiClient;
import com.secondbrain.second_brain_server.external.GeminiMessage;
import com.secondbrain.second_brain_server.repository.DomainInsightRepository;
import com.secondbrain.second_brain_server.service.ai.UserContext;
import com.secondbrain.second_brain_server.service.ai.UserContextAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Per-domain Progress-tab insights. getInsights is read-only (cached row, no AI).
 * generateAndStore does the Gemini call with real domain data and caches it —
 * called by the 3 AM scheduler and the manual refresh endpoint.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InsightsService {

    private final GeminiClient geminiClient;
    private final UserContextAssembler userContextAssembler;
    private final DomainInsightRepository domainInsightRepository;
    private final DomainService domainService;
    private final ObjectMapper objectMapper;

    /** API path — READ ONLY for one domain (ownership-checked). No AI call. */
    public InsightsResponse getInsights(UUID userId, UUID domainId) {
        domainService.assertOwnership(domainId, userId);
        return domainInsightRepository.findByDomainId(domainId)
                .map(this::toResponse)
                .orElseGet(() -> fallback(userContextAssembler.assembleForDomain(userId, domainId)));
    }

    /** Generate + cache insights for ONE domain using real domain data. Ownership-checked. */
    public InsightsResponse generateAndStore(UUID userId, UUID domainId) {
        domainService.assertOwnership(domainId, userId);
        UserContext ctx = userContextAssembler.assembleForDomain(userId, domainId);
        InsightsResponse insights;
        try {
            insights = generateAi(ctx);
        } catch (Exception e) {
            log.warn("AI insights failed for domain {}, using fallback: {}", domainId, e.getMessage());
            insights = fallback(ctx);
        }
        save(domainId, insights);
        return insights;
    }

    // ─── AI generation ──────────────────────────────────────────────────────────

    private InsightsResponse generateAi(UserContext ctx) {
        String prompt = buildPrompt(ctx);
        String system = "You are a focused progress coach for ONE activity domain. " +
                "Return ONLY valid JSON: {\"highlights\":[\"string\"],\"patterns\":[\"string\"],\"suggestions\":[\"string\"]}. " +
                "Exactly 3 of each. Each item MUST be at most 8 words, specific to the data given, punchy, no filler.";

        List<GeminiMessage> messages = List.of(
                GeminiMessage.builder().role("user").parts(List.of(Map.of("text", prompt))).build());

        String response = geminiClient.completeWithJson(system, messages);
        return parse(response, ctx);
    }

    private String buildPrompt(UserContext ctx) {
        StringBuilder sb = new StringBuilder();
        String domainName = ctx.getDomains().isEmpty() ? "this domain"
                : (ctx.getDomains().get(0).getCustomName() != null
                    ? ctx.getDomains().get(0).getCustomName()
                    : ctx.getDomains().get(0).getDomainType().toString());

        sb.append("Generate progress insights for the user's '").append(domainName).append("' domain.\n\n");

        // Streak
        for (StreakResponse s : ctx.getStreaks().values()) {
            sb.append("Current streak: ").append(s.getCurrentStreak())
              .append(" days (longest ").append(s.getLongestStreak()).append(").\n");
        }

        // Recent sessions (up to 10)
        List<SessionLogResponse> logs = ctx.getRecentLogs().stream().limit(10).collect(Collectors.toList());
        sb.append("Recent sessions (").append(logs.size()).append("):\n");
        for (SessionLogResponse l : logs) {
            sb.append("  - ").append(l.getLogDate());
            if (l.getDurationMinutes() != null) sb.append(", ").append(l.getDurationMinutes()).append("min");
            if (l.getFeelLabel() != null) sb.append(", felt ").append(l.getFeelLabel());
            if (l.getMetrics() != null && !l.getMetrics().isEmpty()) {
                sb.append(", ").append(l.getMetrics().entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining(" ")));
            }
            sb.append("\n");
        }

        // PRs
        if (!ctx.getPrs().isEmpty()) {
            sb.append("Personal records:\n");
            for (PersonalRecordResponse pr : ctx.getPrs()) {
                sb.append("  - ").append(pr.getLabel()).append(": ").append(pr.getValue()).append(pr.getUnit()).append("\n");
            }
        }

        // Milestones
        if (!ctx.getMilestones().isEmpty()) {
            sb.append("Active milestones:\n");
            for (MilestoneResponse m : ctx.getMilestones()) {
                sb.append("  - ").append(m.getLabel()).append(" (").append(m.getCurrentValue())
                  .append("/").append(m.getTargetValue()).append(m.getUnit()).append(")\n");
            }
        }

        // Weekly stats
        if (!ctx.getWeeklyStats().isEmpty()) {
            sb.append("This week:\n");
            for (WeeklyStatResponse ws : ctx.getWeeklyStats()) {
                sb.append("  - ").append(ws.getLabel()).append(": ").append(ws.getValue()).append(ws.getUnit());
                if (ws.getTarget() != null) sb.append(" / target ").append(ws.getTarget()).append(ws.getUnit());
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private InsightsResponse parse(String response, UserContext ctx) {
        try {
            String json = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            Map<String, List<String>> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            List<String> h = parsed.getOrDefault("highlights", List.of());
            List<String> p = parsed.getOrDefault("patterns", List.of());
            List<String> s = parsed.getOrDefault("suggestions", List.of());
            InsightsResponse fb = fallback(ctx);
            return InsightsResponse.builder()
                    .highlights(h.isEmpty() ? fb.getHighlights() : h)
                    .patterns(p.isEmpty() ? fb.getPatterns() : p)
                    .suggestions(s.isEmpty() ? fb.getSuggestions() : s)
                    .build();
        } catch (Exception e) {
            return fallback(ctx);
        }
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    private void save(UUID domainId, InsightsResponse insights) {
        try {
            DomainInsight row = domainInsightRepository.findByDomainId(domainId).orElseGet(DomainInsight::new);
            row.setDomainId(domainId);
            row.setHighlights(objectMapper.writeValueAsString(insights.getHighlights()));
            row.setPatterns(objectMapper.writeValueAsString(insights.getPatterns()));
            row.setSuggestions(objectMapper.writeValueAsString(insights.getSuggestions()));
            row.setGeneratedAt(LocalDateTime.now());
            domainInsightRepository.save(row);
        } catch (Exception e) {
            log.error("Failed to store insights for domain {}: {}", domainId, e.getMessage());
        }
    }

    private InsightsResponse toResponse(DomainInsight row) {
        return InsightsResponse.builder()
                .highlights(readList(row.getHighlights()))
                .patterns(readList(row.getPatterns()))
                .suggestions(readList(row.getSuggestions()))
                .build();
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ─── Fallback (short, domain-specific) ───────────────────────────────────────

    private InsightsResponse fallback(UserContext ctx) {
        List<String> highlights = new ArrayList<>();
        List<String> patterns = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        ctx.getStreaks().values().forEach(s -> {
            if (s.getCurrentStreak() != null && s.getCurrentStreak() >= 3) {
                highlights.add(s.getCurrentStreak() + "-day streak going");
            }
        });
        if (!ctx.getPrs().isEmpty()) highlights.add(ctx.getPrs().size() + " personal record(s)");

        int logs = ctx.getRecentLogs().size();
        if (logs > 0) patterns.add(logs + " recent session(s) logged");

        if (logs == 0) suggestions.add("Log a session to start insights");
        else suggestions.add("Keep the momentum — log today");

        if (highlights.isEmpty()) highlights.add("Keep logging to unlock highlights");
        if (patterns.isEmpty()) patterns.add("Log more to reveal patterns");
        return InsightsResponse.builder().highlights(highlights).patterns(patterns).suggestions(suggestions).build();
    }
}
