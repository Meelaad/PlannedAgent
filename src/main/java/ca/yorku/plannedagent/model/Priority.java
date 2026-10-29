package ca.yorku.plannedagent.model;

import java.util.Locale;

public enum Priority {
    LOW,
    MEDIUM,
    HIGH;

    /** Reads LOW, MEDIUM or HIGH in any case; anything else gives MEDIUM. */
    public static Priority parseOrMedium(String text) {
        if (text == null) {
            return MEDIUM;
        }
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }
}
