package ca.yorku.plannedagent.ai;

/**
 * Adapter pattern target: the only way the rest of the program talks to an LLM.
 * Throws LlmException when there is no answer (no connection, quota used up, unreadable reply).
 */
@FunctionalInterface
public interface LlmClient {

    String complete(LlmRequest request);
}
