package ca.yorku.plannedagent.agent;

import ca.yorku.plannedagent.ai.LlmClient;
import ca.yorku.plannedagent.ai.LlmRequest;
import ca.yorku.plannedagent.app.EventBus;
import ca.yorku.plannedagent.commands.Proposal;
import ca.yorku.plannedagent.model.Preference;
import ca.yorku.plannedagent.model.Workspace;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The agent loop. Each step the LLM answers with one JSON object: a tool call or a final answer.
 * The agent runs the tool, stores the result in memory and asks again, for at most 6 steps.
 * LlmException is not caught here so the caller can fall back to a non-AI path.
 */
public class Agent {

    static final String FORMAT_REMINDER = "Your last reply was not valid. Reply with ONE JSON object only: "
            + "{\"tool\": \"name\", \"args\": {...}} or {\"answer\": \"text\"}";
    static final String GIVE_UP = "I could not finish. Try Auto-schedule.";
    private static final DateTimeFormatter NOW = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd", Locale.ENGLISH);

    private final LlmClient llm;
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final ConversationMemory memory;
    private final EventBus events;
    private final Clock clock;
    private final int maxSteps = 6;

    public Agent(LlmClient llm, List<Tool> tools, ConversationMemory memory, EventBus events, Clock clock) {
        this.llm = Objects.requireNonNull(llm, "llm");
        for (Tool tool : tools) {
            this.tools.put(tool.name(), tool);
        }
        this.memory = Objects.requireNonNull(memory, "memory");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AgentReply handle(String message, Workspace workspace) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Type a message first");
        }
        memory.add("user", message.trim());
        Proposal proposal = null;
        for (int step = 0; step < maxSteps; step++) {
            String text = llm.complete(buildRequest(workspace));
            AgentStep next = parseStep(text);
            if (next == null) {
                memory.add("system", FORMAT_REMINDER);
                continue;
            }
            if (next.isAnswer()) {
                memory.add("agent", next.answer);
                return new AgentReply(next.answer, proposal);
            }
            memory.add("agent", callText(next));
            events.notifyObservers(EventBus.AGENT, "using " + next.tool);
            ToolResult result = runTool(next, workspace);
            if (result.proposal != null) {
                proposal = result.proposal;
            }
            memory.add("tool", next.tool + (result.ok ? " result: " : " error: ") + result.text);
        }
        memory.add("agent", GIVE_UP);
        return new AgentReply(GIVE_UP, null);
    }

    public ConversationMemory getMemory() {
        return memory;
    }

    private ToolResult runTool(AgentStep step, Workspace workspace) {
        Tool tool = tools.get(step.tool);
        if (tool == null) {
            return ToolResult.error("There is no tool called '" + step.tool + "'. Use one of: "
                    + String.join(", ", tools.keySet()));
        }
        try {
            return tool.run(step.args, workspace);
        } catch (RuntimeException e) {
            return ToolResult.error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private LlmRequest buildRequest(Workspace workspace) {
        String toolList = tools.values().stream()
                .map(t -> "- " + t.name() + ": " + t.description())
                .collect(Collectors.joining("\n"));
        String rules = workspace.getPreferences().isEmpty() ? "none"
                : workspace.getPreferences().stream().map(Preference::describe).collect(Collectors.joining("; "));
        LocalDateTime now = LocalDateTime.now(clock);
        String nextDays = IntStream.rangeClosed(1, 7)
                .mapToObj(i -> DAY.format(now.toLocalDate().plusDays(i)))
                .collect(Collectors.joining(", "));
        String system = """
                You are PlannedAgent, a planning assistant for a university student.
                It is now %s.
                The next days are: %s. When the student names a day, use its date from this list.
                You work in steps. Each reply must be exactly ONE JSON object and nothing else:
                - to use a tool: {"tool": "<name>", "args": {"<arg>": "<value>"}}
                - to finish: {"answer": "<short reply to the student>"}
                Tools:
                %s
                Rules:
                - Look at the data with tools before answering. Never invent tasks, blocks, ids or times.
                - Tools that change the plan return a proposal. The student applies or discards it, so say the
                  change is ready to apply, never that it is done.
                - Use ids exactly as the tools show them (t1, b3, e2).
                - Times are local, written as YYYY-MM-DDTHH:MM.
                - If a tool returns an error, fix the arguments and try again, or ask the student.
                - If the request is unclear, ask one short question as your answer.
                - Keep answers short and friendly.
                The student's saved rules: %s
                """.formatted(NOW.format(now), nextDays, toolList, rules);
        return new LlmRequest(system, memory.recent(), true);
    }

    /** Returns null if the text holds no usable step. */
    private AgentStep parseStep(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            return null;
        }
        JsonObject object;
        try {
            JsonElement root = JsonParser.parseString(text.substring(start, end + 1));
            if (!root.isJsonObject()) {
                return null;
            }
            object = root.getAsJsonObject();
        } catch (JsonParseException e) {
            return null;
        }
        String tool = primitive(object.get("tool"));
        if (tool != null && !tool.isBlank()) {
            Map<String, String> args = new LinkedHashMap<>();
            JsonElement argsElement = object.get("args");
            if (argsElement != null && argsElement.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : argsElement.getAsJsonObject().entrySet()) {
                    JsonElement value = entry.getValue();
                    if (value != null && !value.isJsonNull()) {
                        args.put(entry.getKey(), value.isJsonPrimitive() ? value.getAsString() : value.toString());
                    }
                }
            }
            return AgentStep.call(tool.trim(), args);
        }
        String answer = primitive(object.get("answer"));
        if (answer != null && !answer.isBlank()) {
            return AgentStep.answer(answer.trim());
        }
        return null;
    }

    private static String callText(AgentStep step) {
        JsonObject call = new JsonObject();
        call.addProperty("tool", step.tool);
        JsonObject args = new JsonObject();
        step.args.forEach(args::addProperty);
        call.add("args", args);
        return call.toString();
    }

    private static String primitive(JsonElement element) {
        return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
    }
}
