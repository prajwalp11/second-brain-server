package com.secondbrain.second_brain_server.external;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FirebasePushService {

    private final FirebaseMessaging messaging;

    public FirebasePushService(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    public void send(UUID userId, String title, String body, Map<String, String> data) {
        List<String> deviceTokens = getDeviceTokens(userId); // Placeholder for fetching device tokens

        if (deviceTokens.isEmpty()) {
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
            } catch (Exception e) {
                // Log error
                System.err.println("Failed to send Firebase message to token " + token + ": " + e.getMessage());
            }
        });
    }

    private List<String> getDeviceTokens(UUID userId) {
        // Placeholder: In a real application, you would fetch device tokens associated with the userId from a database.
        // For now, returning an empty list.
        return List.of();
    }
}
