package com.familyagent.module.session.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ChatSessionArchiveSummaryData(
        String summary,
        String titleSuggestion,
        List<String> focusTopics,
        String confidence
) {
    public ChatSessionArchiveSummaryData {
        summary = summary == null ? "" : summary;
        titleSuggestion = titleSuggestion == null ? "" : titleSuggestion;
        focusTopics = focusTopics == null ? List.of() : List.copyOf(focusTopics);
        confidence = confidence == null ? "" : confidence;
    }

    public Map<String, Object> toMetadataMap() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("summary", summary);
        metadata.put("titleSuggestion", titleSuggestion);
        metadata.put("focusTopics", focusTopics);
        metadata.put("confidence", confidence);
        return metadata;
    }
}
