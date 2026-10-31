package ca.yorku.plannedagent.scheduling;

import java.util.List;
import java.util.Locale;

/** Turns the name a student or the agent types into a strategy object. */
public final class SchedulingStrategies {

    public static final List<String> NAMES = List.of("deadline", "priority", "shortest");

    private SchedulingStrategies() {
    }

    /** Returns null for an unknown name. */
    public static SchedulingStrategy byName(String name) {
        if (name == null) {
            return null;
        }
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "deadline", "deadline first", "deadline_first" -> new DeadlineFirstStrategy();
            case "priority", "priority first", "priority_first" -> new PriorityFirstStrategy();
            case "shortest", "shortest first", "shortest_first" -> new ShortestFirstStrategy();
            default -> null;
        };
    }
}
