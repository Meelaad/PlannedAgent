package ca.yorku.plannedagent.agent;

import ca.yorku.plannedagent.agent.tools.GetScheduleTool;
import ca.yorku.plannedagent.agent.tools.GetTasksTool;
import ca.yorku.plannedagent.agent.tools.ScheduleTasksTool;
import ca.yorku.plannedagent.ai.LlmException;
import ca.yorku.plannedagent.app.EventBus;
import ca.yorku.plannedagent.model.Preference;
import ca.yorku.plannedagent.model.Priority;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.Workspace;
import ca.yorku.plannedagent.scheduling.ConflictChecker;
import ca.yorku.plannedagent.scheduling.Scheduler;
import ca.yorku.plannedagent.services.Forecast;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentTest {

    static final ZoneId ZONE = ZoneId.of("America/Toronto");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 9, 0);
    static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    private Workspace workspace;
    private EventBus events;
    private List<String> seen;

    @BeforeEach
    void setUp() {
        workspace = new Workspace();
        workspace.addTask(new Task("EECS report", 180, NOW.plusDays(2), Priority.HIGH, false));
        workspace.addTask(new Task("Gym", 60, null, Priority.MEDIUM, false));
        events = new EventBus();
        seen = new ArrayList<>();
        events.attach((type, message) -> seen.add(type + " " + message));
    }

    @Test
    void readsThenSchedulesThenAnswers() {
        ScriptedLlm llm = new ScriptedLlm(
                "{\"tool\": \"get_tasks\", \"args\": {}}",
                "{\"tool\": \"schedule_tasks\", \"args\": {\"strategy\": \"deadline\"}}",
                "{\"answer\": \"Your week is ready to apply.\"}");

        AgentReply reply = agent(llm).handle("Plan my week", workspace);

        assertEquals("Your week is ready to apply.", reply.text);
        assertNotNull(reply.proposal);
        assertEquals(2, reply.proposal.getCommands().size());
        assertTrue(workspace.getPlan().getBlocks().isEmpty(), "the agent must not change the plan itself");
        assertEquals(List.of("AGENT using get_tasks", "AGENT using schedule_tasks"), seen);
        assertEquals(3, llm.requests.size());
    }

    @Test
    void toolResultsAreFedBackToTheLlm() {
        ScriptedLlm llm = new ScriptedLlm("{\"tool\": \"get_tasks\", \"args\": {}}", "{\"answer\": \"ok\"}");
        agent(llm).handle("What do I have?", workspace);

        List<String> secondCall = llm.requests.get(1).messages;
        assertEquals("user: What do I have?", secondCall.get(0));
        assertEquals("agent: {\"tool\":\"get_tasks\",\"args\":{}}", secondCall.get(1));
        assertTrue(secondCall.get(2).startsWith("tool: get_tasks result: t1 | EECS report"));
    }

    @Test
    void promptListsToolsTimeAndRules() {
        workspace.addPreference(new Preference(Preference.NO_WORK_AFTER, "22:00", "no studying after 10pm"));
        ScriptedLlm llm = new ScriptedLlm("{\"answer\": \"Hi!\"}");
        agent(llm).handle("hi", workspace);

        String system = llm.requests.get(0).system;
        assertTrue(system.contains("Saturday 2026-10-31 09:00"));
        assertTrue(system.contains("Wednesday 2026-11-04"));
        assertTrue(system.contains("Saturday 2026-11-07"));
        assertFalse(system.contains("2026-11-08"));
        assertTrue(system.contains("- get_schedule:"));
        assertTrue(system.contains("- schedule_tasks:"));
        assertTrue(system.contains("no work after 22:00"));
        assertTrue(llm.requests.get(0).wantJson);
    }

    @Test
    void invalidReplyGetsAFormatReminder() {
        ScriptedLlm llm = new ScriptedLlm("Sure, let me check your tasks!", "{\"answer\": \"done\"}");

        AgentReply reply = agent(llm).handle("hi", workspace);

        assertEquals("done", reply.text);
        assertTrue(llm.requests.get(1).messages.contains("system: " + Agent.FORMAT_REMINDER));
    }

    @Test
    void jsonAroundTextIsStillRead() {
        ScriptedLlm llm = new ScriptedLlm("Here you go:\n{\"answer\": \"You have 2 tasks.\"}\nThanks");
        assertEquals("You have 2 tasks.", agent(llm).handle("hi", workspace).text);
    }

    @Test
    void unknownToolIsReportedToTheLlm() {
        ScriptedLlm llm = new ScriptedLlm("{\"tool\": \"delete_everything\", \"args\": {}}", "{\"answer\": \"sorry\"}");

        agent(llm).handle("clear my week", workspace);

        String toolMessage = llm.requests.get(1).messages.get(2);
        assertTrue(toolMessage.startsWith("tool: delete_everything error: There is no tool"));
        assertTrue(toolMessage.contains("get_tasks"));
    }

    @Test
    void badArgumentsComeBackAsAnError() {
        ScriptedLlm llm = new ScriptedLlm(
                "{\"tool\": \"schedule_tasks\", \"args\": {\"strategy\": \"random\"}}",
                "{\"tool\": \"schedule_tasks\", \"args\": {\"strategy\": \"priority\"}}",
                "{\"answer\": \"ready\"}");

        AgentReply reply = agent(llm).handle("plan by priority", workspace);

        assertTrue(llm.requests.get(1).messages.get(2).contains("Unknown strategy 'random'"));
        assertNotNull(reply.proposal);
    }

    @Test
    void stopsAfterSixSteps() {
        String call = "{\"tool\": \"get_tasks\", \"args\": {}}";
        ScriptedLlm llm = new ScriptedLlm(call, call, call, call, call, call, call, call);

        AgentReply reply = agent(llm).handle("loop forever", workspace);

        assertEquals(Agent.GIVE_UP, reply.text);
        assertNull(reply.proposal);
        assertEquals(6, llm.requests.size());
    }

    @Test
    void llmFailureIsPassedToTheCaller() {
        ScriptedLlm llm = new ScriptedLlm();
        assertThrows(LlmException.class, () -> agent(llm).handle("hi", workspace));
    }

    @Test
    void emptyMessageIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> agent(new ScriptedLlm()).handle("  ", workspace));
    }

    @Test
    void memoryCarriesOverToTheNextMessage() {
        ScriptedLlm llm = new ScriptedLlm("{\"answer\": \"first\"}", "{\"answer\": \"second\"}");
        Agent agent = agent(llm);
        agent.handle("one", workspace);
        agent.handle("two", workspace);
        assertEquals(List.of("user: one", "agent: first", "user: two"), llm.requests.get(1).messages);
    }

    @Test
    void memoryKeepsOnlyTheNewest() {
        ConversationMemory memory = new ConversationMemory(3);
        for (int i = 1; i <= 5; i++) {
            memory.add("user", "m" + i);
        }
        assertEquals(List.of("user: m3", "user: m4", "user: m5"), memory.recent());
    }

    private Agent agent(ScriptedLlm llm) {
        Scheduler scheduler = new Scheduler();
        ConflictChecker checker = new ConflictChecker();
        return new Agent(llm, List.of(
                new GetTasksTool(),
                new GetScheduleTool(CLOCK),
                new ScheduleTasksTool(scheduler, (from, to) -> Forecast.none(), checker, CLOCK)),
                new ConversationMemory(), events, CLOCK);
    }
}
