package com.familyagent.module.mirror.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.PageResult;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
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
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
            MirrorContextService service = service();
            Long familyId = 10L;
            Long targetUserId = 201L;
            String privatePhrase = "私密原文-千万不要泄露-蓝色铁盒";

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
                    .thenReturn(List.of(memory(13L, familyId, targetUserId, "私有经验", "如果重来，我会先问清楚边界。")));
            when(growthRecordRepository.findActiveByFamilyAndTargetForStyle(eq(familyId), eq(targetUserId), anyInt()))
                    .thenReturn(List.of(growth(14L, familyId, targetUserId, "EMOTION", "私下观察到压力较大")));

            MirrorContextResponse response = service.getContext(familyId, targetUserId, "选择");

            verify(familyService).checkMembership(familyId);
            assertFalse(response.getMemoryContext().contains(privatePhrase));
            assertFalse(response.getMemoryContext().contains("蓝色铁盒"));
            assertTrue(response.getMemoryContext().contains("私有风格参考"));
            assertTrue(response.getMemoryContext().contains("安全边界"));
            assertTrue(response.getMemoryContext().contains("不 得引用原文".replace(" ", "")) || response.getMemoryContext().contains("不得引用原文"));
            assertTrue(response.getMemoryContext().contains("授权日记"));
            assertTrue(response.getMemoryContext().contains("授权可见的选择记录"));
        }
    }

    @Test
    void getContext_rejectsTargetOutsideFamily() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
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
