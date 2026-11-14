package com.secondbrain.second_brain_server.entities;

import com.secondbrain.second_brain_server.enums.PushStatus;
import com.secondbrain.second_brain_server.enums.PushType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Sent ledger: audit trail + source of truth for the per-type cooldown (latest sent_at per type). */
@Entity
@Table(name = "push_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PushType type;

    private String title;

    private String body;

    @Column(columnDefinition = "text")
    private String data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PushStatus status;

    @Column(name = "expo_ticket_id")
    private String expoTicketId;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    protected void onCreate() {
        if (sentAt == null) sentAt = LocalDateTime.now();
    }
}
