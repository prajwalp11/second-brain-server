package com.secondbrain.second_brain_server.external;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class FirebasePushService {

    private final FirebaseMessaging messaging;

    public FirebasePushService(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    public void send(UUID userId, String title, String body, Map<String, String> data) {
        List<String> deviceTokens = getDeviceTokens(userId); // Placeholder for fetching device tokens

        if (deviceTokens.isEmpty()) {
            log.info("No device tokens found for user {}. Skipping push notification.", userId);
            return;
        }

        deviceTokens.forEach(token -> {
            Message message = Message.builder()
                    .putAllData(data)
                    .setNotification(com.google.firebase.messaging.Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setToken(token)
                    .build();
            try {
                messaging.send(message);
                log.info("Successfully sent Firebase message to user {} token {}.", userId, token);
            } catch (Exception e) {
                log.error("Failed to send Firebase message to user {} token {}: {}", userId, token, e.getMessage(), e);
            }
        });
    }

    private List<String> getDeviceTokens(UUID userId) {
        // Placeholder: In a real application, you would fetch device tokens associated with the userId from a database.
        // For now, returning an empty list.
        log.warn("getDeviceTokens is a placeholder. No actual device tokens are being fetched for user {}.", userId);
        return List.of();
    }
}
