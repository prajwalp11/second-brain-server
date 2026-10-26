package com.secondbrain.second_brain_server.entities;

import com.secondbrain.second_brain_server.dto.response.UserResponse;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // Uniqueness is enforced by a PARTIAL index (unique on email WHERE is_deleted = false),
    // so a soft-deleted row keeps its original email while the address can be re-registered.
    private String email;

    private String password;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "auth_provider")
    private String authProvider;

    @Column(name = "email_verified")
    private Boolean emailVerified;

    @Column(name = "is_deleted")
    private Boolean isDeleted;

    @Column(name = "ai_daily_limit")
    private Integer aiDailyLimit;

    @Column(name = "ai_used_today")
    private Integer aiUsedToday;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Domain> domains;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SessionLog> sessionLogs;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Task> tasks;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PersonalRecord> personalRecords;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AiNudge> aiNudges;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AiConversation> aiConversations;


    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.aiDailyLimit == null) {
            this.aiDailyLimit = 3;
        }
        if (this.aiUsedToday == null) {
            this.aiUsedToday = 0;
        }
        if (this.authProvider == null) {
            this.authProvider = "EMAIL";
        }
        if (this.emailVerified == null) {
            this.emailVerified = false;
        }
        if (this.isDeleted == null) {
            this.isDeleted = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    

    // Constructor for creating a User reference with just the ID
    public User(UUID id) {
        this.id = id;
    }

    public UserResponse toResponse() {
        return UserResponse.builder()
                .id(this.id)
                .firstName(this.firstName)
                .lastName(this.lastName)
                .email(this.email)
                .build();
    }
}
