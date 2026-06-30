package com.familyagent.module.agent.harness;

import org.springframework.stereotype.Component;

@Component
public class AgentToolInputSummarizer {

    private static final int SUMMARY_LIMIT = 500;

    public String summarize(Object input) {
        if (input == null) {
            return "inputType=null";
        }
        return trim("inputType=" + input.getClass().getSimpleName(), SUMMARY_LIMIT);
    }

    public String trim(String value, int limit) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
