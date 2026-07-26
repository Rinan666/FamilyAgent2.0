package com.familyagent.module.memory.service;

import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.module.memory.entity.MemoryEntry;

import java.util.List;

record UnifiedMemorySourceCounts(int diaries, int memories, int growthRecords) {

    static UnifiedMemorySourceCounts from(List<MemoryEntry> entries) {
        int diaries = 0;
        int growth = 0;
        for (MemoryEntry entry : entries) {
            if (MemoryOriginType.DIARY.name().equals(entry.getOriginType())) {
                diaries++;
            } else if (MemoryOriginType.GROWTH.name().equals(entry.getOriginType())) {
                growth++;
            }
        }
        return new UnifiedMemorySourceCounts(diaries, entries.size() - diaries - growth, growth);
    }
}
