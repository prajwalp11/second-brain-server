package com.secondbrain.second_brain_server.entities;

import com.secondbrain.second_brain_server.enums.NudgeType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_nudges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiNudge {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id")
    private Domain domain;

    @Lob
    private String message;

    @Enumerated(EnumType.STRING)
    private NudgeType nudgeType;

    private boolean isRead;

    private LocalDateTime generatedAt;

    private LocalDateTime readAt;
}
