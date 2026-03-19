package com.secondbrain.second_brain_server.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    private boolean dailyReminderEnabled;

    private LocalTime dailyReminderTime;

    private boolean weeklyReviewEnabled;

    private Integer weeklyReviewDayOfWeek;

    private boolean streakAlertsEnabled;

    private boolean prCelebrationEnabled;

    private LocalDateTime updatedAt;
}
