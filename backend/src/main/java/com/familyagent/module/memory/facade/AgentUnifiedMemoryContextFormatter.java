package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
import com.familyagent.module.memory.dto.MemoryRecallPlan;
import com.familyagent.module.memory.dto.RecallParticipantSummary;
import com.familyagent.module.memory.dto.RecallSourceSummary;
import com.familyagent.module.memory.dto.UnifiedAuthorizedMemoryRecallResult;
import com.familyagent.module.memory.entity.MemoryEntry;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgentUnifiedMemoryContextFormatter {

    private static final int ITEM_PREVIEW_LIMIT = 420;

    public String format(UnifiedAuthorizedMemoryRecallResult recall, MemoryRecallPlan plan) {
        if (recall == null || recall.items().isEmpty() || plan == null || !plan.enabled()) {
            return "";
        }
        Map<String, RecallSourceSummary> sources = sourceIndex(recall.sources());
        StringBuilder context = new StringBuilder();
        context.append("retrieval_summary: mode=")
                .append(recall.retrievalMode())
                .append(" records=")
                .append(recall.items().size())
                .append(" depth=")
                .append(plan.depth().name())
                .append('\n');
        context.append("authorized_record_hits:\n");
        int index = 0;
        for (AuthorizedMemoryRecallCandidate candidate : recall.items()) {
            String line = formatItem(++index, candidate, sources.get(candidate.publicId()));
            if (context.length() + line.length() > plan.contextCharBudget()) {
                break;
            }
            context.append(line).append('\n');
        }
        return context.toString().trim();
    }

    private static String formatItem(
            int index,
            AuthorizedMemoryRecallCandidate candidate,
            RecallSourceSummary source) {
        MemoryEntry entry = candidate.entry();
        return index
                + ". [record_id=" + entry.getId()
                + " source=" + candidate.sourceType().name()
                + " content_type=" + text(entry.getType())
                + " author=" + participant(source == null ? null : source.getAuthor(),
                        source == null ? null : source.getObserver())
                + " subject=" + participant(source == null ? null : source.getSubject(), null)
                + " occurred_at=" + text(entry.getOccurredAt())
                + "] "
                + preview(firstNonBlank(entry.getTitle(), entry.getSummary(), entry.getContent()))
                + " | "
                + preview(entry.getContent());
    }

    private static Map<String, RecallSourceSummary> sourceIndex(List<RecallSourceSummary> sources) {
        Map<String, RecallSourceSummary> index = new LinkedHashMap<>();
        if (sources != null) {
            for (RecallSourceSummary source : sources) {
                if (source != null && source.getId() != null) {
                    index.put(source.getId(), source);
                }
            }
        }
        return index;
    }

    private static String participant(RecallParticipantSummary primary, RecallParticipantSummary fallback) {
        RecallParticipantSummary value = primary == null ? fallback : primary;
        if (value == null) {
            return "unknown";
        }
        return firstNonBlank(value.name(), String.valueOf(value.userId()), "unknown");
    }

    private static String preview(String value) {
        String normalized = text(value).replaceAll("\\s+", " ").trim();
        return normalized.length() <= ITEM_PREVIEW_LIMIT
                ? normalized
                : normalized.substring(0, ITEM_PREVIEW_LIMIT) + "...";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
