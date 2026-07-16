package com.familyagent.module.agent.harness.constant;

public enum AgentTraceOperation {
    LLM_CHAT_STREAM("llm.chat_stream"),
    WEB_SEARCH_PUBLIC("web_search.public"),
    MEMORY_RECALL_AUTHORIZED("memory.recall.authorized"),
    EMBEDDING_RECALL_QUERY("embedding.recall_query");

    private final String value;

    AgentTraceOperation(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
