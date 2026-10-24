package ca.yorku.plannedagent.scheduling;

import ca.yorku.plannedagent.model.FixedEvent;
import ca.yorku.plannedagent.model.Priority;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeBlock;
import ca.yorku.plannedagent.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SchedulerTest {

    private static final LocalDateTime MONDAY_8 = LocalDateTime.of(2026, 10, 26, 8, 0);

    private Workspace workspace;
    private Scheduler scheduler;

    @BeforeEach
    void setUp() {
        workspace = new Workspace();
        scheduler = new Scheduler();
    }

    @Test
    void placesTaskInFirstFreeSlotAfterAnEvent() {
        workspace.addEvent(new FixedEvent("Lecture", MONDAY_8, MONDAY_8.plusMinutes(90)));
        Task report = task("Report", 120, null, Priority.MEDIUM);

        ScheduleResult result = scheduler.schedule(workspace, MONDAY_8, MONDAY_8.plusDays(7));

        assertEquals(1, result.getPlaced().size());
        TimeBlock block = result.getPlaced().get(0);
        assertEquals(report.getId(), block.getTaskId());
        assertEquals(MONDAY_8.plusMinutes(90), block.getStart());
        assertEquals(MONDAY_8.plusMinutes(210), block.getEnd());
    }

    @Test
    void placedTasksDoNotOverlapEachOther() {
        task("A", 60, null, Priority.MEDIUM);
        task("B", 60, null, Priority.MEDIUM);

        ScheduleResult result = scheduler.schedule(workspace, MONDAY_8, MONDAY_8.plusDays(1));

        TimeBlock a = result.getPlaced().get(0);
        TimeBlock b = result.getPlaced().get(1);
        assertFalse(a.overlaps(b.getStart(), b.getEnd()));
        assertEquals(MONDAY_8.plusHours(1), b.getStart());
    }

    @Test
    void doesNotChangeTheWorkspace() {
        task("Report", 60, null, Priority.MEDIUM);
        scheduler.schedule(workspace, MONDAY_8, MONDAY_8.plusDays(1));
        assertTrue(workspace.getPlan().getBlocks().isEmpty());
        assertEquals("TODO", workspace.getTask("t1").getStatus());
    }

    @Test
    void startsAtTheNextHalfHour() {
        task("Report", 60, null, Priority.MEDIUM);
        ScheduleResult result = scheduler.schedule(workspace, MONDAY_8.plusMinutes(47), MONDAY_8.plusDays(1));
        assertEquals(MONDAY_8.plusMinutes(60), result.getPlaced().get(0).getStart());
    }

    @Test
    void staysInsideDayHours() {
        task("Late work", 120, null, Priority.MEDIUM);
        LocalDateTime evening = MONDAY_8.withHour(21);

        ScheduleResult result = scheduler.schedule(workspace, evening, evening.plusDays(1));

        TimeBlock block = result.getPlaced().get(0);
        assertEquals(MONDAY_8.plusDays(1), block.getStart());
    }

    @Test
    void reportsTaskThatCannotFitBeforeDeadline() {
        workspace.addEvent(new FixedEvent("Shift", MONDAY_8, MONDAY_8.plusHours(14)));
        Task report = task("Report", 120, MONDAY_8.plusHours(10), Priority.HIGH);

        ScheduleResult result = scheduler.schedule(workspace, MONDAY_8, MONDAY_8.plusDays(7));

        assertTrue(result.getPlaced().isEmpty());
        assertEquals("no free time before its deadline", result.getNotPlaced().get(report.getId()));
    }

    @Test
    void reportsDeadlineThatAlreadyPassed() {
        Task old = task("Old", 30, MONDAY_8.minusDays(1), Priority.LOW);
        ScheduleResult result = scheduler.schedule(workspace, MONDAY_8, MONDAY_8.plusDays(7));
        assertEquals("its deadline has already passed", result.getNotPlaced().get(old.getId()));
    }

    @Test
    void strategyDecidesWhoGetsTheOnlySlot() {
        workspace.addEvent(new FixedEvent("Busy", MONDAY_8.plusHours(1), MONDAY_8.plusHours(14)));
        Task low = task("Low", 60, MONDAY_8.plusHours(1), Priority.LOW);
        Task high = task("High", 60, MONDAY_8.plusHours(1), Priority.HIGH);

        scheduler.setStrategy(new ShortestFirstStrategy());
        assertEquals(low.getId(), scheduler.schedule(workspace, MONDAY_8, MONDAY_8.plusDays(1))
                .getPlaced().get(0).getTaskId());

        scheduler.setStrategy(new PriorityFirstStrategy());
        assertEquals(high.getId(), scheduler.schedule(workspace, MONDAY_8, MONDAY_8.plusDays(1))
                .getPlaced().get(0).getTaskId());
    }

    @Test
    void skipsTasksThatAreAlreadyScheduledOrDone() {
        Task scheduled = task("Scheduled", 30, null, Priority.LOW);
        Task done = task("Done", 30, null, Priority.LOW);
        scheduled.schedule();
        done.complete();
        ScheduleResult result = scheduler.schedule(workspace, MONDAY_8, MONDAY_8.plusDays(1));
        assertTrue(result.getPlaced().isEmpty());
        assertTrue(result.getNotPlaced().isEmpty());
    }

    @Test
    void roundUpHandlesSeconds() {
        assertEquals(MONDAY_8.plusMinutes(30), Scheduler.roundUp(MONDAY_8.plusSeconds(1)));
        assertEquals(MONDAY_8, Scheduler.roundUp(MONDAY_8));
    }

    private Task task(String title, int minutes, LocalDateTime deadline, Priority priority) {
        Task task = new Task(title, minutes, deadline, priority, false);
        workspace.addTask(task);
        return task;
    }
}
