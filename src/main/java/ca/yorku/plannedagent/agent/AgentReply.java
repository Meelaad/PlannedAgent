package ca.yorku.plannedagent.agent;

import ca.yorku.plannedagent.commands.Proposal;

/** What the agent says back, plus a proposal when the plan would change. */
public class AgentReply {

    public final String text;
    public final Proposal proposal;

    public AgentReply(String text, Proposal proposal) {
        this.text = text;
        this.proposal = proposal;
    }
}
