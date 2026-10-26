package com.secondbrain.second_brain_server.entities;

import com.secondbrain.second_brain_server.enums.OtpPurpose;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A one-time 6-digit OTP code, shared by all auth flows (email verification, password reset,
 * account deletion) — distinguished by {@link OtpPurpose}. Stores only a SHA-256 hash of the
 * code (never the raw code). Single-use, time-limited, attempt-capped.
 */
@Entity
@Table(name = "otp_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpToken {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OtpPurpose purpose;

    /** SHA-256 hash of the 6-digit code — never store the raw code. */
    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used;

    /** Number of failed verify attempts; code is invalidated once it reaches the cap. */
    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
