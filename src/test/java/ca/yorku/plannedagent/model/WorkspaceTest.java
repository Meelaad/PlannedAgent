package ca.yorku.plannedagent.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceTest {

    private static final LocalDateTime MONDAY_9 = LocalDateTime.of(2026, 10, 26, 9, 0);

    private Workspace workspace;
    private Task report;

    @BeforeEach
    void setUp() {
        workspace = new Workspace();
        report = new Task("EECS report", 120, MONDAY_9.plusDays(4), Priority.HIGH, false);
        workspace.addTask(report);
    }

    @Test
    void addTaskGivesAnId() {
        assertEquals("t1", report.getId());
        Task gym = new Task("Gym", 60, null, Priority.LOW, false);
        workspace.addTask(gym);
        assertEquals("t2", gym.getId());
        assertSame(gym, workspace.getTask("t2"));
        assertNull(workspace.getTask("t99"));
    }

    @Test
    void removeTaskAlsoRemovesItsBlocks() {
        workspace.getPlan().add(new TimeBlock("b1", report.getId(), MONDAY_9, MONDAY_9.plusHours(2)));
        workspace.removeTask(report.getId());
        assertTrue(workspace.getTasks().isEmpty());
        assertTrue(workspace.getPlan().getBlocks().isEmpty());
    }

    @Test
    void removeUnknownTaskFails() {
        assertThrows(IllegalArgumentException.class, () -> workspace.removeTask("t42"));
    }

    @Test
    void onlyOpenTasksNeedScheduling() {
        Task gym = new Task("Gym", 60, null, Priority.LOW, false);
        Task done = new Task("Quiz", 30, null, Priority.LOW, false);
        workspace.addTask(gym);
        workspace.addTask(done);
        gym.schedule();
        done.complete();
        assertEquals(1, workspace.tasksToSchedule().size());
        assertSame(report, workspace.tasksToSchedule().get(0));
    }

    @Test
    void busyBetweenIncludesBlocksAndEvents() {
        workspace.addEvent(new FixedEvent("Lecture", MONDAY_9, MONDAY_9.plusMinutes(80)));
        workspace.getPlan().add(new TimeBlock("b1", report.getId(), MONDAY_9.plusHours(3), MONDAY_9.plusHours(5)));

        assertEquals(2, workspace.busyBetween(MONDAY_9, MONDAY_9.plusHours(4)).size());
        assertEquals(1, workspace.busyBetween(MONDAY_9.plusHours(2), MONDAY_9.plusHours(4)).size());
        assertTrue(workspace.busyBetween(MONDAY_9.plusHours(6), MONDAY_9.plusHours(8)).isEmpty());
    }

    @Test
    void blocksOnReturnsOneDaySorted() {
        Plan plan = workspace.getPlan();
        plan.add(new TimeBlock("b2", "t1", MONDAY_9.plusHours(5), MONDAY_9.plusHours(6)));
        plan.add(new TimeBlock("b1", "t1", MONDAY_9, MONDAY_9.plusHours(1)));
        plan.add(new TimeBlock("b3", "t1", MONDAY_9.plusDays(1), MONDAY_9.plusDays(1).plusHours(1)));

        assertEquals(2, plan.blocksOn(LocalDate.of(2026, 10, 26)).size());
        assertEquals("b1", plan.blocksOn(LocalDate.of(2026, 10, 26)).get(0).getId());
    }

    @Test
    void planRejectsDuplicateBlockIds() {
        workspace.getPlan().add(new TimeBlock("b1", "t1", MONDAY_9, MONDAY_9.plusHours(1)));
        assertThrows(IllegalArgumentException.class,
                () -> workspace.getPlan().add(new TimeBlock("b1", "t1", MONDAY_9, MONDAY_9.plusHours(1))));
    }

    @Test
    void sameRuleReplacesTheOldValue() {
        workspace.addPreference(new Preference(Preference.NO_WORK_AFTER, "22:00", "no work after 10pm"));
        workspace.addPreference(new Preference(Preference.NO_WORK_AFTER, "21:00", "no work after 9pm"));
        workspace.addPreference(new Preference(Preference.DAY_OFF, "SUNDAY", "Sundays off"));
        workspace.addPreference(new Preference(Preference.DAY_OFF, "SATURDAY", "Saturdays off too"));

        assertEquals(3, workspace.getPreferences().size());
        assertEquals("21:00", workspace.getPreferences().stream()
                .filter(p -> p.getRule().equals(Preference.NO_WORK_AFTER)).findFirst().orElseThrow().getValue());
    }

    @Test
    void copyIsDeep() {
        workspace.getPlan().add(new TimeBlock("b1", report.getId(), MONDAY_9, MONDAY_9.plusHours(2)));
        Workspace copy = workspace.copy();

        copy.getTask("t1").schedule();
        copy.getPlan().find("b1").moveTo(MONDAY_9.plusDays(1));
        copy.addTask(new Task("Extra", 30, null, Priority.LOW, false));

        assertEquals("TODO", report.getStatus());
        assertEquals(MONDAY_9, workspace.getPlan().find("b1").getStart());
        assertEquals(1, workspace.getTasks().size());
        assertEquals("t2", copy.getTasks().get(1).getId());
    }
}
