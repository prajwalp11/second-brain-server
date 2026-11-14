package com.secondbrain.second_brain_server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.second_brain_server.dto.request.PushPrefs;
import com.secondbrain.second_brain_server.entities.*;
import com.secondbrain.second_brain_server.enums.*;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.external.ExpoPushClient;
import com.secondbrain.second_brain_server.external.ExpoPushClient.ExpoPushMessage;
import com.secondbrain.second_brain_server.external.ExpoPushClient.ExpoPushTicket;
import com.secondbrain.second_brain_server.repository.*;
import com.secondbrain.second_brain_server.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Single cohesive push service: device registration, per-type preferences, the PLANNER step
 * (enqueue eligible notifications into push_outbox) and the SENDER step (drain the outbox to Expo,
 * record history, prune dead tokens). Copy for each type is built here (like EmailService).
 *
 * Fail-soft contract (same as EmailService): send/dispatch failures are logged, never thrown, so
 * schedulers and request flows are never broken by push issues.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PushService {

    private final PushTokenRepository pushTokenRepository;
    private final PushOutboxRepository pushOutboxRepository;
    private final PushHistoryRepository pushHistoryRepository;
    private final ExpoPushClient expoPushClient;
    private final UserRepository userRepository;
    private final DomainRepository domainRepository;
    private final TaskRepository taskRepository;
    private final SessionLogRepository sessionLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${push.cooldown.task-due-hours:12}")
    private int cooldownTaskDueHours;
    @Value("${push.cooldown.streak-at-risk-hours:12}")
    private int cooldownStreakHours;
    @Value("${push.cooldown.weekly-insight-hours:144}")
    private int cooldownWeeklyHours;
    @Value("${push.sender.batch-size:100}")
    private int batchSize;
    @Value("${push.max-attempts:5}")
    private int maxAttempts;

    // Send windows (server-local for v1). "HH:mm-HH:mm".
    @Value("${push.window.morning:08:00-10:00}")
    private String morningWindow;
    @Value("${push.window.evening:18:00-21:00}")
    private String eveningWindow;

    private static final List<TaskStatus> OPEN = List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS);

    // ─── Registration / preferences (called from PushController) ─────────────────────────────

    @Transactional
    public void registerToken(UUID userId, String expoToken, PushPlatform platform) {
        PushToken token = pushTokenRepository.findByExpoToken(expoToken)
                .orElseGet(PushToken::new);
        token.setUserId(userId);
        token.setExpoToken(expoToken);
        token.setPlatform(platform);
        token.setEnabled(true);
        pushTokenRepository.save(token);
    }

    @Transactional
    public void disableToken(UUID userId, String expoToken) {
        pushTokenRepository.findByExpoToken(expoToken).ifPresent(t -> {
            if (t.getUserId().equals(userId)) {
                t.setEnabled(false);
                pushTokenRepository.save(t);
            }
        });
    }

    @Transactional
    public void updatePrefs(UUID userId, PushPrefs prefs) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        try {
            user.setPushPrefs(objectMapper.writeValueAsString(prefs));
            userRepository.save(user);
        } catch (Exception e) {
            log.warn("Failed to serialize push prefs for user {}: {}", userId, e.getMessage());
        }
    }

    // ─── PLANNER: enqueue eligible notifications (called by PushPlannerScheduler, per user) ──

    @Transactional
    public int planForUser(UUID userId) {
        if (pushTokenRepository.findByUserIdAndEnabledTrue(userId).isEmpty()) {
            return 0; // no device -> nothing to plan
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return 0;
        PushPrefs prefs = parsePrefs(user.getPushPrefs());

        int queued = 0;
        LocalTime now = LocalTime.now();

        if (prefs.isTaskDue() && inWindow(now, morningWindow)) {
            queued += planTaskDue(user);
        }
        if (prefs.isStreakAtRisk() && inWindow(now, eveningWindow)) {
            queued += planStreakAtRisk(user);
        }
        // WEEKLY_INSIGHT is enqueued by the weekly insight flow, not the 5-min planner.
        return queued;
    }

    private int planTaskDue(User user) {
        if (blocked(user.getId(), PushType.TASK_DUE)) return 0;
        LocalDate today = LocalDate.now();
        List<Task> dueToday = taskRepository
                .findByUserIdAndDueDateBetweenAndStatusIn(user.getId(), today, today, OPEN);
        if (dueToday.isEmpty()) return 0;

        int count = dueToday.size();
        String title = "You have " + count + (count == 1 ? " task" : " tasks") + " due today";
        String body = count == 1 ? dueToday.get(0).getTitle() : "Open Second Brain to tackle today's plan.";
        enqueue(user.getId(), PushType.TASK_DUE, title, body,
                Map.of("route", "/(tabs)"), endOfToday());
        return 1;
    }

    private int planStreakAtRisk(User user) {
        if (blocked(user.getId(), PushType.STREAK_AT_RISK)) return 0;
        LocalDate today = LocalDate.now();
        List<Domain> domains = domainRepository.findByUserIdAndStatus(user.getId(), DomainStatus.ACTIVE);
        for (Domain d : domains) {
            boolean trainingDay = DateUtil.isScheduledDay(d.getWeeklySchedule(), today);
            if (!trainingDay) continue;
            long logsToday = sessionLogRepository.countByDomainIdAndLogDateBetween(d.getId(), today, today);
            if (logsToday > 0) continue;
            String name = d.getCustomName() != null ? d.getCustomName() : d.getDomainType().name();
            enqueue(user.getId(), PushType.STREAK_AT_RISK,
                    "Keep your " + name + " streak alive",
                    "Today is a training day — log a session before midnight.",
                    Map.of("route", "/(tabs)", "domainId", d.getId().toString()), endOfToday());
            return 1; // one streak nudge per user per run (dedup/cooldown handle the rest)
        }
        return 0;
    }

    /**
     * Enqueue a WEEKLY_INSIGHT push. Called by the weekly insight flow when a user's recap is ready.
     * Respects prefs/cooldown/pending-guard like the others.
     */
    @Transactional
    public void enqueueWeeklyInsight(UUID userId, String title, String body) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;
        if (pushTokenRepository.findByUserIdAndEnabledTrue(userId).isEmpty()) return;
        if (!parsePrefs(user.getPushPrefs()).isWeeklyInsight()) return;
        if (blocked(userId, PushType.WEEKLY_INSIGHT)) return;
        enqueue(userId, PushType.WEEKLY_INSIGHT, title, body, Map.of("route", "/(tabs)/progress"), null);
    }

    /** True when a type is in cooldown or already has a PENDING row for this user. */
    private boolean blocked(UUID userId, PushType type) {
        if (pushOutboxRepository.existsByUserIdAndTypeAndStatus(userId, type, PushStatus.PENDING)) {
            return true;
        }
        return cooldownActive(userId, type);
    }

    private boolean cooldownActive(UUID userId, PushType type) {
        int hours = switch (type) {
            case TASK_DUE -> cooldownTaskDueHours;
            case STREAK_AT_RISK -> cooldownStreakHours;
            case WEEKLY_INSIGHT -> cooldownWeeklyHours;
        };
        return pushHistoryRepository.findFirstByUserIdAndTypeOrderBySentAtDesc(userId, type)
                .map(h -> h.getSentAt() != null
                        && h.getSentAt().isAfter(LocalDateTime.now().minusHours(hours)))
                .orElse(false);
    }

    private void enqueue(UUID userId, PushType type, String title, String body,
                         Map<String, Object> data, LocalDateTime expiresAt) {
        String dataJson = null;
        try {
            dataJson = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("Failed to serialize push data for user {} type {}: {}", userId, type, e.getMessage());
        }
        pushOutboxRepository.save(PushOutbox.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .data(dataJson)
                .status(PushStatus.PENDING)
                .attempts(0)
                .expiresAt(expiresAt)
                .build());
    }

    // ─── SENDER: drain the outbox to Expo (called by PushSenderScheduler) ────────────────────

    @Transactional
    public int dispatchPending() {
        List<PushOutbox> pending = pushOutboxRepository.findByStatusOrderByCreatedAtAsc(
                PushStatus.PENDING, PageRequest.of(0, batchSize));
        int sent = 0;
        LocalDateTime now = LocalDateTime.now();

        for (PushOutbox row : pending) {
            if (row.getExpiresAt() != null && now.isAfter(row.getExpiresAt())) {
                row.setStatus(PushStatus.FAILED); // stale -> never send
                pushOutboxRepository.save(row);
                continue;
            }
            List<PushToken> tokens = pushTokenRepository.findByUserIdAndEnabledTrue(row.getUserId());
            if (tokens.isEmpty()) {
                row.setStatus(PushStatus.FAILED);
                pushOutboxRepository.save(row);
                continue;
            }

            Object data = parseData(row.getData());
            List<ExpoPushMessage> messages = new ArrayList<>();
            for (PushToken t : tokens) {
                messages.add(new ExpoPushMessage(
                        t.getExpoToken(), row.getTitle(), row.getBody(), data, androidChannel(row.getType())));
            }

            row.setAttempts(row.getAttempts() + 1);
            List<ExpoPushTicket> tickets = expoPushClient.send(messages);

            boolean anyOk = false;
            String firstTicketId = null;
            for (int i = 0; i < tickets.size() && i < tokens.size(); i++) {
                ExpoPushTicket ticket = tickets.get(i);
                if ("ok".equals(ticket.status())) {
                    anyOk = true;
                    if (firstTicketId == null) firstTicketId = ticket.id();
                } else if (ExpoPushClient.isDeviceNotRegistered(ticket)) {
                    PushToken dead = tokens.get(i);
                    dead.setEnabled(false);
                    pushTokenRepository.save(dead);
                }
            }

            if (anyOk) {
                row.setStatus(PushStatus.SENT);
                row.setSentAt(now);
                pushOutboxRepository.save(row);
                writeHistory(row, PushStatus.SENT, firstTicketId);
                sent++;
            } else if (row.getAttempts() >= maxAttempts) {
                row.setStatus(PushStatus.FAILED);
                pushOutboxRepository.save(row);
                writeHistory(row, PushStatus.FAILED, null);
            } else {
                pushOutboxRepository.save(row); // stays PENDING -> retried next tick
            }
        }
        return sent;
    }

    private void writeHistory(PushOutbox row, PushStatus status, String ticketId) {
        pushHistoryRepository.save(PushHistory.builder()
                .userId(row.getUserId())
                .type(row.getType())
                .title(row.getTitle())
                .body(row.getBody())
                .data(row.getData())
                .status(status)
                .expoTicketId(ticketId)
                .sentAt(LocalDateTime.now())
                .build());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────────────────

    private PushPrefs parsePrefs(String json) {
        if (json == null || json.isBlank()) return PushPrefs.allEnabled();
        try {
            return objectMapper.readValue(json, PushPrefs.class);
        } catch (Exception e) {
            return PushPrefs.allEnabled();
        }
    }

    private Object parseData(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime endOfToday() {
        return LocalDate.now().atTime(LocalTime.MAX);
    }

    /** Android notification channel id per type (channels are created app-side). */
    private String androidChannel(PushType type) {
        return switch (type) {
            case STREAK_AT_RISK -> "streaks";
            case WEEKLY_INSIGHT -> "insights";
            default -> "reminders";
        };
    }

    private boolean inWindow(LocalTime now, String window) {
        try {
            String[] parts = window.split("-");
            LocalTime start = LocalTime.parse(parts[0].trim());
            LocalTime end = LocalTime.parse(parts[1].trim());
            return !now.isBefore(start) && !now.isAfter(end);
        } catch (Exception e) {
            return true; // misconfigured window -> don't block sending
        }
    }
}
