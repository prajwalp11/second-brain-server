package com.secondbrain.second_brain_server.service.ai;

import com.secondbrain.second_brain_server.dto.request.AiChatRequest;
import com.secondbrain.second_brain_server.dto.request.ApplyAiActionRequest;
import com.secondbrain.second_brain_server.dto.response.AiActionDto;
import com.secondbrain.second_brain_server.dto.response.AiChatResponse;
import com.secondbrain.second_brain_server.dto.response.AiConversationDto;
import com.secondbrain.second_brain_server.dto.response.AiMessageDto;
import com.secondbrain.second_brain_server.entities.AiConversation;
import com.secondbrain.second_brain_server.external.GeminiClient;
import com.secondbrain.second_brain_server.repository.AiConversationRepository;
import com.secondbrain.second_brain_server.repository.AiMessageRepository;
import com.secondbrain.second_brain_server.services.DomainService;
import com.secondbrain.second_brain_server.services.MilestoneService;
import com.secondbrain.second_brain_server.services.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private final GeminiClient geminiClient;
    private final PromptBuilder promptBuilder;
    private final UserContextAssembler contextAssembler;
    private final AiConversationRepository aiConversationRepository;
    private final AiMessageRepository aiMessageRepository;
    private final TaskService taskService;
    private final MilestoneService milestoneService;
    private final DomainService domainService;

    public AiChatResponse chat(UUID userId, AiChatRequest request) {
        // Placeholder for AI chat logic
        return null;
    }

    public List<AiConversationDto> getConversations(UUID userId) {
        // Placeholder
        return null;
    }

    public List<AiMessageDto> getMessages(UUID conversationId, UUID userId) {
        // Placeholder
        return null;
    }

    public void applyAction(UUID userId, ApplyAiActionRequest request) {
        // Placeholder
    }

    private AiConversation resolveOrCreateConversation(UUID userId, UUID conversationId) {
        // Placeholder
        return null;
    }

    private void persistMessages(AiConversation conv, String userMsg, String reply) {
        // Placeholder
    }

    private List<AiActionDto> parseProposedActions(String reply) {
        // Placeholder
        return null;
    }
}
