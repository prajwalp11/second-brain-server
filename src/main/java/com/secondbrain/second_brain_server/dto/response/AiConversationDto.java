package com.secondbrain.second_brain_server.dto.response;

import com.secondbrain.second_brain_server.entities.AiConversation;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiConversationDto {

    private UUID id;
    private String preview;
    private LocalDateTime updatedAt;

    public AiConversationDto(AiConversation conversation) {
        this.id = conversation.getId();
        this.preview = conversation.getPreview();
        this.updatedAt = conversation.getUpdatedAt();
    }
}
