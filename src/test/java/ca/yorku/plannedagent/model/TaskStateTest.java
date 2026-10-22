package ca.yorku.plannedagent.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TaskStateTest {

    private Task task;

    @BeforeEach
    void setUp() {
        task = new Task("EECS report", 180, LocalDateTime.of(2026, 10, 23, 17, 0), Priority.HIGH, false);
    }

    @Test
    void newTaskIsTodoAndNeedsScheduling() {
        assertEquals("TODO", task.getStatus());
        assertTrue(task.needsScheduling());
    }

    @Test
    void scheduleThenUnscheduleGoesBackToTodo() {
        task.schedule();
        assertEquals("SCHEDULED", task.getStatus());
        assertFalse(task.needsScheduling());
        task.unschedule();
        assertEquals("TODO", task.getStatus());
    }

    @Test
    void missedTaskNeedsSchedulingAgain() {
        task.schedule();
        task.markMissed();
        assertEquals("MISSED", task.getStatus());
        assertTrue(task.needsScheduling());
        task.schedule();
        assertEquals("SCHEDULED", task.getStatus());
    }

    @Test
    void anyOpenTaskCanBeCompleted() {
        task.complete();
        assertEquals("DONE", task.getStatus());

        Task scheduled = new Task("Gym", 60, null, Priority.LOW, false);
        scheduled.schedule();
        scheduled.complete();
        assertEquals("DONE", scheduled.getStatus());

        Task missed = new Task("Groceries", 45, null, Priority.LOW, false);
        missed.schedule();
        missed.markMissed();
        missed.complete();
        assertEquals("DONE", missed.getStatus());
    }

    @Test
    void doneTaskRefusesEveryChange() {
        task.complete();
        IllegalStateException e = assertThrows(IllegalStateException.class, task::complete);
        assertEquals("'EECS report' is already done", e.getMessage());
        assertThrows(IllegalStateException.class, task::schedule);
        assertThrows(IllegalStateException.class, task::unschedule);
        assertThrows(IllegalStateException.class, task::markMissed);
        assertFalse(task.needsScheduling());
    }

    @Test
    void todoTaskCannotBeMissedOrUnscheduled() {
        assertThrows(IllegalStateException.class, task::markMissed);
        assertThrows(IllegalStateException.class, task::unschedule);
        assertEquals("TODO", task.getStatus());
    }

    @Test
    void scheduledTaskCannotBeScheduledTwice() {
        task.schedule();
        assertThrows(IllegalStateException.class, task::schedule);
    }

    @Test
    void missedTaskCannotBeMissedTwice() {
        task.schedule();
        task.markMissed();
        assertThrows(IllegalStateException.class, task::markMissed);
        assertThrows(IllegalStateException.class, task::unschedule);
    }

    @Test
    void copyKeepsStatusButIsSeparate() {
        task.schedule();
        Task copy = task.copy();
        copy.unschedule();
        assertEquals("SCHEDULED", task.getStatus());
        assertEquals("TODO", copy.getStatus());
    }

    @Test
    void missingPriorityDefaultsToMedium() {
        Task t = new Task("Read chapter 4", 30, null, null, false);
        assertEquals(Priority.MEDIUM, t.getPriority());
    }
}
