package com.familyagent.module.memory.facade;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.entity.MemoryEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AgentMemoryContextFormatter {

    private static final int MEMORY_LIMIT = 6;
    private static final int DIARY_LIMIT = 6;
    private static final int GROWTH_LIMIT = 6;
    private static final int PREVIEW_LIMIT = 220;

    public String format(AuthorizedMemoryRecallResult recall) {
        if (recall == null) {
            return "";
        }

        List<String> memoryHits = memoryHits(recall.getMemories());
        List<String> diaryHits = diaryHits(recall.getDiaries());
        List<String> growthHits = growthHits(recall.getGrowthRecords());

        List<String> sections = new ArrayList<>();
        sections.add(String.format(
                "retrieval_summary: mode=%s embedding_ready=%d memories=%d diaries=%d growth=%d",
                textOrDefault(recall.getRetrievalMode(), "TEXT_FALLBACK"),
                recall.getEmbeddingReadyCount(),
                memoryHits.size(),
                diaryHits.size(),
                growthHits.size()));
        addSection(sections, "family_memory_hits", memoryHits);
        addSection(sections, "diary_hits", diaryHits);
        addSection(sections, "growth_hits", growthHits);
        return String.join("\n\n", sections);
    }

    private List<String> memoryHits(List<MemoryEntry> memories) {
        List<String> hits = new ArrayList<>();
        for (MemoryEntry memory : safeList(memories)) {
            if (hits.size() >= MEMORY_LIMIT) {
                break;
            }
            if (memory == null || !EntityStatus.ACTIVE.name().equals(memory.getStatus())) {
                continue;
            }
            String content = textOrDefault(memory.getSummary(), memory.getContent());
            if (content.isBlank()) {
                continue;
            }
            hits.add(numbered(hits.size(), "[" + textOrDefault(memory.getType(), "MEMORY") + "] " + preview(content)));
        }
        return hits;
    }

    private List<String> diaryHits(List<DiaryEntry> diaries) {
        List<String> hits = new ArrayList<>();
        for (DiaryEntry diary : safeList(diaries)) {
            if (hits.size() >= DIARY_LIMIT) {
                break;
            }
            if (diary == null || diary.getRawText() == null || diary.getRawText().isBlank()) {
                continue;
            }
            String entryType = entryType(diary.getStructured());
            hits.add(numbered(hits.size(), "[" + entryType + "] " + preview(diary.getRawText())));
        }
        return hits;
    }

    private List<String> growthHits(List<GrowthGuardRecord> records) {
        List<String> hits = new ArrayList<>();
        for (GrowthGuardRecord record : safeList(records)) {
            if (hits.size() >= GROWTH_LIMIT) {
                break;
            }
            if (record == null || !EntityStatus.ACTIVE.name().equals(record.getStatus())) {
                continue;
            }
            String content = textOrDefault(record.getContent(), "");
            if (content.isBlank()) {
                continue;
            }
            hits.add(numbered(hits.size(), String.format("[%s] severity=%d %s",
                    textOrDefault(record.getCategory(), "OTHER"),
                    record.getSeverity() == null ? 0 : record.getSeverity(),
                    preview(content))));
        }
        return hits;
    }

    private void addSection(List<String> sections, String title, List<String> lines) {
        if (!lines.isEmpty()) {
            sections.add(title + ":\n" + String.join("\n", lines));
        }
    }

    private String entryType(Object structured) {
        if (structured instanceof Map<?, ?> data) {
            Object value = data.get("entryType");
            if (value instanceof String text && !text.isBlank()) {
                return text.trim();
            }
        }
        return "DAILY";
    }

    private String numbered(int index, String content) {
        return (index + 1) + ". " + content;
    }

    private String preview(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= PREVIEW_LIMIT ? text : text.substring(0, PREVIEW_LIMIT);
    }

    private String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private <T> List<T> safeList(List<T> items) {
        return items == null ? List.of() : items;
    }
}
