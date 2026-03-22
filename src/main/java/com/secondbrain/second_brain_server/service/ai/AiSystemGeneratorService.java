package com.secondbrain.second_brain_server.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.second_brain_server.dto.response.GeneratedSystemDto;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.enums.DomainType;
import com.secondbrain.second_brain_server.enums.SkillLevel;
import com.secondbrain.second_brain_server.exception.AiServiceException;
import com.secondbrain.second_brain_server.external.GeminiClient;
import com.secondbrain.second_brain_server.external.GeminiMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiSystemGeneratorService {

    private final GeminiClient geminiClient;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public GeneratedSystemDto generateSystem(DomainType type, SkillLevel level, String linkedUrl) {
        String systemPrompt = promptBuilder.systemGenerator(type, level, linkedUrl);
        List<GeminiMessage> messages = List.of(new GeminiMessage("user", List.of(Map.of("text", "Generate the system now."))));
        String rawResponse = geminiClient.completeWithJson(systemPrompt, messages);
        return parseResponse(rawResponse);
    }

    public GeneratedSystemDto regenerateSystem(Domain domain) {
        String systemPrompt = promptBuilder.systemGenerator(domain.getDomainType(), domain.getSkillLevel(), domain.getLinkedResourceUrl());
        List<GeminiMessage> messages = List.of(new GeminiMessage("user", List.of(Map.of("text", "Regenerate the system based on the current domain details."))));
        String rawResponse = geminiClient.completeWithJson(systemPrompt, messages);
        return parseResponse(rawResponse);
    }

    private GeneratedSystemDto parseResponse(String raw) {
        try {
            return objectMapper.readValue(raw, GeneratedSystemDto.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse AI system generation response: {}", raw, e);
            throw new AiServiceException("Failed to parse AI system generation response", e);
        }
    }
}
