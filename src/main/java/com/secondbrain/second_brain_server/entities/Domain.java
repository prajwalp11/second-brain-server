package com.secondbrain.second_brain_server.entities;

import com.secondbrain.second_brain_server.dto.response.DomainDto;
import com.secondbrain.second_brain_server.enums.DomainStatus;
import com.secondbrain.second_brain_server.enums.DomainType;
import com.secondbrain.second_brain_server.enums.SkillLevel;
import com.secondbrain.second_brain_server.exception.ForbiddenException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "domains")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Domain {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private DomainType domainType;

    private String customName;

    @Enumerated(EnumType.STRING)
    private SkillLevel skillLevel;

    @Enumerated(EnumType.STRING)
    private DomainStatus status;

    @Lob
    private String planDescription;

    private String weeklySchedule;

    private String linkedResourceUrl;

    private String linkedResourceTitle;

    private Integer currentStreak;

    private Integer longestStreak;

    private LocalDate lastLogDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "domain", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DomainMetricDefinition> metricDefinitions;

    @OneToMany(mappedBy = "domain", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SessionLog> sessionLogs;

    @OneToMany(mappedBy = "domain", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Milestone> milestones;

    @OneToMany(mappedBy = "domain", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PersonalRecord> personalRecords;

    @OneToMany(mappedBy = "domain", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Task> tasks;

    public Domain checkOwnership(UUID userId) {
        if (!this.user.getId().equals(userId)) {
            throw new ForbiddenException("User is not authorized to access this domain.");
        }
        return this;
    }

    public DomainDto toDto() {
        return DomainDto.builder()
                .id(this.id)
                .domainType(this.domainType)
                .customName(this.customName)
                .skillLevel(this.skillLevel)
                .status(this.status)
                .planDescription(this.planDescription)
                .weeklySchedule(this.weeklySchedule)
                .linkedResourceUrl(this.linkedResourceUrl)
                .linkedResourceTitle(this.linkedResourceTitle)
                .currentStreak(this.currentStreak)
                .longestStreak(this.longestStreak)
                .lastLogDate(this.lastLogDate)
                // Metrics and Milestones will be populated by service layer or specific DTO mappers
                .metrics(this.metricDefinitions != null ? this.metricDefinitions.stream().map(DomainMetricDefinition::toDto).collect(Collectors.toList()) : null)
                // nextMilestone will be populated by service layer
                .build();
    }
}
