package com.secondbrain.second_brain_server.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for date operations including week boundaries
 * and weekly schedule parsing.
 */
public final class DateUtil {

    private DateUtil() {
        // Prevent instantiation
    }

    // ─── Schedule format constants ───────────────────────────────────────────────

    private static final String SCHEDULE_DELIMITER = ",";

    /**
     * Maps common day-of-week abbreviations and full names to Java DayOfWeek.
     * Supports: MON/MONDAY, TUE/TUES/TUESDAY, WED/WEDNESDAY,
     *           THU/THURS/THURSDAY, FRI/FRIDAY, SAT/SATURDAY, SUN/SUNDAY
     */
    private static final Map<String, DayOfWeek> DAY_NAME_TO_DAY_OF_WEEK = Map.ofEntries(
            // Monday
            Map.entry("MON", DayOfWeek.MONDAY),
            Map.entry("MONDAY", DayOfWeek.MONDAY),
            // Tuesday
            Map.entry("TUE", DayOfWeek.TUESDAY),
            Map.entry("TUES", DayOfWeek.TUESDAY),
            Map.entry("TUESDAY", DayOfWeek.TUESDAY),
            // Wednesday
            Map.entry("WED", DayOfWeek.WEDNESDAY),
            Map.entry("WEDNESDAY", DayOfWeek.WEDNESDAY),
            // Thursday
            Map.entry("THU", DayOfWeek.THURSDAY),
            Map.entry("THURS", DayOfWeek.THURSDAY),
            Map.entry("THURSDAY", DayOfWeek.THURSDAY),
            // Friday
            Map.entry("FRI", DayOfWeek.FRIDAY),
            Map.entry("FRIDAY", DayOfWeek.FRIDAY),
            // Saturday
            Map.entry("SAT", DayOfWeek.SATURDAY),
            Map.entry("SATURDAY", DayOfWeek.SATURDAY),
            // Sunday
            Map.entry("SUN", DayOfWeek.SUNDAY),
            Map.entry("SUNDAY", DayOfWeek.SUNDAY)
    );

    // ─── Week boundary methods ───────────────────────────────────────────────────

    /**
     * Returns the Monday of the week containing the given date.
     */
    public static LocalDate getWeekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Returns the Sunday of the week containing the given date.
     */
    public static LocalDate getWeekEnd(LocalDate date) {
        return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    // ─── Schedule parsing ────────────────────────────────────────────────────────

    /**
     * Checks if the given date falls on a scheduled day according to the weekly schedule.
     *
     * @param weeklySchedule comma-separated day names (e.g. "MON,WED,SAT")
     * @param date           the date to check
     * @return true if the date's day-of-week is in the schedule, false otherwise
     */
    public static boolean isScheduledDay(String weeklySchedule, LocalDate date) {
        Set<DayOfWeek> scheduledDays = parseWeeklySchedule(weeklySchedule);
        return scheduledDays.contains(date.getDayOfWeek());
    }

    /**
     * Parses a comma-separated weekly schedule string into a set of DayOfWeek values.
     * Handles abbreviated (MON, TUE, SAT) and full (MONDAY, TUESDAY, SATURDAY) names.
     * Unrecognized tokens are silently skipped.
     *
     * @param weeklySchedule comma-separated day names, case-insensitive (e.g. "Mon,Wed,Sat")
     * @return unmodifiable set of scheduled days, empty set if input is null/blank
     */
    public static Set<DayOfWeek> parseWeeklySchedule(String weeklySchedule) {
        if (weeklySchedule == null || weeklySchedule.isBlank()) {
            return Collections.emptySet();
        }

        Set<DayOfWeek> days = Arrays.stream(weeklySchedule.split(SCHEDULE_DELIMITER))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isEmpty())
                .map(DAY_NAME_TO_DAY_OF_WEEK::get)
                .filter(day -> day != null)
                .collect(Collectors.toUnmodifiableSet());

        return days;
    }
}
