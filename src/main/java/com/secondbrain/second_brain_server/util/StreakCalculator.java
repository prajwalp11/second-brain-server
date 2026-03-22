package com.secondbrain.second_brain_server.util;

import java.time.LocalDate;
import java.util.List;

public class StreakCalculator {

    public static Integer compute(List<LocalDate> logDates, String schedule, LocalDate today) {
        // This is a placeholder for a more complex streak calculation logic
        // that would need to account for the user's schedule.
        if (logDates == null || logDates.isEmpty()) {
            return 0;
        }
        // A simple implementation: count consecutive days from today.
        int streak = 0;
        LocalDate checkDate = today;
        while (logDates.contains(checkDate)) {
            streak++;
            checkDate = checkDate.minusDays(1);
        }
        return streak;
    }
}
