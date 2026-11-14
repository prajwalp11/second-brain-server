package com.secondbrain.second_brain_server.entities;

import com.secondbrain.second_brain_server.enums.PushStatus;
import com.secondbrain.second_brain_server.enums.PushType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Queued notification (outbox pattern). The PLANNER inserts PENDING rows; the SENDER drains them
 * to Expo. At most one PENDING row per (user, type); expires_at drops stale rows unsent.
 */
@Entity
@Table(name = "push_outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PushType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    @Column(columnDefinition = "text")
    private String data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PushStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = PushStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
