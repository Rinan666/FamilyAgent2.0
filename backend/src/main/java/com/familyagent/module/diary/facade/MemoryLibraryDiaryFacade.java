package com.familyagent.module.diary.facade;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.module.diary.service.DiaryMemorySyncSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryLibraryDiaryFacade {

    private final DiaryEntryRepository diaryRepository;
    private final DiaryMemorySyncSupport memorySyncSupport;

    public DiaryEntry findById(Long diaryId) {
        return diaryRepository.selectById(diaryId);
    }

    public void update(DiaryEntry entry) {
        diaryRepository.updateById(entry);
        memorySyncSupport.sync(entry);
    }

    public void delete(Long diaryId) {
        diaryRepository.deleteById(diaryId);
        memorySyncSupport.delete(diaryId);
    }
}
