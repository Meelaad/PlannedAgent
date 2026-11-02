package ca.yorku.plannedagent.agent;

import ca.yorku.plannedagent.agent.tools.MoveTaskTool;
import ca.yorku.plannedagent.agent.tools.RememberPreferenceTool;
import ca.yorku.plannedagent.commands.CommandHistory;
import ca.yorku.plannedagent.model.FixedEvent;
import ca.yorku.plannedagent.model.Preference;
import ca.yorku.plannedagent.model.Priority;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeBlock;
import ca.yorku.plannedagent.model.Workspace;
import ca.yorku.plannedagent.scheduling.ConflictChecker;
import ca.yorku.plannedagent.services.Forecast;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import static ca.yorku.plannedagent.agent.AgentTest.CLOCK;
import static ca.yorku.plannedagent.agent.AgentTest.NOW;
import static org.junit.jupiter.api.Assertions.*;

class ChangeToolsTest {

    private static final LocalDateTime TUESDAY_18 = LocalDateTime.of(2026, 11, 3, 18, 0);

    private Workspace workspace;
    private MoveTaskTool move;

    @BeforeEach
    void setUp() {
        workspace = new Workspace();
        Task gym = new Task("Gym", 60, null, Priority.MEDIUM, false);
        workspace.addTask(gym);
        gym.schedule();
        workspace.getPlan().add(new TimeBlock("b5", gym.getId(), TUESDAY_18, TUESDAY_18.plusHours(1)));
        workspace.addEvent(new FixedEvent("Lab", TUESDAY_18.plusDays(1).withHour(7), TUESDAY_18.plusDays(1).withHour(9)));
        move = new MoveTaskTool(new ConflictChecker(), (from, to) -> Forecast.none(), CLOCK);
    }

    @Test
    void proposesAMoveWithoutChangingThePlan() {
        ToolResult result = move.run(Map.of("blockId", "b5", "newStart", "2026-11-04T10:00"), workspace);

        assertTrue(result.ok);
        assertFalse(result.proposal.hasConflicts());
        assertTrue(result.text.startsWith("Move 'Gym' to Wed Nov 4 10:00."));
        assertEquals(TUESDAY_18, workspace.getPlan().find("b5").getStart());

        new CommandHistory().run(result.proposal.getCommands(), workspace);
        assertEquals(LocalDateTime.of(2026, 11, 4, 10, 0), workspace.getPlan().find("b5").getStart());
    }

    @Test
    void moveIntoAnEventIsFlagged() {
        ToolResult result = move.run(Map.of("blockId", "b5", "newStart", "2026-11-04T07:30"), workspace);

        assertTrue(result.proposal.hasConflicts());
        assertTrue(result.text.contains("cannot be applied"));
        assertTrue(result.text.contains("'Lab'"));
    }

    @Test
    void weekdayAndTimeAreTurnedIntoADateInJava() {
        assertEquals("Move 'Gym' to Wed Nov 4 10:00.", firstLine(Map.of("blockId", "b5", "day", "wednesday", "time", "10:00")));
        assertEquals("Move 'Gym' to Sat Oct 31 10:00.", firstLine(Map.of("blockId", "b5", "day", "SATURDAY", "time", "10:00")));
        assertEquals("Move 'Gym' to Sat Nov 7 08:00.", firstLine(Map.of("blockId", "b5", "day", "SATURDAY", "time", "08:00")));
        assertEquals("Move 'Gym' to Sun Nov 1 09:30.", firstLine(Map.of("blockId", "b5", "day", "TOMORROW", "time", "9:30")));
        assertEquals("Move 'Gym' to Thu Nov 5 16:00.", firstLine(Map.of("blockId", "b5", "day", "2026-11-05", "time", "16:00")));
    }

    @Test
    void badDayOrTimeIsRejected() {
        assertFalse(move.run(Map.of("blockId", "b5", "day", "someday", "time", "10:00"), workspace).ok);
        assertFalse(move.run(Map.of("blockId", "b5", "day", "WEDNESDAY", "time", "7am"), workspace).ok);
        assertFalse(move.run(Map.of("blockId", "b5", "day", "WEDNESDAY"), workspace).ok);
    }

