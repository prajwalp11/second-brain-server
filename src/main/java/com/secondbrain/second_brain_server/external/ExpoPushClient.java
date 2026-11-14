package com.secondbrain.second_brain_server.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin client for Expo's free Push API (https://exp.host/--/api/v2/push/send). Expo relays to APNs
 * (iOS) and FCM (Android) — the server holds no Apple/Firebase credentials (those live in EAS).
 *
 * Fail-soft like {@code EmailService}/{@code YouTubeService}: network/parse errors are logged and
 * surface as per-message error tickets, never thrown, so the sender job is never broken.
 */
@Component
@Slf4j
public class ExpoPushClient {

    private static final String ENDPOINT = "https://exp.host/--/api/v2/push/send";
    private static final int MAX_BATCH = 100;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ExpoPushClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /** A message to one device. */
    public record ExpoPushMessage(String to, String title, String body, Object data, String channelId) {}

    /** Per-message result. status "ok" => delivered to Expo; "error" + errorCode on failure. */
    public record ExpoPushTicket(String id, String status, String errorCode) {}

    /**
     * Sends messages in batches of ≤100. Returns one ticket per input message, in order. A whole
     * batch that fails transport yields error tickets for that batch (so callers can retry/age out).
     */
    public List<ExpoPushTicket> send(List<ExpoPushMessage> messages) {
        List<ExpoPushTicket> tickets = new ArrayList<>();
        if (messages == null || messages.isEmpty()) {
            return tickets;
        }
        for (int i = 0; i < messages.size(); i += MAX_BATCH) {
            List<ExpoPushMessage> batch = messages.subList(i, Math.min(i + MAX_BATCH, messages.size()));
            tickets.addAll(sendBatch(batch));
        }
        return tickets;
    }

    private List<ExpoPushTicket> sendBatch(List<ExpoPushMessage> batch) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            String payload = objectMapper.writeValueAsString(batch.stream().map(m -> {
                var map = new java.util.HashMap<String, Object>();
                map.put("to", m.to());
                map.put("title", m.title());
                map.put("body", m.body());
                if (m.data() != null) map.put("data", m.data());
                if (m.channelId() != null) map.put("channelId", m.channelId());
                return map;
            }).toList());

            String response = restTemplate.postForObject(
                    ENDPOINT, new HttpEntity<>(payload, headers), String.class);
            return parseTickets(response, batch.size());
        } catch (Exception e) {
            log.warn("Expo push batch of {} failed: {}", batch.size(), e.getMessage());
            return errorTickets(batch.size(), "TransportError");
        }
    }

    private List<ExpoPushTicket> parseTickets(String response, int expected) {
        List<ExpoPushTicket> tickets = new ArrayList<>();
        try {
            JsonNode data = objectMapper.readTree(response).path("data");
            if (data.isArray()) {
                for (JsonNode node : data) {
                    String status = node.path("status").asText("error");
                    String id = node.path("id").asText(null);
                    String errorCode = node.path("details").path("error").asText(null);
                    tickets.add(new ExpoPushTicket(id, status, errorCode));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Expo push response: {}", e.getMessage());
        }
        // Pad/truncate defensively so the caller always gets one ticket per message.
        while (tickets.size() < expected) {
            tickets.add(new ExpoPushTicket(null, "error", "NoTicket"));
        }
        return tickets.size() > expected ? tickets.subList(0, expected) : tickets;
    }

    private List<ExpoPushTicket> errorTickets(int n, String code) {
        List<ExpoPushTicket> tickets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            tickets.add(new ExpoPushTicket(null, "error", code));
        }
        return tickets;
    }

    /** True when a ticket's error means the token is dead and should be disabled. */
    public static boolean isDeviceNotRegistered(ExpoPushTicket ticket) {
        return ticket != null && "DeviceNotRegistered".equals(ticket.errorCode());
    }
}
