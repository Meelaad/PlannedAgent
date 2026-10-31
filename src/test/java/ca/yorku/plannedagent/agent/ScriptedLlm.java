package ca.yorku.plannedagent.agent;

import ca.yorku.plannedagent.ai.LlmClient;
import ca.yorku.plannedagent.ai.LlmException;
import ca.yorku.plannedagent.ai.LlmRequest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** A fake LLM that gives back prepared replies in order and remembers every request. */
public class ScriptedLlm implements LlmClient {

    private final Deque<String> replies = new ArrayDeque<>();
    public final List<LlmRequest> requests = new ArrayList<>();

    public ScriptedLlm(String... replies) {
        this.replies.addAll(List.of(replies));
    }

    @Override
    public String complete(LlmRequest request) {
        requests.add(request);
        if (replies.isEmpty()) {
            throw new LlmException("no more scripted replies");
        }
        return replies.poll();
    }
}
