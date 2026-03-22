package com.secondbrain.second_brain_server.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class StreakCalculator {

    public static Integer compute(List<LocalDate> logDates, String weeklySchedule, LocalDate today) {
        if (logDates == null || logDates.isEmpty()) {
            return 0;
        }

        // Sort log dates to ensure proper iteration
        Collections.sort(logDates);

        int streak = 0;
        LocalDate checkDate = today;

        // Convert schedule string to a set of DayOfWeek for efficient lookup
        Set<DayOfWeek> scheduledDays = DateUtil.parseWeeklySchedule(weeklySchedule);

        // Iterate backwards from today
        while (true) {
            // If the current checkDate is before the earliest log, break
            if (checkDate.isBefore(logDates.get(0))) {
                break;
            }

            // Check if the current day is a scheduled day for the domain
            if (scheduledDays.contains(checkDate.getDayOfWeek())) {
                if (logDates.contains(checkDate)) {
                    streak++;
                } else {
                    // Missed a scheduled day, streak broken
                    break;
                }
            }
            // Move to the previous day
            checkDate = checkDate.minusDays(1);
        }
        return streak;
    }
}
