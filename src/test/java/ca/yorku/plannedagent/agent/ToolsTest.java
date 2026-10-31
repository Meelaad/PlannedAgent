package ca.yorku.plannedagent.agent;

import ca.yorku.plannedagent.agent.tools.GetScheduleTool;
import ca.yorku.plannedagent.agent.tools.GetTasksTool;
import ca.yorku.plannedagent.agent.tools.ScheduleTasksTool;
import ca.yorku.plannedagent.model.FixedEvent;
import ca.yorku.plannedagent.model.Priority;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeBlock;
import ca.yorku.plannedagent.model.Workspace;
import ca.yorku.plannedagent.scheduling.ConflictChecker;
import ca.yorku.plannedagent.scheduling.Scheduler;
import ca.yorku.plannedagent.services.Forecast;
import ca.yorku.plannedagent.services.WeatherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static ca.yorku.plannedagent.agent.AgentTest.CLOCK;
import static ca.yorku.plannedagent.agent.AgentTest.NOW;
import static org.junit.jupiter.api.Assertions.*;

class ToolsTest {

    private Workspace workspace;
    private Task report;

    @BeforeEach
    void setUp() {
        workspace = new Workspace();
        report = new Task("EECS report", 180, NOW.plusDays(2), Priority.HIGH, false);
        workspace.addTask(report);
    }

    @Test
    void getTasksListsEverything() {
        ToolResult result = new GetTasksTool().run(Map.of(), workspace);
        assertTrue(result.ok);
        assertEquals("t1 | EECS report | 180 min | due Mon Nov 2 09:00 | HIGH | TODO", result.text);
        assertEquals("There are no tasks.", new GetTasksTool().run(Map.of(), new Workspace()).text);
    }

    @Test
    void getScheduleShowsEventsAndBlocksWithIds() {
        workspace.addEvent(new FixedEvent("Lecture", NOW.plusHours(1), NOW.plusHours(2)));
        workspace.getPlan().add(new TimeBlock("b7", "t1", NOW.plusHours(3), NOW.plusHours(6)));

        ToolResult result = new GetScheduleTool(CLOCK).run(Map.of("days", "2"), workspace);

        assertTrue(result.ok);
        assertTrue(result.text.contains("Saturday 2026-10-31:"));
        assertTrue(result.text.contains("e2 10:00-11:00 Lecture (fixed event)"));
        assertTrue(result.text.contains("b7 12:00-15:00 EECS report (task t1)"));
        assertTrue(result.text.contains("Sunday 2026-11-01: free"));
        assertFalse(result.text.contains("Monday"));
    }

    @Test
    void getScheduleChecksItsArguments() {
        GetScheduleTool tool = new GetScheduleTool(CLOCK);
        assertFalse(tool.run(Map.of("from", "next friday"), workspace).ok);
        assertFalse(tool.run(Map.of("days", "100"), workspace).ok);
        assertFalse(tool.run(Map.of("days", "a week"), workspace).ok);
        assertTrue(tool.run(Map.of("from", "2026-11-02", "days", "1.0"), workspace).ok);
    }

    @Test
    void scheduleTasksReturnsAProposalAndChangesNothing() {
        ToolResult result = scheduleTool((from, to) -> Forecast.none()).run(Map.of(), workspace);

        assertTrue(result.ok);
        assertEquals(1, result.proposal.getCommands().size());
        assertTrue(result.text.contains("Add 'EECS report' Sat Oct 31 09:00-12:00"));
        assertTrue(workspace.getPlan().getBlocks().isEmpty());
        assertEquals("TODO", report.getStatus());
    }

    @Test
    void scheduleTasksCarriesOnWithoutWeather() {
        ToolResult result = scheduleTool((from, to) -> {
            throw new IOException("offline");
        }).run(Map.of("strategy", "shortest"), workspace);

        assertTrue(result.ok);
        assertTrue(result.text.contains("weather service is not available"));
        assertNotNull(result.proposal);
    }

    @Test
    void scheduleTasksExplainsWhatDidNotFit() {
        Task huge = new Task("Thesis", 600, NOW.plusHours(5), Priority.HIGH, false);
        workspace.addTask(huge);

        ToolResult result = scheduleTool((from, to) -> Forecast.none()).run(Map.of(), workspace);

        assertTrue(result.text.contains("'Thesis': no free time before its deadline"));
    }

    @Test
    void scheduleTasksWithNothingToDo() {
        report.complete();
        ToolResult result = scheduleTool((from, to) -> Forecast.none()).run(Map.of(), workspace);
        assertTrue(result.ok);
        assertNull(result.proposal);
    }

    @Test
    void scheduleTasksChecksArguments() {
        ScheduleTasksTool tool = scheduleTool((from, to) -> Forecast.none());
        assertFalse(tool.run(Map.of("strategy", "fastest"), workspace).ok);
        assertFalse(tool.run(Map.of("days", "0"), workspace).ok);
    }

    private ScheduleTasksTool scheduleTool(WeatherService weather) {
        return new ScheduleTasksTool(new Scheduler(), weather, new ConflictChecker(), CLOCK);
    }
}
