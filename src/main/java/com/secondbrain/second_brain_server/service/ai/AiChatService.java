package com.secondbrain.second_brain_server.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.second_brain_server.dto.request.AiChatRequest;
import com.secondbrain.second_brain_server.dto.request.ApplyAiActionRequest;
import com.secondbrain.second_brain_server.dto.request.CreateMilestoneRequest;
import com.secondbrain.second_brain_server.dto.request.CreateTaskRequest;
import com.secondbrain.second_brain_server.dto.request.UpdateDomainRequest;
import com.secondbrain.second_brain_server.dto.response.AiActionResponse;
import com.secondbrain.second_brain_server.dto.response.AiChatResponse;
import com.secondbrain.second_brain_server.dto.response.AiConversationResponse;
import com.secondbrain.second_brain_server.dto.response.AiMessageResponse;
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
import com.secondbrain.second_brain_server.service.DomainService;
import com.secondbrain.second_brain_server.service.MilestoneService;
import com.secondbrain.second_brain_server.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        List<AiActionResponse> proposedActions = new ArrayList<>();

        try {
            JsonNode rootNode = objectMapper.readTree(rawAiResponse);
            replyText = rootNode.path("reply").asText("I'm sorry, I couldn't process that.");
            JsonNode actionsNode = rootNode.path("proposedActions");
            if (actionsNode.isArray()) {
                proposedActions = objectMapper.convertValue(actionsNode, new TypeReference<List<AiActionResponse>>() {});
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

    public List<AiConversationResponse> getConversations(UUID userId) {
        return aiConversationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(conv -> AiConversationResponse.builder()
                        .id(conv.getId())
                        .preview(conv.getPreview())
                        .updatedAt(conv.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public List<AiMessageResponse> getMessages(UUID conversationId, UUID userId) {
        AiConversation conversation = aiConversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        return aiMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
                .map(msg -> AiMessageResponse.builder()
                        .id(msg.getId())
                        .role(msg.getRole())
                        .content(msg.getContent())
                        .createdAt(msg.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void applyAction(UUID userId, ApplyAiActionRequest request) {
        Map<String, Object> payload = request.getPayload();
        if (payload == null) {
            throw new ValidationException("Action payload must not be null");
        }

        switch (request.getActionType()) {
            case "ADD_TASK":
                CreateTaskRequest taskRequest = objectMapper.convertValue(payload, CreateTaskRequest.class);
                taskService.createTask(userId, taskRequest);
                log.info("AI Action applied: ADD_TASK for user {}. Title: {}", userId, taskRequest.getTitle());
                break;
            case "SET_MILESTONE":
                CreateMilestoneRequest milestoneRequest = objectMapper.convertValue(payload, CreateMilestoneRequest.class);
                milestoneService.createMilestone(userId, milestoneRequest);
                log.info("AI Action applied: SET_MILESTONE for user {}. Label: {}", userId, milestoneRequest.getLabel());
                break;
            case "ADJUST_PLAN":
                UUID domainId = objectMapper.convertValue(payload.get("domainId"), UUID.class);
                if (domainId == null) {
                    throw new ValidationException("ADJUST_PLAN requires a domainId in payload");
                }
                UpdateDomainRequest domainRequest = objectMapper.convertValue(payload, UpdateDomainRequest.class);
                domainService.updateDomain(domainId, userId, domainRequest);
                log.info("AI Action applied: ADJUST_PLAN for user {}, domain {}", userId, domainId);
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
