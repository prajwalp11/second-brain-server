package com.secondbrain.second_brain_server.entities;

import com.secondbrain.second_brain_server.enums.PushPlatform;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** A single device's Expo push token. One row per device; disabled on logout or when invalid. */
@Entity
@Table(name = "push_token",
        uniqueConstraints = @UniqueConstraint(name = "uq_push_token_expo", columnNames = {"expo_token"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushToken {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expo_token", nullable = false)
    private String expoToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PushPlatform platform;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
