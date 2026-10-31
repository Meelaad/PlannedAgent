package ca.yorku.plannedagent.agent;

import ca.yorku.plannedagent.commands.Proposal;

/** What a tool gives back to the agent. Errors are text too, so the LLM can read them and fix its call. */
public class ToolResult {

    public final boolean ok;
    public final String text;
    public final Proposal proposal;

    public ToolResult(boolean ok, String text, Proposal proposal) {
        this.ok = ok;
        this.text = text;
        this.proposal = proposal;
    }

    public static ToolResult ok(String text) {
        return new ToolResult(true, text, null);
    }

    public static ToolResult ok(String text, Proposal proposal) {
        return new ToolResult(true, text, proposal);
    }

    public static ToolResult error(String text) {
        return new ToolResult(false, text, null);
    }
}
