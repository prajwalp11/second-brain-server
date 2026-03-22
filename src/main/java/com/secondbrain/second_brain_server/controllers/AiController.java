package com.secondbrain.second_brain_server.controllers;

import com.secondbrain.second_brain_server.dto.request.AiChatRequest;
import com.secondbrain.second_brain_server.dto.request.ApplyAiActionRequest;
import com.secondbrain.second_brain_server.dto.response.AiChatResponse;
import com.secondbrain.second_brain_server.dto.response.AiConversationDto;
import com.secondbrain.second_brain_server.dto.response.AiMessageDto;
import com.secondbrain.second_brain_server.dto.response.AiNudgeDto;
import com.secondbrain.second_brain_server.security.CurrentUser;
import com.secondbrain.second_brain_server.service.ai.AiChatService;
import com.secondbrain.second_brain_server.service.ai.AiNudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiChatService aiChatService;
    private final AiNudgeService aiNudgeService;

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@RequestBody AiChatRequest request, @CurrentUser UUID userId) {
        return ResponseEntity.ok(aiChatService.chat(userId, request));
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<AiConversationDto>> getConversations(@CurrentUser UUID userId) {
        return ResponseEntity.ok(aiChatService.getConversations(userId));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<AiMessageDto>> getMessages(@PathVariable UUID conversationId, @CurrentUser UUID userId) {
        return ResponseEntity.ok(aiChatService.getMessages(conversationId, userId));
    }

    @PostMapping("/actions/apply")
    public ResponseEntity<Void> applyAction(@RequestBody ApplyAiActionRequest request, @CurrentUser UUID userId) {
        aiChatService.applyAction(userId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/nudge")
    public ResponseEntity<AiNudgeDto> getNudge(@CurrentUser UUID userId) {
        return ResponseEntity.ok(aiNudgeService.getUnreadNudge(userId).orElse(null));
    }

    @PostMapping("/nudge/{nudgeId}/read")
    public ResponseEntity<Void> markNudgeRead(@PathVariable UUID nudgeId, @CurrentUser UUID userId) {
        aiNudgeService.markNudgeRead(nudgeId, userId);
        return ResponseEntity.ok().build();
    }
}
