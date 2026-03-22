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

import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PromptBuilder {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");

    public String systemGenerator(DomainType type, SkillLevel level, String url) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert system designer for a personal growth and habit tracking application called 'Second Brain'.\n");
        prompt.append("Your task is to generate a comprehensive system for a user's new domain based on their input.\n");
        prompt.append("The output MUST be a valid JSON object with the following structure:\n");
        prompt.append("{\n");
        prompt.append("  \"planDescription\": \"string\",\n");
        prompt.append("  \"weeklySchedule\": \"string\",\n");
        prompt.append("  \"metrics\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"metricKey\": \"string\",\n");
        prompt.append("      \"label\": \"string\",\n");
        prompt.append("      \"unit\": \"string\",\n");
        prompt.append("      \"isTrackedPerSession\": boolean,\n");
        prompt.append("      \"isPR\": boolean,\n");
        prompt.append("      \"isHigherBetter\": boolean,\n");
        prompt.append("      \"displayOrder\": integer\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"milestones\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"label\": \"string\",\n");
        prompt.append("      \"metricKey\": \"string\",\n");
        prompt.append("      \"targetValue\": double,\n");
        prompt.append("      \"unit\": \"string\",\n");
        prompt.append("      \"deadline\": \"YYYY-MM-DD\"\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"linkedResourceUrl\": \"string\",\n");
        prompt.append("  \"linkedResourceTitle\": \"string\",\n");
        prompt.append("  \"tasks\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"title\": \"string\",\n");
        prompt.append("      \"description\": \"string\",\n");
        prompt.append("      \"dueDate\": \"YYYY-MM-DD\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n");
        prompt.append("Ensure all fields are populated with sensible defaults or generated content. For metrics, provide at least 3-5 relevant metrics. For milestones, provide 2-3 achievable milestones. For tasks, provide 2-3 initial tasks.\n");
        prompt.append("User Input:\n");
        prompt.append("Domain Type: ").append(type).append("\n");
        prompt.append("Skill Level: ").append(level).append("\n");
        if (url != null && !url.isEmpty()) {
            prompt.append("Linked Resource: ").append(url).append("\n");
        }
        prompt.append("Generate the system now.\n");
        return prompt.toString();
    }

    public String sessionInsight(SessionLog log, List<SessionLog> recentLogs, List<PersonalRecordDto> prs) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI assistant providing insights for a user's session logs in a personal growth app.\n");
        prompt.append("Analyze the provided session log and recent history to offer constructive feedback, highlight progress, or suggest areas for improvement.\n");
        prompt.append("Keep the insight concise, encouraging, and actionable. Max 150 words.\n");
        prompt.append("Current Session Log (ID: ").append(log.getId()).append(", Date: ").append(log.getLogDate().format(DATE_FORMATTER)).append("):\n");
        prompt.append("  Domain: ").append(log.getDomain().getCustomName() != null ? log.getDomain().getCustomName() : log.getDomain().getDomainType()).append("\n");
        prompt.append("  Duration: ").append(log.getDurationMinutes()).append(" minutes\n");
        prompt.append("  Feel Score: ").append(log.getFeelScore()).append(" (").append(log.getFeelLabel()).append(")\n");
        prompt.append("  Notes: ").append(log.getNotes()).append("\n");
        if (log.getMetricValues() != null && !log.getMetricValues().isEmpty()) {
            prompt.append("  Metrics: ").append(log.getMetricValues().stream()
                    .map(mv -> mv.getMetricKey() + ": " + DECIMAL_FORMAT.format(mv.getNumericValue()) + mv.getUnit())
                    .collect(Collectors.joining(", "))).append("\n");
        }
        if (prs != null && !prs.isEmpty()) {
            prompt.append("  New Personal Records: ").append(prs.stream()
                    .map(pr -> pr.getLabel() + " " + DECIMAL_FORMAT.format(pr.getValue()) + pr.getUnit())
                    .collect(Collectors.joining(", "))).append("\n");
        }

        if (!recentLogs.isEmpty()) {
            prompt.append("\nRecent Session History (last ").append(recentLogs.size()).append(" logs):\n");
            recentLogs.forEach(rl -> prompt.append("  - Date: ").append(rl.getLogDate().format(DATE_FORMATTER))
                    .append(", Duration: ").append(rl.getDurationMinutes())
                    .append(", Feel: ").append(rl.getFeelLabel()).append("\n"));
        }
        prompt.append("\nProvide your insight:\n");
        return prompt.toString();
    }

    public String nudge(Domain domain, List<SessionLog> logs, NudgeType type) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI assistant generating a personalized 'nudge' message for a user in a personal growth app.\n");
        prompt.append("The nudge should be concise, encouraging, and relevant to the user's current status in their domain. Max 50 words.\n");
        prompt.append("Domain: ").append(domain.getCustomName() != null ? domain.getCustomName() : domain.getDomainType()).append("\n");
        prompt.append("Current Status: ").append(domain.getStatus()).append("\n");
        prompt.append("Current Streak: ").append(domain.getCurrentStreak()).append(" days\n");
        if (domain.getLastLogDate() != null) {
            prompt.append("Last Log Date: ").append(domain.getLastLogDate().format(DATE_FORMATTER)).append("\n");
        }
        if (!logs.isEmpty()) {
            prompt.append("Recent Logs (last ").append(logs.size()).append("):\n");
            logs.forEach(l -> prompt.append("  - Date: ").append(l.getLogDate().format(DATE_FORMATTER))
                    .append(", Duration: ").append(l.getDurationMinutes()).append("\n"));
        }
        prompt.append("Nudge Type: ").append(type).append("\n");
        prompt.append("Generate the nudge message:\n");
        return prompt.toString();
    }

    public String chat(UserContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI assistant for a personal growth and habit tracking application called 'Second Brain'.\n");
        prompt.append("Your goal is to help the user manage their domains, tasks, and milestones. You can answer questions, provide suggestions, and help with planning.\n");
        prompt.append("You have access to the user's context:\n");
        prompt.append(context.toPromptString()).append("\n"); // UserContext will format itself
        prompt.append("If the user asks for an action (e.g., 'add a task', 'set a milestone', 'adjust my plan'), respond with a JSON object containing a proposed action. The output MUST be a valid JSON object with the following structure:\n");
        prompt.append("{\n");
        prompt.append("  \"reply\": \"string\",\n");
        prompt.append("  \"proposedActions\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"type\": \"string\", // e.g., ADD_TASK, SET_MILESTONE, ADJUST_PLAN, LINK_RESOURCE, UPDATE_SCHEDULE\n");
        prompt.append("      \"description\": \"string\",\n");
        prompt.append("      \"payload\": { /* JSON object with action-specific data */ }\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n");
        prompt.append("If no action is proposed, the 'proposedActions' array should be empty. Otherwise, provide a helpful reply and the action.\n");
        prompt.append("If the user's query is not an action, just provide a helpful text reply. Your reply should be concise and directly address the user's query.\n");
        prompt.append("User's message will follow.\n");
        return prompt.toString();
    }

    public String weeklyInsight(List<WeeklyStatDto> stats, List<PersonalRecordDto> prs) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI assistant providing a weekly review insight for a user in a personal growth app.\n");
        prompt.append("Analyze the provided weekly statistics and any new personal records to offer a summary of progress, highlight achievements, and suggest focus areas for the upcoming week.\n");
        prompt.append("Keep the insight concise, encouraging, and actionable. Max 200 words.\n");
        if (!stats.isEmpty()) {
            prompt.append("\nWeekly Statistics:\n");
            stats.forEach(ws -> prompt.append("  - ").append(ws.getDomainName()).append(" - ").append(ws.getLabel())
                    .append(": ").append(DECIMAL_FORMAT.format(ws.getValue())).append(ws.getUnit())
                    .append(ws.getTarget() != null ? " (Target: " + DECIMAL_FORMAT.format(ws.getTarget()) + ws.getUnit() + ")" : "")
                    .append("\n"));
        }
        if (!prs.isEmpty()) {
            prompt.append("\nNew Personal Records this week:\n");
            prs.forEach(pr -> prompt.append("  - ").append(pr.getLabel()).append(" ").append(DECIMAL_FORMAT.format(pr.getValue())).append(pr.getUnit())
                    .append(" (Previous: ").append(DECIMAL_FORMAT.format(pr.getPreviousValue())).append(pr.getUnit()).append(")\n"));
        }
        prompt.append("\nProvide your weekly insight:\n");
        return prompt.toString();
    }
}
