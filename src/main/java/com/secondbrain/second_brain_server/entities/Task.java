package com.secondbrain.second_brain_server.entities;

import com.secondbrain.second_brain_server.dto.response.TaskDto;
import com.secondbrain.second_brain_server.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id")
    private Domain domain;

    private String title;

    @Lob
    private String description;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    private LocalDateTime dueDate;

    private LocalDateTime completedAt;

    private Integer progress;

    private boolean aiGenerated;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    public TaskDto toDto() {
        return TaskDto.builder()
                .id(this.id)
                .domainId(this.domain != null ? this.domain.getId() : null)
                .title(this.title)
                .description(this.description)
                .status(this.status)
                .dueDate(this.dueDate)
                .progress(this.progress)
                .aiGenerated(this.aiGenerated)
                .build();
    }
}
