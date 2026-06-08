package com.familyagent.module.mirror.dto;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.family.dto.FamilyMemberVO;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MirrorContextResponse {

    private Long familyId;
    private Long viewerUserId;
    private FamilyMemberVO targetMember;
    private List<DiaryEntry> diaries;
    private List<MemoryEntry> memories;
    private List<MemoryLibraryItem> libraryItems;
    private Map<String, Object> mirrorProfile;
    private String memoryContext;
    private String disclaimer;
    private boolean insufficientRecords;
    private String sourceSummary;
    private String retrievalMode;
    private String retrievalQuery;
    private long embeddingReadyCount;
    private List<String> suggestedQuestions;
    private List<String> missingRecordSuggestions;
}
