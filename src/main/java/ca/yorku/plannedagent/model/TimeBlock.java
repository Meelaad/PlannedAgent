package ca.yorku.plannedagent.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/** A slot in the plan reserved for one task. Fixed events are also shown as blocks with no task. */
public class TimeBlock {

    private final String id;
    private final String taskId;
    private LocalDateTime start;
    private LocalDateTime end;

    public TimeBlock(String id, String taskId, LocalDateTime start, LocalDateTime end) {
        this.id = Objects.requireNonNull(id, "id");
        this.taskId = taskId;
        this.start = Objects.requireNonNull(start, "start");
        this.end = Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("A block must end after it starts");
        }
    }

    public void moveTo(LocalDateTime newStart) {
        Objects.requireNonNull(newStart, "newStart");
        Duration length = Duration.between(start, end);
        start = newStart;
        end = newStart.plus(length);
    }

    public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }

    public int getMinutes() {
        return (int) Duration.between(start, end).toMinutes();
    }

    public TimeBlock copy() {
        return new TimeBlock(id, taskId, start, end);
    }

    public String getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    @Override
    public String toString() {
        return id + " [" + start + " - " + end + "]";
    }
}
