package com.secondbrain.second_brain_server.service;

import com.secondbrain.second_brain_server.entities.User;
import com.secondbrain.second_brain_server.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Single source of truth for user-timezone-aware "today / this week / what hour is it".
 *
 * All date-boundary logic (Today's Focus, due dates, streaks, weekly stats, push windows, AI
 * "today's date") must go through this instead of bare {@code LocalDate.now()} / {@code LocalTime.now()},
 * which use the JVM zone. Resolution: the user's stored IANA zone if valid, else the configured
 * app-default zone. Week math is delegated to {@link DateUtil} (no duplication).
 */
@Service
@Slf4j
public class ClockService {

    private final ZoneId defaultZone;

    public ClockService(@Value("${app.default-timezone:Asia/Kolkata}") String defaultTimezone) {
        this.defaultZone = safeZone(defaultTimezone, ZoneId.of("UTC"));
    }

    /** The user's zone if set + valid, otherwise the app-default zone. Never throws. */
    public ZoneId zoneOf(User user) {
        if (user != null && user.getTimezone() != null && !user.getTimezone().isBlank()) {
            return safeZone(user.getTimezone(), defaultZone);
        }
        return defaultZone;
    }

    /** Current local date in the user's zone. */
    public LocalDate today(User user) {
        return LocalDate.now(zoneOf(user));
    }

    /** Current local date for an explicit zone (when only the zone is on hand). */
    public LocalDate today(ZoneId zone) {
        return LocalDate.now(zone != null ? zone : defaultZone);
    }

    /** Monday of the current week in the user's zone. */
    public LocalDate weekStart(User user) {
        return DateUtil.getWeekStart(today(user));
    }

    /** Current wall-clock time in the user's zone (for send-window / greeting checks). */
    public LocalTime nowTime(User user) {
        return LocalTime.now(zoneOf(user));
    }

    /** End of the user's current local day (used e.g. for push expiry). */
    public LocalDateTime endOfToday(User user) {
        return today(user).atTime(LocalTime.MAX);
    }

    public ZoneId getDefaultZone() {
        return defaultZone;
    }

    private ZoneId safeZone(String id, ZoneId fallback) {
        try {
            return ZoneId.of(id);
        } catch (Exception e) {
            log.warn("Invalid timezone '{}', falling back to {}", id, fallback);
            return fallback;
        }
    }
}
