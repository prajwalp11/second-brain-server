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
@Slf4j
public class AiSystemGeneratorService {

    private final GeminiClient geminiClient;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public GeneratedSystemResponse generateSystem(DomainType type, SkillLevel level, String linkedUrl) {
        try {
            String systemPrompt = promptBuilder.systemGenerator(type, level, linkedUrl);
            List<GeminiMessage> messages = List.of(new GeminiMessage("user", List.of(Map.of("text", "Generate the system now."))));
            String rawResponse = geminiClient.completeWithJson(systemPrompt, messages);
            return parseResponse(rawResponse);
        } catch (AiServiceException e) {
            log.error("AI service failed, using fallback system for {} at {} level", type, level, e);
            return createFallbackSystem(type, level, linkedUrl);
        }
    }

    public GeneratedSystemResponse regenerateSystem(Domain domain) {
        try {
            String systemPrompt = promptBuilder.systemGenerator(domain.getDomainType(), domain.getSkillLevel(), domain.getLinkedResourceUrl());
            List<GeminiMessage> messages = List.of(new GeminiMessage("user", List.of(Map.of("text", "Regenerate the system based on the current domain details."))));
            String rawResponse = geminiClient.completeWithJson(systemPrompt, messages);
            return parseResponse(rawResponse);
        } catch (AiServiceException e) {
            log.error("AI service failed, using fallback system for domain {}", domain.getId(), e);
            return createFallbackSystem(domain.getDomainType(), domain.getSkillLevel(), domain.getLinkedResourceUrl());
        }
    }

    private GeneratedSystemResponse createFallbackSystem(DomainType type, SkillLevel level, String linkedUrl) {
        return GeneratedSystemResponse.builder()
                .planDescription("Welcome to your " + type.name().toLowerCase() + " journey! Start by setting a consistent schedule and tracking your progress.")
                .weeklySchedule("Mon,Tue,Wed,Thu,Fri,Sat,Sun")
                .linkedResourceUrl(linkedUrl)
                .linkedResourceTitle(linkedUrl != null ? "Resource" : null)
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
