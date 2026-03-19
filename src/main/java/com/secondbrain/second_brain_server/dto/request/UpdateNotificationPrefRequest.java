package com.secondbrain.second_brain_server.dto.request;

import lombok.*;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateNotificationPrefRequest {

    private Boolean dailyReminderEnabled;
    private LocalTime dailyReminderTime;
    private Boolean weeklyReviewEnabled;
    private Integer weeklyReviewDayOfWeek;
    private Boolean streakAlertsEnabled;
    private Boolean prCelebrationEnabled;
}
