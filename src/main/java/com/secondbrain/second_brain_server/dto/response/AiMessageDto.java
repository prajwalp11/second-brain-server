package com.secondbrain.second_brain_server.dto.response;

import com.secondbrain.second_brain_server.enums.MessageRole;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiMessageDto {

    private UUID id;
    private MessageRole role;
    private String content;
    private LocalDateTime createdAt;
}
