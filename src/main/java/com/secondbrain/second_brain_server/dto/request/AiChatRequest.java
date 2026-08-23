package com.secondbrain.second_brain_server.dto.request;

import com.secondbrain.second_brain_server.enums.ChatMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatRequest {

    @NotBlank
    private String message;

    private UUID conversationId;

    @NotNull(message = "domainId is required — select a domain to chat about")
    private UUID domainId;

    @NotNull(message = "chatMode is required — choose ADJUST_PLAN or DATA_QUERY")
    private ChatMode chatMode;
}
