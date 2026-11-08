package ca.yorku.plannedagent.ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Reading and writing the date and time text used in the dialogs. */
final class Forms {

    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Forms() {
    }

    static String format(LocalDateTime time) {
        return time == null ? "" : DATE_TIME.format(time);
    }

    /** Empty text means no deadline. */
    static LocalDateTime parseDeadline(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            if (value.length() == 10) {
                return LocalDate.parse(value).atTime(23, 59);
            }
            return LocalDateTime.parse(value.replace('T', ' '), DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("The due date must look like 2026-11-12 17:00 (or be empty)");
        }
    }

    static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("The date must look like 2026-11-12");
        }
    }

    static LocalTime parseTime(String text) {
        String value = text.trim();
        try {
            return LocalTime.parse(value.length() == 4 ? "0" + value : value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Times must look like 09:30");
        }
    }
}
