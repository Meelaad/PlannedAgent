package ca.yorku.plannedagent.model;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** A rule the student told the agent, e.g. NO_WORK_AFTER 22:00. The scheduler and the conflict check obey it. */
public class Preference {

    public static final String NO_WORK_AFTER = "NO_WORK_AFTER";
    public static final String NO_WORK_BEFORE = "NO_WORK_BEFORE";
    public static final String DAY_OFF = "DAY_OFF";

    private final String rule;
    private final String value;
    private final String text;

    /** Checks and normalizes the rule and value; throws IllegalArgumentException with a readable message. */
    public Preference(String rule, String value, String text) {
        if (rule == null || rule.isBlank()) {
            throw new IllegalArgumentException("A preference needs a rule");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A preference needs a value");
        }
        this.rule = rule.trim().toUpperCase(Locale.ROOT);
        this.value = switch (this.rule) {
            case NO_WORK_AFTER, NO_WORK_BEFORE -> parseTime(value.trim()).toString();
            case DAY_OFF -> parseDay(value.trim()).name();
            default -> throw new IllegalArgumentException(
                    "Unknown rule " + rule + ". Use NO_WORK_AFTER, NO_WORK_BEFORE or DAY_OFF");
        };
        this.text = text == null ? "" : text.trim();
    }

    /** True if a block from start to end obeys this rule. */
    public boolean allows(LocalDateTime start, LocalDateTime end) {
        return switch (rule) {
            case NO_WORK_AFTER -> end.toLocalDate().equals(start.toLocalDate())
                    && !end.toLocalTime().isAfter(LocalTime.parse(value));
            case NO_WORK_BEFORE -> !start.toLocalTime().isBefore(LocalTime.parse(value));
            case DAY_OFF -> {
                DayOfWeek off = DayOfWeek.valueOf(value);
                yield start.getDayOfWeek() != off && end.minusNanos(1).getDayOfWeek() != off;
            }
            default -> true;
        };
    }

    public String describe() {
        return switch (rule) {
            case NO_WORK_AFTER -> "no work after " + value;
            case NO_WORK_BEFORE -> "no work before " + value;
            case DAY_OFF -> value.charAt(0) + value.substring(1).toLowerCase(Locale.ROOT) + " off";
            default -> rule + " " + value;
        };
    }

    private static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value.length() == 4 ? "0" + value : value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("'" + value + "' is not a time. Use HH:mm, for example 22:00");
        }
    }

    private static DayOfWeek parseDay(String value) {
        try {
            return DayOfWeek.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + value + "' is not a day. Use MONDAY to SUNDAY");
        }
    }

    public String getRule() {
        return rule;
    }

    public String getValue() {
        return value;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return rule + " " + value;
    }
}
