package com.secondbrain.second_brain_server.entities;

import com.secondbrain.second_brain_server.enums.MilestoneStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "milestones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id")
    private Domain domain;

    private String label;

    private String metricKey;

    private Double targetValue;

    private Double currentValue;

    private String unit;

    @Enumerated(EnumType.STRING)
    private MilestoneStatus status;

    private LocalDate deadline;

    private LocalDate completedAt;

    private boolean aiGenerated;

    private LocalDateTime createdAt;
}
