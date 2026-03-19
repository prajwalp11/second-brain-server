package com.secondbrain.second_brain_server.dto.response;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatResponse {

    private String reply;
    private UUID conversationId;
    private List<AiActionDto> proposedActions;
}
