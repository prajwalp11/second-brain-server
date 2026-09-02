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
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.User;
import com.secondbrain.second_brain_server.enums.MessageRole;
import com.secondbrain.second_brain_server.enums.MessageStatus;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final com.secondbrain.second_brain_server.repository.UserRepository userRepository;

    // ─── Chat ────────────────────────────────────────────────────────────────────

    @Transactional
    public AiChatResponse chat(UUID userId, AiChatRequest request) {
        // 1. Rate limit check — per-user daily limit from DB
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        int limit = user.getAiDailyLimit() != null ? user.getAiDailyLimit() : 3;
        int used = user.getAiUsedToday() != null ? user.getAiUsedToday() : 0;

        if (used >= limit) {
            return AiChatResponse.builder()
                    .reply("You've reached your daily limit of " + limit + " AI questions. Come back tomorrow!")
                    .conversationId(null)
                    .proposedActions(List.of())
                    .build();
        }

        // 2. Validate domain ownership
        Domain domain = domainService.assertOwnership(request.getDomainId(), userId);
        String domainName = domain.getCustomName() != null ? domain.getCustomName() : domain.getDomainType().name();

        // 3. Resolve or create conversation
        AiConversation conversation = resolveOrCreateConversation(userId, request.getConversationId());
        conversation.setUpdatedAt(LocalDateTime.now());

        // 4. Build domain-scoped context + strict prompt
        UserContext userContext = contextAssembler.assembleForDomain(userId, request.getDomainId());
        String systemPrompt = promptBuilder.chat(userContext, request.getChatMode(), domainName);

        // 5. Build conversation history for multi-turn
        List<GeminiMessage> geminiMessages = aiMessageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversation.getId())
                .stream()
                .map(msg -> new GeminiMessage(
                        msg.getRole().name().toLowerCase(),
                        List.of(Map.of("text", msg.getContent()))))
                .collect(Collectors.toList());

        // Add current user message
        geminiMessages.add(new GeminiMessage("user", List.of(Map.of("text", request.getMessage()))));

        // 6. Call Gemini
        String rawAiResponse;
        try {
            rawAiResponse = geminiClient.completeWithJson(systemPrompt, geminiMessages);
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("[AiChat] Gemini call failed for user {}: {}", userId, errorMsg);
            // Record the outage on the message row so it's visible, but don't count it
            // against the user's daily limit.
            String fallbackReply = "AI is temporarily unavailable. Please try again in a moment. (Your question was not counted against your daily limit.)";
            persistMessages(conversation, request.getMessage(), fallbackReply, MessageStatus.FAILED, errorMsg);
            return AiChatResponse.builder()
                    .reply(fallbackReply)
                    .conversationId(conversation.getId())
                    .proposedActions(List.of())
                    .build();
        }

        // 7. Parse response
        String replyText;
        List<AiActionResponse> proposedActions = new ArrayList<>();
        MessageStatus status = MessageStatus.SUCCESS;
        String errorMessage = null;

        try {
            JsonNode rootNode = objectMapper.readTree(rawAiResponse);
            replyText = rootNode.path("reply").asText("I couldn't process that. Please try again.");
            JsonNode actionsNode = rootNode.path("proposedActions");
            if (actionsNode.isArray()) {
                proposedActions = objectMapper.convertValue(actionsNode, new TypeReference<List<AiActionResponse>>() {});
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse AI chat response JSON: {}", rawAiResponse, e);
            replyText = rawAiResponse; // Fallback: use raw text if not valid JSON
            status = MessageStatus.DEGRADED;
            errorMessage = "Response was not valid JSON: " + e.getMessage();
        }

        // 8. Persist messages (assistant row carries the call status)
        persistMessages(conversation, request.getMessage(), replyText, status, errorMessage);

        // 9. Increment the user's daily AI usage counter (only on success)
        user.setAiUsedToday(used + 1);
        userRepository.save(user);

        return AiChatResponse.builder()
                .reply(replyText)
                .conversationId(conversation.getId())
                .proposedActions(proposedActions)
                .build();
    }

    // ─── Conversations ───────────────────────────────────────────────────────────

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

    // ─── Apply Actions ───────────────────────────────────────────────────────────

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
                // Remove domainId from payload before converting to UpdateDomainRequest
                Map<String, Object> planPayload = new HashMap<>(payload);
                planPayload.remove("domainId");
                UpdateDomainRequest domainRequest = objectMapper.convertValue(planPayload, UpdateDomainRequest.class);
                domainService.updateDomain(domainId, userId, domainRequest);
                log.info("AI Action applied: ADJUST_PLAN for user {}, domain {}", userId, domainId);
                break;
            default:
                throw new ValidationException("Unknown AI action type: " + request.getActionType());
        }
    }

    /**
     * Returns remaining AI messages for the user today (from per-user DB counters).
     */
    public int getRemainingMessages(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        int limit = user.getAiDailyLimit() != null ? user.getAiDailyLimit() : 3;
        int used = user.getAiUsedToday() != null ? user.getAiUsedToday() : 0;
        return Math.max(0, limit - used);
    }

    // ─── Private Helpers ─────────────────────────────────────────────────────────

    private AiConversation resolveOrCreateConversation(UUID userId, UUID conversationId) {
        if (conversationId != null) {
            return aiConversationRepository.findByIdAndUserId(conversationId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
        } else {
            AiConversation newConv = AiConversation.builder()
                    .user(new User(userId))
                    .preview("New Chat")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            return aiConversationRepository.save(newConv);
        }
    }

    private void persistMessages(AiConversation conv, String userMsg, String reply, MessageStatus status, String errorMessage) {
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
                .status(status)
                .errorMessage(errorMessage)
                .createdAt(LocalDateTime.now())
                .build();
        aiMessageRepository.save(aiReplyMessage);

        // Update conversation preview with first 50 chars of user message
        conv.setPreview(userMsg.length() > 50 ? userMsg.substring(0, 50) + "..." : userMsg);
        aiConversationRepository.save(conv);
    }
}
