package ca.yorku.plannedagent.agent;

import java.util.ArrayList;
import java.util.List;

/** Short-term memory: the last messages of the conversation, oldest dropped first. */
public class ConversationMemory {

    private final List<String> messages = new ArrayList<>();
    private final int limit;

    public ConversationMemory() {
        this(20);
    }

    public ConversationMemory(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("The memory limit must be at least 1");
        }
        this.limit = limit;
    }

    public void add(String role, String text) {
        messages.add(role + ": " + text);
        while (messages.size() > limit) {
            messages.remove(0);
        }
    }

    public List<String> recent() {
        return List.copyOf(messages);
    }

    public void clear() {
        messages.clear();
    }
}
