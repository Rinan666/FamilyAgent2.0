package com.familyagent.module.mirror.service;

import com.familyagent.common.constant.DiaryRecallSource;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
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
import com.familyagent.module.mirror.repository.MirrorAgentDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MirrorContextServiceTest {

    @Mock private MirrorFamilyContextFacade familyService;
    @Mock private MirrorStyleDiaryFacade diaryStyleFacade;
    @Mock private MirrorStyleMemoryFacade memoryStyleFacade;
    @Mock private MirrorStyleGrowthFacade growthStyleFacade;
    @Mock private MirrorMemoryRecallFacade memoryRecallService;
    @Mock private MirrorAgentDataRepository mirrorAgentDataRepository;

    @Test
    void getContext_usesPrivateStyleReferenceWithoutLeakingPrivateRawText() {
        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(101L);
            MirrorContextService service = service();
            Long familyId = 10L;
            Long targetUserId = 201L;
            String privatePhrase = "私密原文-千万不要泄露-蓝色铁盒";
            String privateMemoryPhrase = "私有经验-不要外传-青色抽屉";
            String privateGrowthPhrase = "成长观察-不要泄露-深夜哭过";

            FamilyMemberVO target = member(familyId, targetUserId, "target", "目标成员", "MEMBER");
            FamilyMemberVO viewer = member(familyId, 101L, "viewer", "查看者", "MEMBER");
            when(familyService.getMemberView(familyId, targetUserId)).thenReturn(target);
            when(familyService.getMemberView(familyId, 101L)).thenReturn(viewer);
            when(memoryRecallService.recallForMirror(eq(familyId), eq(targetUserId), eq(101L), eq("选择"), anyInt(), anyInt()))
                    .thenReturn(AuthorizedMemoryRecallResult.builder()
                            .diaries(List.of(diary(1L, familyId, targetUserId, "授权可见的选择记录", "FAMILY_VISIBLE")))
                            .memories(List.of(memory(2L, familyId, targetUserId, "授权可见经验", "做选择要看长期代价")))
                            .growthRecords(List.of())
                            .retrievalMode("TEXT_FALLBACK")
                            .query("选择")
                            .embeddingReadyCount(0)
                            .build());
            when(diaryStyleFacade.findActiveByFamilyAndUser(eq(familyId), eq(targetUserId), anyInt()))
                    .thenReturn(List.of(
                            diary(11L, familyId, targetUserId, privatePhrase + "。我当时很担心，也反复问自己为什么。", "PRIVATE"),
                            diary(12L, familyId, targetUserId, "我后来觉得，选择要慢一点，先想后果，再行动。", "PRIVATE")));
            when(memoryStyleFacade.findActiveByFamilyAndUser(eq(familyId), eq(targetUserId), anyInt()))
                    .thenReturn(List.of(memory(13L, familyId, targetUserId, "私有经验", privateMemoryPhrase + "；如果重来，我会先问清楚边界。")));
            when(growthStyleFacade.findActiveByFamilyAndTarget(eq(familyId), eq(targetUserId), anyInt()))
                    .thenReturn(List.of(growth(14L, familyId, targetUserId, "EMOTION", privateGrowthPhrase)));

            MirrorContextResponse response = service.getContext(familyId, targetUserId, "选择");

            verify(familyService).checkMembership(familyId);
            assertFalse(response.getMemoryContext().contains(privatePhrase));
            assertFalse(response.getMemoryContext().contains("蓝色铁盒"));
            assertFalse(response.getMemoryContext().contains(privateMemoryPhrase));
            assertFalse(response.getMemoryContext().contains("青色抽屉"));
            assertFalse(response.getMemoryContext().contains(privateGrowthPhrase));
            assertFalse(response.getMemoryContext().contains("深夜哭过"));
            assertTrue(response.getMemoryContext().contains("私有风格参考"));
            assertTrue(response.getMemoryContext().contains("镜像边界"));
            assertTrue(response.getMemoryContext().contains("不得冒充现实本人"));
            assertTrue(response.getMemoryContext().contains("基于授权资料的可能看法"));
            assertTrue(response.getMemoryContext().contains("不得引用原文"));
            assertTrue(response.getMemoryContext().contains("授权日记"));
            assertTrue(response.getMemoryContext().contains("授权可见的选择记录"));
        }
    }

    @Test
    void getContext_blankQuerySkipsLibrarySearchAndReturnsEmptyLibraryItems() {
        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(101L);
            MirrorContextService service = service();
            Long familyId = 10L;
            Long targetUserId = 201L;

            FamilyMemberVO target = member(familyId, targetUserId, "target", "目标成员", "MEMBER");
            FamilyMemberVO viewer = member(familyId, 101L, "viewer", "查看者", "MEMBER");
            when(familyService.getMemberView(familyId, targetUserId)).thenReturn(target);
            when(familyService.getMemberView(familyId, 101L)).thenReturn(viewer);
            when(memoryRecallService.recallForMirror(eq(familyId), eq(targetUserId), eq(101L), eq("   "), anyInt(), anyInt()))
                    .thenReturn(recall(List.of(), List.of(), "   "));
            stubEmptyStyleSamples(familyId, targetUserId);

            MirrorContextResponse response = service.getContext(familyId, targetUserId, "   ");

            assertTrue(response.getLibraryItems().isEmpty());
            assertTrue(response.getMemoryContext().contains("本轮未额外匹配到相关家族记忆片段"));
        }
    }

    @Test
    void getContext_nonBlankQueryReturnsEmptyLibraryItems() {
        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(101L);
            MirrorContextService service = service();
            Long familyId = 10L;
            Long targetUserId = 201L;

            FamilyMemberVO target = member(familyId, targetUserId, "target", "目标成员", "MEMBER");
            FamilyMemberVO viewer = member(familyId, 101L, "viewer", "查看者", "MEMBER");
            when(familyService.getMemberView(familyId, targetUserId)).thenReturn(target);
            when(familyService.getMemberView(familyId, 101L)).thenReturn(viewer);
            when(memoryRecallService.recallForMirror(eq(familyId), eq(targetUserId), eq(101L), eq("成长"), anyInt(), anyInt()))
                    .thenReturn(recall(List.of(), List.of(), "成长"));
            stubEmptyStyleSamples(familyId, targetUserId);
            MirrorContextResponse response = service.getContext(familyId, targetUserId, "成长");

            assertTrue(response.getLibraryItems().isEmpty());
        }
    }

    @Test
    void getContext_marksInsufficientRecordsAtThresholdBoundaries() {
        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(101L);
            MirrorContextService service = service();
            Long familyId = 10L;
            Long targetUserId = 201L;

            FamilyMemberVO target = member(familyId, targetUserId, "target", "目标成员", "MEMBER");
            FamilyMemberVO viewer = member(familyId, 101L, "viewer", "查看者", "MEMBER");
            when(familyService.getMemberView(familyId, targetUserId)).thenReturn(target);
            when(familyService.getMemberView(familyId, 101L)).thenReturn(viewer);
            stubEmptyStyleSamples(familyId, targetUserId);

            when(memoryRecallService.recallForMirror(eq(familyId), eq(targetUserId), eq(101L), eq("case11"), anyInt(), anyInt()))
                    .thenReturn(recall(List.of(diary(1L, familyId, targetUserId, "d1", "FAMILY_VISIBLE")),
                            List.of(), "case11"));
            when(memoryRecallService.recallForMirror(eq(familyId), eq(targetUserId), eq(101L), eq("case20"), anyInt(), anyInt()))
                    .thenReturn(recall(List.of(
                                    diary(2L, familyId, targetUserId, "d2", "FAMILY_VISIBLE"),
                                    diary(3L, familyId, targetUserId, "d3", "FAMILY_VISIBLE")),
                            List.of(), "case20"));
            when(memoryRecallService.recallForMirror(eq(familyId), eq(targetUserId), eq(101L), eq("case02"), anyInt(), anyInt()))
                    .thenReturn(recallWithGrowth(List.of(),
                            List.of(
                                    growth(2L, familyId, targetUserId, "EMOTION", "g2"),
                                    growth(3L, familyId, targetUserId, "EMOTION", "g3")), "case02"));
            when(memoryRecallService.recallForMirror(eq(familyId), eq(targetUserId), eq(101L), eq("case22"), anyInt(), anyInt()))
                    .thenReturn(recallWithGrowth(List.of(
                                    diary(4L, familyId, targetUserId, "d4", "FAMILY_VISIBLE"),
                                    diary(5L, familyId, targetUserId, "d5", "FAMILY_VISIBLE")),
                            List.of(
                                    growth(4L, familyId, targetUserId, "EMOTION", "g4"),
                                    growth(5L, familyId, targetUserId, "EMOTION", "g5")), "case22"));

            assertTrue(service.getContext(familyId, targetUserId, "case11").isInsufficientRecords());
            assertFalse(service.getContext(familyId, targetUserId, "case20").isInsufficientRecords());
            assertFalse(service.getContext(familyId, targetUserId, "case02").isInsufficientRecords());
            assertFalse(service.getContext(familyId, targetUserId, "case22").isInsufficientRecords());
        }
    }

    @Test
    void getContext_handlesMissingViewerAndProfileGracefully() {
        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(101L);
            MirrorContextService service = service();
            Long familyId = 10L;
            Long targetUserId = 201L;

            FamilyMemberVO target = member(familyId, targetUserId, "target", "目标成员", null);
            target.setRelationshipLabel("");
            when(familyService.getMemberView(familyId, targetUserId)).thenReturn(target);
            when(familyService.getMemberView(familyId, 101L))
                    .thenThrow(new BusinessException(ErrorCode.NOT_FAMILY_MEMBER));
            when(memoryRecallService.recallForMirror(eq(familyId), eq(targetUserId), eq(101L), eq("空画像"), anyInt(), anyInt()))
                    .thenReturn(recall(List.of(), List.of(), "空画像"));
            stubEmptyStyleSamples(familyId, targetUserId);
            when(mirrorAgentDataRepository.findVisibleByFamilyAndTarget(familyId, targetUserId, 101L)).thenReturn(null);

            MirrorContextResponse response = service.getContext(familyId, targetUserId, "空画像");

            assertEquals(Map.of(), response.getMirrorProfile());
            assertTrue(response.getMemoryContext().contains("UNKNOWN"));
            assertTrue(response.getMemoryContext().contains("当前用户尚未为该成员设置亲属称呼"));
            assertTrue(response.getMemoryContext().contains("暂无该成员的授权画像摘要"));
        }
    }

    @Test
    void getContext_buildsSourceSummaryAndSuggestionsForSparseRecords() {
        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(101L);
            MirrorContextService service = service();
            Long familyId = 10L;
            Long targetUserId = 201L;

            FamilyMemberVO target = member(familyId, targetUserId, "target", "目标成员", "MEMBER");
            FamilyMemberVO viewer = member(familyId, 101L, "viewer", "查看者", "MEMBER");
            when(familyService.getMemberView(familyId, targetUserId)).thenReturn(target);
            when(familyService.getMemberView(familyId, 101L)).thenReturn(viewer);
            DiaryEntry selfDiary = diary(1L, familyId, targetUserId, "本人记录内容", "FAMILY_VISIBLE");
            DiaryEntry relatedDiary = diary(2L, familyId, targetUserId, "家人补充内容", "FAMILY_VISIBLE");
            relatedDiary.setMetadata(Map.of(
                    DiaryRecallSource.METADATA_KEY,
                    DiaryRecallSource.RELATED_BY_FAMILY.name(),
                    "relatedMemberName",
                    "妈妈"));
            when(memoryRecallService.recallForMirror(eq(familyId), eq(targetUserId), eq(101L), eq("总结"), anyInt(), anyInt()))
                    .thenReturn(recall(List.of(selfDiary, relatedDiary), List.of(), "总结"));
            stubEmptyStyleSamples(familyId, targetUserId);

            MirrorContextResponse response = service.getContext(familyId, targetUserId, "总结");

            assertTrue(response.getSourceSummary().contains("1 条本人记录"));
            assertTrue(response.getSourceSummary().contains("1 条家人补充"));
            assertFalse(response.getMissingRecordSuggestions().isEmpty());
            assertTrue(response.getSuggestedQuestions().stream().anyMatch(q -> q.contains("目标成员")));
        }
    }

    @Test
    void getContext_rejectsTargetOutsideFamily() {
        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(101L);
            MirrorContextService service = service();
            when(familyService.getMemberView(10L, 999L))
                    .thenThrow(new BusinessException(ErrorCode.NOT_FAMILY_MEMBER));

            assertThrows(BusinessException.class, () -> service.getContext(10L, 999L, "任何问题"));

            verify(familyService).checkMembership(10L);
        }
    }

    private MirrorContextService service() {
        MirrorContextPromptBuilder promptBuilder = new MirrorContextPromptBuilder();
        return new MirrorContextService(
                familyService,
                diaryStyleFacade,
                memoryStyleFacade,
                growthStyleFacade,
                memoryRecallService,
                mirrorAgentDataRepository,
                promptBuilder);
    }

    private void stubEmptyStyleSamples(Long familyId, Long targetUserId) {
        when(diaryStyleFacade.findActiveByFamilyAndUser(familyId, targetUserId, 80)).thenReturn(List.of());
        when(memoryStyleFacade.findActiveByFamilyAndUser(familyId, targetUserId, 80)).thenReturn(List.of());
        when(growthStyleFacade.findActiveByFamilyAndTarget(familyId, targetUserId, 80)).thenReturn(List.of());
    }

    private static AuthorizedMemoryRecallResult recall(List<DiaryEntry> diaries, List<MemoryEntry> memories, String query) {
        return AuthorizedMemoryRecallResult.builder()
                .diaries(diaries)
                .memories(memories)
                .growthRecords(List.of())
                .retrievalMode("TEXT_FALLBACK")
                .query(query)
                .embeddingReadyCount(0)
                .build();
    }

    private static AuthorizedMemoryRecallResult recallWithGrowth(
            List<DiaryEntry> diaries,
            List<GrowthGuardRecord> growthRecords,
            String query) {
        return AuthorizedMemoryRecallResult.builder()
                .diaries(diaries)
                .memories(List.of())
                .growthRecords(growthRecords)
                .retrievalMode("TEXT_FALLBACK")
                .query(query)
                .embeddingReadyCount(0)
                .build();
    }

    private static FamilyMemberVO member(Long familyId, Long userId, String username, String nickname, String role) {
        return FamilyMemberVO.builder()
                .familyId(familyId)
                .userId(userId)
                .username(username)
                .nickname(nickname)
                .role(role)
                .birthDate("2000-01-01")
                .build();
    }

    private static DiaryEntry diary(Long id, Long familyId, Long userId, String rawText, String visibility) {
        DiaryEntry entry = new DiaryEntry();
        entry.setId(id);
        entry.setFamilyId(familyId);
        entry.setUserId(userId);
        entry.setRawText(rawText);
        entry.setVisibility(visibility);
        entry.setStructured(java.util.Map.of("title", "测试记录", "entryType", "SELF_REFLECTION"));
        entry.setCreatedAt(LocalDateTime.now().minusDays(id));
        entry.setUpdatedAt(LocalDateTime.now().minusDays(id));
        return entry;
    }

    private static MemoryEntry memory(Long id, Long familyId, Long userId, String summary, String content) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setFamilyId(familyId);
        entry.setUserId(userId);
        entry.setSummary(summary);
        entry.setContent(content);
        entry.setScope("FAMILY_VISIBLE");
        entry.setType("ELDER_ADVICE");
        entry.setStatus("ACTIVE");
        entry.setImportance(3);
        entry.setCreatedAt(LocalDateTime.now().minusDays(id));
        entry.setUpdatedAt(LocalDateTime.now().minusDays(id));
        return entry;
    }

    private static GrowthGuardRecord growth(Long id, Long familyId, Long targetUserId, String category, String content) {
        GrowthGuardRecord record = new GrowthGuardRecord();
        record.setId(id);
        record.setFamilyId(familyId);
        record.setTargetUserId(targetUserId);
        record.setCreatedBy(101L);
        record.setCategory(category);
        record.setContent(content);
        record.setVisibility("CARE_VISIBLE");
        record.setStatus("ACTIVE");
        record.setSeverity(3);
        record.setObservedAt(LocalDate.now().minusDays(id));
        record.setCreatedAt(LocalDateTime.now().minusDays(id));
        return record;
    }

}
