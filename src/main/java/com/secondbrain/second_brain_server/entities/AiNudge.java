package com.secondbrain.second_brain_server.entities;

import com.secondbrain.second_brain_server.dto.response.AiNudgeDto;
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

    public AiNudgeDto toDto() {
        return AiNudgeDto.builder()
                .id(this.id)
                .message(this.message)
                .nudgeType(this.nudgeType)
                .domainId(this.domain != null ? this.domain.getId() : null)
                .domainName(this.domain != null ? (this.domain.getCustomName() != null ? this.domain.getCustomName() : this.domain.getDomainType().name()) : null)
                .build();
    }
}
