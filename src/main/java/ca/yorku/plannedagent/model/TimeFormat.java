package ca.yorku.plannedagent.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Short, readable date and time text used in messages. */
public final class TimeFormat {

    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("EEE MMM d HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private TimeFormat() {
    }

    public static String dayTime(LocalDateTime time) {
        return time == null ? "none" : DAY_TIME.format(time);
    }

    public static String time(LocalDateTime time) {
        return TIME.format(time);
    }

    public static String range(LocalDateTime start, LocalDateTime end) {
        return dayTime(start) + "-" + time(end);
    }
}
