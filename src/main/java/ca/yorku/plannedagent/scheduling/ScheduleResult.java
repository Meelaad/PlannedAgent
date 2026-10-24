package ca.yorku.plannedagent.scheduling;

import ca.yorku.plannedagent.model.TimeBlock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScheduleResult {

    private final List<TimeBlock> placed;
    private final Map<String, String> notPlaced;

    public ScheduleResult(List<TimeBlock> placed, Map<String, String> notPlaced) {
        this.placed = List.copyOf(placed);
        this.notPlaced = Collections.unmodifiableMap(new LinkedHashMap<>(notPlaced));
    }

    public List<TimeBlock> getPlaced() {
        return placed;
    }

    /** Task id to the reason it could not be placed. */
    public Map<String, String> getNotPlaced() {
        return notPlaced;
    }
}
