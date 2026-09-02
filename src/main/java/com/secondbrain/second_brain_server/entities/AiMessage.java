package com.secondbrain.second_brain_server.entities;

import com.secondbrain.second_brain_server.enums.MessageRole;
import com.secondbrain.second_brain_server.enums.MessageStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private AiConversation conversation;

    @Enumerated(EnumType.STRING)
    private MessageRole role;

    @Column(columnDefinition = "TEXT")
    private String content;

    /** Outcome of the AI call for this message (set on ASSISTANT rows). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private MessageStatus status;

    /** Error detail when status is FAILED/DEGRADED (null on success). */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    private LocalDateTime createdAt;
}
