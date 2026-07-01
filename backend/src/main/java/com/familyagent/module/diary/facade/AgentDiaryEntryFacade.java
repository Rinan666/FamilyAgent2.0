package com.familyagent.module.diary.facade;

import com.familyagent.module.diary.dto.CreateDiaryEntryRequest;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.service.DiaryEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentDiaryEntryFacade {

    private final DiaryEntryService diaryEntryService;

    public DiaryEntry create(CreateDiaryEntryRequest request) {
        return diaryEntryService.create(request);
    }
}
