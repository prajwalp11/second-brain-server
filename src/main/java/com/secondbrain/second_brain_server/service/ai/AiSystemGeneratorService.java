package com.secondbrain.second_brain_server.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.second_brain_server.dto.response.GeneratedSystemResponse;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.enums.DomainType;
import com.secondbrain.second_brain_server.enums.SkillLevel;
import com.secondbrain.second_brain_server.exception.AiServiceException;
import com.secondbrain.second_brain_server.external.GeminiClient;
import com.secondbrain.second_brain_server.external.GeminiMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiSystemGeneratorService {

    private final GeminiClient geminiClient;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public GeneratedSystemResponse generateSystem(DomainType type, SkillLevel level, String linkedUrl, String customName, String context, List<String> existingSchedules) {
        try {
            String systemPrompt = promptBuilder.systemGenerator(type, level, linkedUrl, customName, context, existingSchedules);
            List<GeminiMessage> messages = List.of(new GeminiMessage("user", List.of(Map.of("text", "Generate the system now."))));
            String rawResponse = geminiClient.completeWithJson(systemPrompt, messages);
            return parseResponse(rawResponse);
        } catch (AiServiceException e) {
            log.error("AI service failed, using fallback system for {} at {} level", type, level, e);
            return createFallbackSystem(type, level, customName);
        }
    }

    public GeneratedSystemResponse regenerateSystem(Domain domain) {
        try {
            String systemPrompt = promptBuilder.systemGenerator(domain.getDomainType(), domain.getSkillLevel(), null, domain.getCustomName(), domain.getContext(), null);
            List<GeminiMessage> messages = List.of(new GeminiMessage("user", List.of(Map.of("text", "Regenerate the system based on the current domain details."))));
            String rawResponse = geminiClient.completeWithJson(systemPrompt, messages);
            return parseResponse(rawResponse);
        } catch (AiServiceException e) {
            log.error("AI service failed, using fallback system for domain {}", domain.getId(), e);
            return createFallbackSystem(domain.getDomainType(), domain.getSkillLevel(), domain.getCustomName());
        }
    }

    /**
     * Generates a single progressive "next" milestone for a domain whose active
     * milestones are all complete. Returns null if the AI call/parse fails (caller skips).
     */
    public com.secondbrain.second_brain_server.dto.response.MilestoneResponse generateNextMilestone(
            Domain domain,
            java.util.List<com.secondbrain.second_brain_server.entities.DomainMetricDefinition> metrics,
            com.secondbrain.second_brain_server.entities.Milestone lastCompleted) {
        try {
            String prompt = promptBuilder.nextMilestone(domain, metrics, lastCompleted);
            List<GeminiMessage> messages = List.of(new GeminiMessage("user", List.of(Map.of("text", "Generate the next milestone now."))));
            String rawResponse = geminiClient.completeWithJson(prompt, messages);
            return objectMapper.readValue(rawResponse, com.secondbrain.second_brain_server.dto.response.MilestoneResponse.class);
        } catch (Exception e) {
            log.error("Failed to generate next milestone for domain {}: {}", domain.getId(), e.getMessage());
            return null;
        }
    }

    private GeneratedSystemResponse createFallbackSystem(DomainType type, SkillLevel level, String customName) {
        String displayName = (type == DomainType.CUSTOM && customName != null) ? customName : type.name().toLowerCase();
        return GeneratedSystemResponse.builder()
                .planDescription("Welcome to your " + displayName + " journey! Start by setting a consistent schedule and tracking your progress.")
                .weeklySchedule("Mon,Tue,Wed,Thu,Fri,Sat,Sun")
                .metrics(List.of())
                .milestones(List.of())
                .tasks(List.of())
                .build();
    }

    private GeneratedSystemResponse parseResponse(String raw) {
        try {
            return objectMapper.readValue(raw, GeneratedSystemResponse.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse AI system generation response: {}", raw, e);
            throw new AiServiceException("Failed to parse AI system generation response", e);
        }
    }
}
