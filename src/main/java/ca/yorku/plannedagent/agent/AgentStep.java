package ca.yorku.plannedagent.agent;

import java.util.Map;

/** One reply from the LLM: either a tool call or the final answer. */
public class AgentStep {

    public final String tool;
    public final Map<String, String> args;
    public final String answer;

    private AgentStep(String tool, Map<String, String> args, String answer) {
        this.tool = tool;
        this.args = args;
        this.answer = answer;
    }

    public static AgentStep call(String tool, Map<String, String> args) {
        return new AgentStep(tool, Map.copyOf(args), null);
    }

    public static AgentStep answer(String answer) {
        return new AgentStep(null, Map.of(), answer);
    }

    public boolean isAnswer() {
        return tool == null || tool.isBlank();
    }
}
