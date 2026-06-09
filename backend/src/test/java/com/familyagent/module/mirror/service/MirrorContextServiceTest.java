package com.familyagent.module.mirror.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.PageResult;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.module.family.dto.FamilyMemberVO;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallService;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryItem;
import com.familyagent.module.memorylibrary.dto.MemoryLibrarySearchRequest;
import com.familyagent.module.memorylibrary.service.MemoryLibraryService;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MirrorContextServiceTest {

    @Mock private FamilyService familyService;
    @Mock private FamilyMemberRepository familyMemberRepository;
    @Mock private DiaryEntryRepository diaryRepository;
    @Mock private MemoryEntryRepository memoryRepository;
    @Mock private GrowthGuardRecordRepository growthRecordRepository;
    @Mock private AuthorizedMemoryRecallService memoryRecallService;
    @Mock private MemoryLibraryService memoryLibraryService;
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
            when(familyMemberRepository.findMemberViewByFamilyAndUser(familyId, targetUserId)).thenReturn(target);
            when(familyMemberRepository.findMemberViewByFamilyAndUser(familyId, 101L)).thenReturn(viewer);
            when(memoryRecallService.recallForMirror(eq(familyId), eq(targetUserId), eq(101L), eq("选择"), anyInt(), anyInt()))
                    .thenReturn(AuthorizedMemoryRecallResult.builder()
                            .diaries(List.of(diary(1L, familyId, targetUserId, "授权可见的选择记录", "FAMILY_VISIBLE")))
                            .memories(List.of(memory(2L, familyId, targetUserId, "授权可见经验", "做选择要看长期代价")))
                            .growthRecords(List.of())
                            .retrievalMode("TEXT_FALLBACK")
                            .query("选择")
                            .embeddingReadyCount(0)
                            .build());
            when(memoryLibraryService.search(any(MemoryLibrarySearchRequest.class)))
                    .thenReturn(PageResult.of(List.<MemoryLibraryItem>of(), 1, 5, 0));
            when(diaryRepository.findActiveByFamilyAndUserForStyle(eq(familyId), eq(targetUserId), anyInt()))
                    .thenReturn(List.of(
                            diary(11L, familyId, targetUserId, privatePhrase + "。我当时很担心，也反复问自己为什么。", "PRIVATE"),
                            diary(12L, familyId, targetUserId, "我后来觉得，选择要慢一点，先想后果，再行动。", "PRIVATE")));
            when(memoryRepository.findActiveByFamilyAndUserForStyle(eq(familyId), eq(targetUserId), anyInt()))
                    .thenReturn(List.of(memory(13L, familyId, targetUserId, "私有经验", privateMemoryPhrase + "；如果重来，我会先问清楚边界。")));
            when(growthRecordRepository.findActiveByFamilyAndTargetForStyle(eq(familyId), eq(targetUserId), anyInt()))
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
            assertTrue(response.getMemoryContext().contains("安全边界"));
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
            when(familyMemberRepository.findMemberViewByFamilyAndUser(familyId, targetUserId)).thenReturn(target);
            when(familyMemberRepository.findMemberViewByFamilyAndUser(familyId, 101L)).thenReturn(viewer);
            when(memoryRecallService.recallForMirror(eq(familyId), eq(targetUserId), eq(101L), eq("   "), anyInt(), anyInt()))
                    .thenReturn(recall(List.of(), List.of(), "   "));
            when(diaryRepository.findActiveByFamilyAndUserForStyle(eq(familyId), eq(targetUserId), anyInt())).thenReturn(List.of());
            when(memoryRepository.findActiveByFamilyAndUserForStyle(eq(familyId), eq(targetUserId), anyInt())).thenReturn(List.of());
            when(growthRecordRepository.findActiveByFamilyAndTargetForStyle(eq(familyId), eq(targetUserId), anyInt())).thenReturn(List.of());

            MirrorContextResponse response = service.getContext(familyId, targetUserId, "   ");

            assertTrue(response.getLibraryItems().isEmpty());
            assertTrue(response.getMemoryContext().contains("本轮未额外匹配到相关家族记忆片段"));
            verify(memoryLibraryService, never()).search(any(MemoryLibrarySearchRequest.class));
        }
    }

    @Test
    void getContext_deduplicatesAndLimitsLibraryItems() {
        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(101L);
            MirrorContextService service = service();
            Long familyId = 10L;
            Long targetUserId = 201L;

            FamilyMemberVO target = member(familyId, targetUserId, "target", "目标成员", "MEMBER");
            FamilyMemberVO viewer = member(familyId, 101L, "viewer", "查看者", "MEMBER");
            when(familyMemberRepository.findMemberViewByFamilyAndUser(familyId, targetUserId)).thenReturn(target);
            when(familyMemberRepository.findMemberViewByFamilyAndUser(familyId, 101L)).thenReturn(viewer);
            when(memoryRecallService.recallForMirror(eq(familyId), eq(targetUserId), eq(101L), eq("成长"), anyInt(), anyInt()))
                    .thenReturn(recall(List.of(), List.of(), "成长"));
            when(diaryRepository.findActiveByFamilyAndUserForStyle(eq(familyId), eq(targetUserId), anyInt())).thenReturn(List.of());
            when(memoryRepository.findActiveByFamilyAndUserForStyle(eq(familyId), eq(targetUserId), anyInt())).thenReturn(List.of());
            when(growthRecordRepository.findActiveByFamilyAndTargetForStyle(eq(familyId), eq(targetUserId), anyInt())).thenReturn(List.of());
            when(memoryLibraryService.search(any(MemoryLibrarySearchRequest.class)))
                    .thenReturn(PageResult.of(List.of(
                            libraryItem("A", "LIFE_RECORD", "标题A"),
                            libraryItem("B", "LIFE_RECORD", "标题B"),
                            libraryItem("C", "LIFE_RECORD", "标题C"),
                            libraryItem("D", "LIFE_RECORD", "标题D"),
                            libraryItem("E", "LIFE_RECORD", "标题E")
                    ), 1, 5, 5))
                    .thenReturn(PageResult.of(List.of(
                            libraryItem("B", "FAMILY_EXPERIENCE", "重复B"),
                            libraryItem("F", "FAMILY_EXPERIENCE", "标题F"),
                            libraryItem("G", "FAMILY_EXPERIENCE", "标题G")
                    ), 1, 3, 3))
                    .thenReturn(PageResult.of(List.of(
                            libraryItem("H", "AI_SUMMARY", "标题H"),
                            libraryItem("I", "AI_SUMMARY", "标题I")
                    ), 1, 2, 2));

            MirrorContextResponse response = service.getContext(familyId, targetUserId, "成长");

            assertEquals(8, response.getLibraryItems().size());
            assertEquals(List.of("A", "B", "C", "D", "E", "F", "G", "H"),
                    response.getLibraryItems().stream().map(MemoryLibraryItem::getId).toList());
            verify(memoryLibraryService, times(3)).search(any(MemoryLibrarySearchRequest.class));
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
            when(familyMemberRepository.findMemberViewByFamilyAndUser(familyId, targetUserId)).thenReturn(target);
            when(familyMemberRepository.findMemberViewByFamilyAndUser(familyId, 101L)).thenReturn(viewer);
            when(diaryRepository.findActiveByFamilyAndUserForStyle(eq(familyId), eq(targetUserId), anyInt())).thenReturn(List.of());
            when(memoryRepository.findActiveByFamilyAndUserForStyle(eq(familyId), eq(targetUserId), anyInt())).thenReturn(List.of());
            when(growthRecordRepository.findActiveByFamilyAndTargetForStyle(eq(familyId), eq(targetUserId), anyInt())).thenReturn(List.of());

            when(memoryRecallService.recallForMirror(eq(familyId), eq(targetUserId), eq(101L), eq("case11"), anyInt(), anyInt()))
                    .thenReturn(recall(List.of(diary(1L, familyId, targetUserId, "d1", "FAMILY_VISIBLE")),
                            List.of(memory(1L, familyId, targetUserId, "m1", "c1")), "case11"));
            when(memoryRecallService.recallForMirror(eq(familyId), eq(targetUserId), eq(101L), eq("case20"), anyInt(), anyInt()))
                    .thenReturn(recall(List.of(
                                    diary(2L, familyId, targetUserId, "d2", "FAMILY_VISIBLE"),
                                    diary(3L, familyId, targetUserId, "d3", "FAMILY_VISIBLE")),
                            List.of(), "case20"));
            when(memoryRecallService.recallForMirror(eq(familyId), eq(targetUserId), eq(101L), eq("case02"), anyInt(), anyInt()))
                    .thenReturn(recall(List.of(),
                            List.of(
                                    memory(2L, familyId, targetUserId, "m2", "c2"),
                                    memory(3L, familyId, targetUserId, "m3", "c3")), "case02"));
            when(memoryRecallService.recallForMirror(eq(familyId), eq(targetUserId), eq(101L), eq("case22"), anyInt(), anyInt()))
                    .thenReturn(recall(List.of(
                                    diary(4L, familyId, targetUserId, "d4", "FAMILY_VISIBLE"),
                                    diary(5L, familyId, targetUserId, "d5", "FAMILY_VISIBLE")),
                            List.of(
                                    memory(4L, familyId, targetUserId, "m4", "c4"),
                                    memory(5L, familyId, targetUserId, "m5", "c5")), "case22"));

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
            when(familyMemberRepository.findMemberViewByFamilyAndUser(familyId, targetUserId)).thenReturn(target);
            when(familyMemberRepository.findMemberViewByFamilyAndUser(familyId, 101L)).thenReturn(null);
            when(memoryRecallService.recallForMirror(eq(familyId), eq(targetUserId), eq(101L), eq("空画像"), anyInt(), anyInt()))
                    .thenReturn(recall(List.of(), List.of(), "空画像"));
            when(diaryRepository.findActiveByFamilyAndUserForStyle(eq(familyId), eq(targetUserId), anyInt())).thenReturn(List.of());
            when(memoryRepository.findActiveByFamilyAndUserForStyle(eq(familyId), eq(targetUserId), anyInt())).thenReturn(List.of());
            when(growthRecordRepository.findActiveByFamilyAndTargetForStyle(eq(familyId), eq(targetUserId), anyInt())).thenReturn(List.of());
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
            when(familyMemberRepository.findMemberViewByFamilyAndUser(familyId, targetUserId)).thenReturn(target);
            when(familyMemberRepository.findMemberViewByFamilyAndUser(familyId, 101L)).thenReturn(viewer);
            DiaryEntry selfDiary = diary(1L, familyId, targetUserId, "本人记录内容", "FAMILY_VISIBLE");
            DiaryEntry relatedDiary = diary(2L, familyId, targetUserId, "家人补充内容", "FAMILY_VISIBLE");
            relatedDiary.setMetadata(Map.of("mirrorSourceType", "RELATED_BY_FAMILY", "relatedMemberName", "妈妈"));
            when(memoryRecallService.recallForMirror(eq(familyId), eq(targetUserId), eq(101L), eq("总结"), anyInt(), anyInt()))
                    .thenReturn(recall(List.of(selfDiary, relatedDiary), List.of(), "总结"));
            when(diaryRepository.findActiveByFamilyAndUserForStyle(eq(familyId), eq(targetUserId), anyInt())).thenReturn(List.of());
            when(memoryRepository.findActiveByFamilyAndUserForStyle(eq(familyId), eq(targetUserId), anyInt())).thenReturn(List.of());
            when(growthRecordRepository.findActiveByFamilyAndTargetForStyle(eq(familyId), eq(targetUserId), anyInt())).thenReturn(List.of());

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
            when(familyMemberRepository.findMemberViewByFamilyAndUser(10L, 999L)).thenReturn(null);

            assertThrows(BusinessException.class, () -> service.getContext(10L, 999L, "任何问题"));

            verify(familyService).checkMembership(10L);
        }
    }

    private MirrorContextService service() {
        return new MirrorContextService(
                familyService,
                familyMemberRepository,
                diaryRepository,
                memoryRepository,
                growthRecordRepository,
                memoryRecallService,
                memoryLibraryService,
                mirrorAgentDataRepository);
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

    private static MemoryLibraryItem libraryItem(String id, String sourceType, String title) {
        return MemoryLibraryItem.builder()
                .id(id)
                .sourceType(sourceType)
                .title(title)
                .body(title + " 的内容")
                .memberName("目标成员")
                .visibility("FAMILY_VISIBLE")
                .metadata(Map.of())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
