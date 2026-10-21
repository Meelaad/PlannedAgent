package ca.yorku.plannedagent.model;

import java.util.Objects;

/** A rule the student told the agent, e.g. NO_WORK_AFTER 22:00. */
public class Preference {

    public static final String NO_WORK_AFTER = "NO_WORK_AFTER";
    public static final String NO_WORK_BEFORE = "NO_WORK_BEFORE";
    public static final String DAY_OFF = "DAY_OFF";

    private final String rule;
    private final String value;
    private final String text;

    public Preference(String rule, String value, String text) {
        this.rule = Objects.requireNonNull(rule, "rule");
        this.value = Objects.requireNonNull(value, "value");
        this.text = text == null ? "" : text;
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
