package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.request.UpdateNotificationPrefRequest;
import com.secondbrain.second_brain_server.dto.response.PersonalRecordDto;
import com.secondbrain.second_brain_server.entities.NotificationPreference;
import com.secondbrain.second_brain_server.external.FirebasePushService;
import com.secondbrain.second_brain_server.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationPreferenceRepository notificationPrefRepository;
    private final FirebasePushService firebasePushService;

    public NotificationPreference getPreferences(UUID userId) {
        // Placeholder
        return null;
    }

    public void updatePreferences(UUID userId, UpdateNotificationPrefRequest request) {
        // Placeholder
    }

    public void sendDailyReminder(UUID userId) {
        // Placeholder
    }

    public void sendStreakAlert(UUID userId, String domainName, Integer streak) {
        // Placeholder
    }

    public void sendPrCelebration(UUID userId, PersonalRecordDto pr) {
        // Placeholder
    }

    public void sendWeeklyReview(UUID userId) {
        // Placeholder
    }
}
