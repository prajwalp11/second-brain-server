package com.secondbrain.second_brain_server.service.ai;

import com.secondbrain.second_brain_server.dto.response.GeneratedSystemDto;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.enums.DomainType;
import com.secondbrain.second_brain_server.enums.SkillLevel;
import com.secondbrain.second_brain_server.external.GeminiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiSystemGeneratorService {

    private final GeminiClient geminiClient;
    private final PromptBuilder promptBuilder;

    public GeneratedSystemDto generateSystem(DomainType domainType, SkillLevel skillLevel, String linkedUrl) {
        // Placeholder for AI system generation logic
        return null;
    }

    public GeneratedSystemDto regenerateSystem(Domain domain) {
        // Placeholder for AI system regeneration logic
        return null;
    }

    private GeneratedSystemDto parseResponse(String raw) {
        // Placeholder for parsing AI response
        return null;
    }
}
