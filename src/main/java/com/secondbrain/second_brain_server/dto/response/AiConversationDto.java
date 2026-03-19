package com.secondbrain.second_brain_server.dto.response;

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
}
