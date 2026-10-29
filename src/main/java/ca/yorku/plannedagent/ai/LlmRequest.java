package ca.yorku.plannedagent.ai;

import java.util.List;
import java.util.Objects;

/** What we send to any LLM: the instructions, the conversation so far, and whether we want JSON back. */
public class LlmRequest {

    public final String system;
    public final List<String> messages;
    public final boolean wantJson;

    public LlmRequest(String system, List<String> messages, boolean wantJson) {
        this.system = Objects.requireNonNull(system, "system");
        this.messages = List.copyOf(messages);
        this.wantJson = wantJson;
    }

    /** The conversation as one block of text, the same for every provider. */
    public String conversation() {
        return String.join("\n\n", messages);
    }
}
