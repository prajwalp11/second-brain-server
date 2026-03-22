package com.secondbrain.second_brain_server.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public class DateUtil {

    public static LocalDate getWeekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static LocalDate getWeekEnd(LocalDate date) {
        return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    public static boolean isScheduledDay(String weeklySchedule, LocalDate date) {
        // Assuming weeklySchedule is a 7-char string like "1111100" for Mon-Fri
        if (weeklySchedule == null || weeklySchedule.length() != 7) {
            return false; // Or handle as an error
        }
        DayOfWeek day = date.getDayOfWeek();
        return weeklySchedule.charAt(day.getValue() - 1) == '1';
    }
}
