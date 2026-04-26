package com.secondbrain.second_brain_server.entities;

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

    private String name;

    @Column(unique = true)
    private String email;

    private String passwordHash;

    private String profilePictureUrl;

    private String timezone;

    private LocalDateTime createdAt;

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

    

    // Constructor for creating a User reference with just the ID
    public User(UUID id) {
        this.id = id;
    }

    public com.secondbrain.second_brain_server.dto.response.UserDto toDto() {
        return com.secondbrain.second_brain_server.dto.response.UserDto.builder()
                .id(this.id)
                .name(this.name)
                .email(this.email)
                .profilePictureUrl(this.profilePictureUrl)
                .timezone(this.timezone)
                .build();
    }
}
