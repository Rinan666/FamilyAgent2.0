package com.familyagent.module.agent.harness;

/**
 * Typed Agent tool boundary.
 */
public interface AgentTool<I, O> {

    AgentToolDescriptor descriptor();

    Class<I> inputType();

    O execute(AgentRunContext context, I input);
}
