package com.secondbrain.second_brain_server.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StreakCalculator {

    /**
     * Streak = number of logged days walking backward from today. ANY day with a log counts toward
     * the streak (not just scheduled days). The weekly schedule is used ONLY to break the streak:
     * if a scheduled day in the past has no log, the streak is broken at that point. Non-scheduled
     * days with no log are simply skipped (they neither extend nor break the streak). Today is never
     * treated as a "missed scheduled day" (the day isn't over yet) — it only counts if it was logged.
     */
    public static Integer compute(List<LocalDate> logDates, String weeklySchedule, LocalDate today) {
        if (logDates == null || logDates.isEmpty()) {
            return 0;
        }

        Set<LocalDate> loggedDays = new HashSet<>(logDates);
        LocalDate earliest = Collections.min(logDates);
        Set<DayOfWeek> scheduledDays = DateUtil.parseWeeklySchedule(weeklySchedule);

        int streak = 0;
        LocalDate checkDate = today;

        while (!checkDate.isBefore(earliest)) {
            if (loggedDays.contains(checkDate)) {
                // Any logged day counts, scheduled or not.
                streak++;
            } else if (scheduledDays.contains(checkDate.getDayOfWeek()) && checkDate.isBefore(today)) {
                // A scheduled day in the past with no log breaks the streak.
                break;
            }
            // Unlogged non-scheduled day (or unlogged today) → skip, keep going.
            checkDate = checkDate.minusDays(1);
        }
        return streak;
    }
}
