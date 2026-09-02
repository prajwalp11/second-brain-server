package com.secondbrain.second_brain_server.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "job_execution_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "job_name", nullable = false)
    private String jobName;

    @Column(nullable = false)
    private String status; // SUCCESS | FAILED

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    private String details;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
