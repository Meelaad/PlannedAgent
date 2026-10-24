package ca.yorku.plannedagent.scheduling;

import ca.yorku.plannedagent.model.Priority;
import ca.yorku.plannedagent.model.Task;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchedulingStrategyTest {

    private static final LocalDateTime FRIDAY = LocalDateTime.of(2026, 10, 30, 17, 0);

    private final Task report = new Task("Report", 180, FRIDAY, Priority.MEDIUM, false);
    private final Task quiz = new Task("Quiz prep", 60, FRIDAY.minusDays(2), Priority.LOW, false);
    private final Task gym = new Task("Gym", 45, null, Priority.HIGH, false);
    private final List<Task> tasks = List.of(report, quiz, gym);

    @Test
    void deadlineFirstPutsTasksWithoutDeadlineLast() {
        assertEquals(List.of(quiz, report, gym), new DeadlineFirstStrategy().sort(tasks));
    }

    @Test
    void priorityFirstOrdersHighToLow() {
        assertEquals(List.of(gym, report, quiz), new PriorityFirstStrategy().sort(tasks));
    }

    @Test
    void shortestFirstOrdersByLength() {
        assertEquals(List.of(gym, quiz, report), new ShortestFirstStrategy().sort(tasks));
    }

    @Test
    void deadlineTieGoesToHigherPriority() {
        Task urgent = new Task("Urgent", 30, FRIDAY, Priority.HIGH, false);
        assertEquals(List.of(urgent, report), new DeadlineFirstStrategy().sort(List.of(report, urgent)));
    }

    @Test
    void sortDoesNotChangeTheInput() {
        List<Task> input = new java.util.ArrayList<>(tasks);
        new ShortestFirstStrategy().sort(input);
        assertEquals(tasks, input);
    }

    @Test
    void namesAreTheOnesTheUserTypes() {
        assertEquals("deadline", new DeadlineFirstStrategy().name());
        assertEquals("priority", new PriorityFirstStrategy().name());
        assertEquals("shortest", new ShortestFirstStrategy().name());
    }
}
