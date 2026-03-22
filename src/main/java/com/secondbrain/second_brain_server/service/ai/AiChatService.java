package com.secondbrain.second_brain_server.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.second_brain_server.dto.request.AiChatRequest;
import com.secondbrain.second_brain_server.dto.request.ApplyAiActionRequest;
import com.secondbrain.second_brain_server.dto.response.AiActionDto;
import com.secondbrain.second_brain_server.dto.response.AiChatResponse;
import com.secondbrain.second_brain_server.dto.response.AiConversationDto;
import com.secondbrain.second_brain_server.dto.response.AiMessageDto;
import com.secondbrain.second_brain_server.entities.AiConversation;
import com.secondbrain.second_brain_server.entities.AiMessage;
import com.secondbrain.second_brain_server.entities.User;
import com.secondbrain.second_brain_server.enums.MessageRole;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.exception.ValidationException;
import com.secondbrain.second_brain_server.external.GeminiClient;
import com.secondbrain.second_brain_server.external.GeminiMessage;
import com.secondbrain.second_brain_server.repository.AiConversationRepository;
import com.secondbrain.second_brain_server.repository.AiMessageRepository;
import com.secondbrain.second_brain_server.services.DomainService;
import com.secondbrain.second_brain_server.services.MilestoneService;
import com.secondbrain.second_brain_server.services.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatService {

    private final GeminiClient geminiClient;
    private final PromptBuilder promptBuilder;
    private final UserContextAssembler contextAssembler;
    private final AiConversationRepository aiConversationRepository;
    private final AiMessageRepository aiMessageRepository;
    private final TaskService taskService;
    private final MilestoneService milestoneService;
    private final DomainService domainService;
    private final ObjectMapper objectMapper;

    @Transactional
    public AiChatResponse chat(UUID userId, AiChatRequest request) {
        AiConversation conversation = resolveOrCreateConversation(userId, request.getConversationId());
        conversation.setUpdatedAt(LocalDateTime.now());

        UserContext userContext = contextAssembler.assemble(userId);
        String systemPrompt = promptBuilder.chat(userContext);

        List<GeminiMessage> geminiMessages = aiMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
                .map(msg -> new GeminiMessage(msg.getRole().name().toLowerCase(), List.of(Map.of("text", msg.getContent()))))
                .collect(Collectors.toList());

        // Add current user message
        geminiMessages.add(new GeminiMessage("user", List.of(Map.of("text", request.getMessage()))));

        String rawAiResponse = geminiClient.completeWithJson(systemPrompt, geminiMessages);

        String replyText;
        List<AiActionDto> proposedActions = new ArrayList<>();

        try {
            JsonNode rootNode = objectMapper.readTree(rawAiResponse);
            replyText = rootNode.path("reply").asText("I'm sorry, I couldn't process that.");
            JsonNode actionsNode = rootNode.path("proposedActions");
            if (actionsNode.isArray()) {
                proposedActions = objectMapper.convertValue(actionsNode, new TypeReference<List<AiActionDto>>() {});
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse AI chat response JSON: {}", rawAiResponse, e);
            replyText = "I'm sorry, I received an unparseable response from the AI.";
        }

        persistMessages(conversation, request.getMessage(), replyText);

        return AiChatResponse.builder()
                .reply(replyText)
                .conversationId(conversation.getId())
                .proposedActions(proposedActions)
                .build();
    }

    public List<AiConversationDto> getConversations(UUID userId) {
        return aiConversationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(conv -> AiConversationDto.builder()
                        .id(conv.getId())
                        .preview(conv.getPreview())
                        .updatedAt(conv.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public List<AiMessageDto> getMessages(UUID conversationId, UUID userId) {
        AiConversation conversation = aiConversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        return aiMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
                .map(msg -> AiMessageDto.builder()
                        .id(msg.getId())
                        .role(msg.getRole())
                        .content(msg.getContent())
                        .createdAt(msg.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void applyAction(UUID userId, ApplyAiActionRequest request) {
        // This is a simplified implementation. In a real app, you'd have more robust action handling.
        switch (request.getActionType()) {
            case "ADD_TASK":
                // Assuming payload contains necessary fields for CreateTaskRequest
                // taskService.createTask(userId, objectMapper.convertValue(request.getPayload(), CreateTaskRequest.class));
                log.info("AI Action: ADD_TASK for user {}. Payload: {}", userId, request.getPayload());
                break;
            case "SET_MILESTONE":
                // milestoneService.createMilestone(userId, objectMapper.convertValue(request.getPayload(), CreateMilestoneRequest.class));
                log.info("AI Action: SET_MILESTONE for user {}. Payload: {}", userId, request.getPayload());
                break;
            case "ADJUST_PLAN":
                // domainService.updateDomain(domainId, userId, objectMapper.convertValue(request.getPayload(), UpdateDomainRequest.class));
                log.info("AI Action: ADJUST_PLAN for user {}. Payload: {}", userId, request.getPayload());
                break;
            default:
                throw new ValidationException("Unknown AI action type: " + request.getActionType());
        }
    }

    private AiConversation resolveOrCreateConversation(UUID userId, UUID conversationId) {
        if (conversationId != null) {
            return aiConversationRepository.findByIdAndUserId(conversationId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
        } else {
            AiConversation newConv = AiConversation.builder()
                    .user(new User(userId))
                    .preview("New Chat") // Default preview, will be updated later
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            return aiConversationRepository.save(newConv);
        }
    }

    private void persistMessages(AiConversation conv, String userMsg, String reply) {
        AiMessage userAiMessage = AiMessage.builder()
                .conversation(conv)
                .role(MessageRole.USER)
                .content(userMsg)
                .createdAt(LocalDateTime.now())
                .build();
        aiMessageRepository.save(userAiMessage);

        AiMessage aiReplyMessage = AiMessage.builder()
                .conversation(conv)
                .role(MessageRole.ASSISTANT)
                .content(reply)
                .createdAt(LocalDateTime.now())
                .build();
        aiMessageRepository.save(aiReplyMessage);

        // Update conversation preview
        conv.setPreview(userMsg.substring(0, Math.min(userMsg.length(), 50)) + "...");
        aiConversationRepository.save(conv);
    }
}