    @Test
    void taskIdIsAcceptedWhenItHasOneBlock() {
        ToolResult result = move.run(Map.of("blockId", "t1", "newStart", "2026-11-04T10:00:00"), workspace);
        assertTrue(result.ok);
    }

    @Test
    void badArgumentsChangeNothing() {
        assertFalse(move.run(Map.of("newStart", "2026-11-04T10:00"), workspace).ok);
        assertFalse(move.run(Map.of("blockId", "b99", "newStart", "2026-11-04T10:00"), workspace).ok);
        assertFalse(move.run(Map.of("blockId", "b5", "newStart", "Wednesday 7am"), workspace).ok);
        assertFalse(move.run(Map.of("blockId", "b5", "newStart", NOW.minusDays(1).toString()), workspace).ok);
        assertFalse(move.run(Map.of("blockId", "b5", "newStart", "2026-11-03T18:00"), workspace).ok);
        assertEquals(TUESDAY_18, workspace.getPlan().find("b5").getStart());
    }

    @Test
    void fixedEventsCannotBeMoved() {
        ToolResult result = move.run(Map.of("blockId", "e2", "newStart", "2026-11-04T10:00"), workspace);
        assertFalse(result.ok);
        assertTrue(result.text.contains("fixed event"));
    }

    @Test
    void movingAnOutdoorTaskIntoRainIsFlagged() {
        Task run = new Task("Run", 45, null, Priority.LOW, true);
        workspace.addTask(run);
        run.schedule();
        workspace.getPlan().add(new TimeBlock("b8", run.getId(), TUESDAY_18.withHour(8), TUESDAY_18.withHour(8).plusMinutes(45)));
        LocalDateTime target = LocalDateTime.of(2026, 11, 5, 16, 0);
        MoveTaskTool wetMove = new MoveTaskTool(new ConflictChecker(),
                (from, to) -> new Forecast(Map.of(target, 85)), CLOCK);

        ToolResult result = wetMove.run(Map.of("blockId", "b8", "newStart", "2026-11-05T16:00"), workspace);

        assertTrue(result.proposal.hasConflicts());
        assertTrue(result.text.contains("85% chance of rain"));
    }

    @Test
    void weatherOutageIsOnlyAWarning() {
        MoveTaskTool offline = new MoveTaskTool(new ConflictChecker(), (from, to) -> {
            throw new IOException("offline");
        }, CLOCK);
        ToolResult result = offline.run(Map.of("blockId", "b5", "newStart", "2026-11-04T10:00"), workspace);
        assertTrue(result.ok);
        assertTrue(result.text.contains("weather service is not available"));
    }

    private String firstLine(Map<String, String> args) {
        ToolResult result = move.run(args, workspace);
        assertTrue(result.ok, result.text);
        return result.text.lines().findFirst().orElseThrow();
    }

    @Test
    void remembersAValidRule() {
        ToolResult result = new RememberPreferenceTool().run(
                Map.of("rule", "NO_WORK_AFTER", "value", "22:00", "text", "I never study after 10pm"), workspace);

        assertTrue(result.ok);
        Preference saved = workspace.getPreferences().get(0);
        assertEquals("22:00", saved.getValue());
        assertEquals("I never study after 10pm", saved.getText());
    }

    @Test
    void rejectsABadRuleSoTheAgentCanAskAgain() {
        RememberPreferenceTool tool = new RememberPreferenceTool();
        ToolResult badTime = tool.run(Map.of("rule", "NO_WORK_AFTER", "value", "25:00"), workspace);
        ToolResult badRule = tool.run(Map.of("rule", "NO_MEETINGS", "value", "10:00"), workspace);
        ToolResult missing = tool.run(Map.of(), workspace);

        assertFalse(badTime.ok);
        assertTrue(badTime.text.contains("not a time"));
        assertFalse(badRule.ok);
        assertFalse(missing.ok);
        assertTrue(workspace.getPreferences().isEmpty());
    }
}
