package ca.yorku.plannedagent.model;

import java.time.LocalDateTime;
import java.util.Objects;

/** A commitment the scheduler must work around, e.g. a lecture or a work shift. */
public class FixedEvent {

    private String id;
    private final String title;
    private final LocalDateTime start;
    private final LocalDateTime end;

    public FixedEvent(String title, LocalDateTime start, LocalDateTime end) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("An event needs a title");
        }
        this.title = title.trim();
        this.start = Objects.requireNonNull(start, "start");
        this.end = Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("An event must end after it starts");
        }
    }

    public TimeBlock toBlock() {
        return new TimeBlock(id == null ? "event" : id, null, start, end);
    }

    public String getId() {
        return id;
    }

    void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getEnd() {
        return end;
    }
}
