package ca.yorku.plannedagent.model;

import java.util.List;
import java.util.Objects;

/** A problem found in the plan. */
public class Conflict {

    public static final String OVERLAP = "OVERLAP";
    public static final String LATE = "LATE";
    public static final String RAIN = "RAIN";
    public static final String PREFERENCE = "PREFERENCE";

    private final String type;
    private final String message;
    private final List<String> blockIds;

    public Conflict(String type, String message, List<String> blockIds) {
        this.type = Objects.requireNonNull(type, "type");
        this.message = Objects.requireNonNull(message, "message");
        this.blockIds = List.copyOf(blockIds);
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getBlockIds() {
        return blockIds;
    }

    @Override
    public String toString() {
        return type + ": " + message;
    }
}
