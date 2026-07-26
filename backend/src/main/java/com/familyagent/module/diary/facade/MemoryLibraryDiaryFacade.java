package com.familyagent.module.diary.facade;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.service.DiaryMemorySyncSupport;
import com.familyagent.module.memory.facade.UnifiedDiaryRecordFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryLibraryDiaryFacade {

    private final UnifiedDiaryRecordFacade diaryRecords;
    private final DiaryMemorySyncSupport memorySyncSupport;

    public DiaryEntry findById(Long diaryId) {
        return diaryRecords.findById(diaryId);
    }

    public void update(DiaryEntry entry) {
        memorySyncSupport.sync(entry);
    }

    public void delete(Long diaryId) {
        memorySyncSupport.delete(diaryId);
    }
}
