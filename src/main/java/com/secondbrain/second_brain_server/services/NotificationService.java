package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.request.UpdateNotificationPrefRequest;
import com.secondbrain.second_brain_server.dto.response.PersonalRecordDto;
import com.secondbrain.second_brain_server.entities.NotificationPreference;
import com.secondbrain.second_brain_server.entities.User;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.external.FirebasePushService;
import com.secondbrain.second_brain_server.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationPreferenceRepository notificationPrefRepository;
    private final FirebasePushService firebasePushService;

    public NotificationPreference getPreferences(UUID userId) {
        return notificationPrefRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("NotificationPreference", userId));
    }

    @Transactional
    public void updatePreferences(UUID userId, UpdateNotificationPrefRequest request) {
        NotificationPreference prefs = getPreferences(userId);

        Optional.ofNullable(request.getDailyReminderEnabled()).ifPresent(prefs::setDailyReminderEnabled);
        Optional.ofNullable(request.getDailyReminderTime()).ifPresent(prefs::setDailyReminderTime);
        Optional.ofNullable(request.getWeeklyReviewEnabled()).ifPresent(prefs::setWeeklyReviewEnabled);
        Optional.ofNullable(request.getWeeklyReviewDayOfWeek()).ifPresent(prefs::setWeeklyReviewDayOfWeek);
        Optional.ofNullable(request.getStreakAlertsEnabled()).ifPresent(prefs::setStreakAlertsEnabled);
        Optional.ofNullable(request.getPrCelebrationEnabled()).ifPresent(prefs::setPrCelebrationEnabled);
        prefs.setUpdatedAt(LocalDateTime.now());

        notificationPrefRepository.save(prefs);
    }

    public void sendDailyReminder(UUID userId) {
        Map<String, String> data = new HashMap<>();
        data.put("type", "DAILY_REMINDER");
        firebasePushService.send(userId, "Time to log your progress!", "Don't forget to log your daily activities.", data);
    }

    public void sendStreakAlert(UUID userId, String domainName, Integer streak) {
        Map<String, String> data = new HashMap<>();
        data.put("type", "STREAK_ALERT");
        data.put("domainName", domainName);
        firebasePushService.send(userId, "Streak Alert for " + domainName + "!", "Your " + streak + "-day streak is at risk. Log today to keep it going!", data);
    }

    public void sendPrCelebration(UUID userId, PersonalRecordDto pr) {
        Map<String, String> data = new HashMap<>();
        data.put("type", "PR_CELEBRATION");
        data.put("metricKey", pr.getMetricKey());
        data.put("value", String.valueOf(pr.getValue()));
        data.put("unit", pr.getUnit());
        firebasePushService.send(userId, "New Personal Record!", "Congratulations! You achieved a new PR in " + pr.getLabel() + ": " + pr.getValue() + pr.getUnit() + "!", data);
    }

    public void sendWeeklyReview(UUID userId) {
        Map<String, String> data = new HashMap<>();
        data.put("type", "WEEKLY_REVIEW");
        firebasePushService.send(userId, "Your Weekly Review is Ready!", "Check out your progress and insights from the past week.", data);
    }
}

