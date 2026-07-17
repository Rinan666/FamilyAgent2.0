package com.familyagent.module.mirror.service;

import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.facade.MirrorStyleDiaryFacade;
import com.familyagent.module.family.dto.FamilyMemberVO;
import com.familyagent.module.family.facade.MirrorFamilyContextFacade;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.facade.MirrorStyleGrowthFacade;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.facade.MirrorMemoryRecallFacade;
import com.familyagent.module.memory.facade.MirrorStyleMemoryFacade;
import com.familyagent.module.mirror.dto.MirrorContextResponse;
import com.familyagent.module.mirror.entity.MirrorAgentData;
import com.familyagent.module.mirror.repository.MirrorAgentDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MirrorContextService {

    private static final int DIARY_LIMIT = 12;
    private static final int MEMORY_LIMIT = 10;
    private static final int STYLE_LIMIT = 80;
    private static final String DISCLAIMER = "镜像 Agent 不是本人，也不代表本人真实想法；它会用目标成员的授权可见内容回答，并用目标成员的私有记录生成不含原文的风格参考。记录不足时应直接说明不确定。";

    private final MirrorFamilyContextFacade familyContextFacade;
    private final MirrorStyleDiaryFacade diaryStyleFacade;
    private final MirrorStyleMemoryFacade memoryStyleFacade;
    private final MirrorStyleGrowthFacade growthStyleFacade;
    private final MirrorMemoryRecallFacade memoryRecallFacade;
    private final MirrorAgentDataRepository mirrorAgentDataRepository;
    private final MirrorContextPromptBuilder promptBuilder;

    public MirrorContextResponse getContext(Long familyId, Long targetUserId, String query) {
        Long viewerUserId = CurrentUserGuard.currentUserId();
        MirrorFamilyContextFacade.MirrorFamilyContext familyContext = familyContextFacade.load(
                familyId,
                targetUserId,
                viewerUserId);
        FamilyMemberVO target = familyContext.target();
        FamilyMemberVO viewer = familyContext.viewer();

        AuthorizedMemoryRecallResult recall = memoryRecallFacade.recallForMirror(
                familyId,
                targetUserId,
                viewerUserId,
                query,
                DIARY_LIMIT,
                MEMORY_LIMIT);
        List<DiaryEntry> diaries = recall.getDiaries() == null ? List.of() : recall.getDiaries();
        List<MemoryEntry> memories = recall.getMemories() == null ? List.of() : recall.getMemories();
        List<GrowthGuardRecord> growthRecords = recall.getGrowthRecords() == null ? List.of() : recall.getGrowthRecords();
        promptBuilder.annotateTemporalLayers(diaries, memories, growthRecords);

        MirrorAgentData mirrorProfile = mirrorAgentDataRepository.findVisibleByFamilyAndTarget(
                familyId,
                targetUserId,
                viewerUserId);
        String privateStyleReference = promptBuilder.buildPrivateStyleReference(
                diaryStyleFacade.findActiveByFamilyAndUser(familyId, targetUserId, STYLE_LIMIT),
                memoryStyleFacade.findActiveByFamilyAndUser(familyId, targetUserId, STYLE_LIMIT),
                growthStyleFacade.findActiveByFamilyAndTarget(familyId, targetUserId, STYLE_LIMIT));

        boolean insufficientRecords = diaries.size() < 2 && growthRecords.size() < 2;
        return MirrorContextResponse.builder()
                .familyId(familyId)
                .viewerUserId(viewerUserId)
                .targetMember(target)
                .diaries(diaries)
                .memories(memories)
                .growthRecords(growthRecords)
                .libraryItems(List.of())
                .mirrorProfile(mirrorProfile == null ? Map.of() : mirrorProfile.getTraits())
                .memoryContext(promptBuilder.buildMemoryContext(
                        viewer,
                        target,
                        diaries,
                        memories,
                        growthRecords,
                        List.of(),
                        mirrorProfile,
                        privateStyleReference))
                .disclaimer(DISCLAIMER)
                .insufficientRecords(insufficientRecords)
                .sourceSummary(promptBuilder.buildSourceSummary(diaries, memories, growthRecords, List.of()))
                .retrievalMode(recall.getRetrievalMode())
                .retrievalQuery(recall.getQuery())
                .embeddingReadyCount(recall.getEmbeddingReadyCount())
                .suggestedQuestions(promptBuilder.buildSuggestedQuestions(target, diaries, growthRecords))
                .missingRecordSuggestions(promptBuilder.buildMissingRecordSuggestions(diaries, growthRecords))
                .build();
    }
}
