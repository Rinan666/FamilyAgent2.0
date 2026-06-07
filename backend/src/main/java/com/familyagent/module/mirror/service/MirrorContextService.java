package com.familyagent.module.mirror.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.family.dto.FamilyMemberVO;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallService;
import com.familyagent.module.mirror.dto.MirrorContextResponse;
import com.familyagent.module.mirror.entity.MirrorAgentData;
import com.familyagent.module.mirror.repository.MirrorAgentDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MirrorContextService {

    private static final int DIARY_LIMIT = 12;
    private static final int MEMORY_LIMIT = 10;
    private static final String DISCLAIMER = "镜像 Agent 不是本人，也不代表本人真实想法；它只能基于授权可见的家族日记和家族经验做谨慎参考。记录不足时应直接说明不确定。";

    private final FamilyService familyService;
    private final FamilyMemberRepository familyMemberRepository;
    private final AuthorizedMemoryRecallService memoryRecallService;
    private final MirrorAgentDataRepository mirrorAgentDataRepository;

    public MirrorContextResponse getContext(Long familyId, Long targetUserId, String query) {
        Long viewerUserId = CurrentUserGuard.currentUserId();
        familyService.checkMembership(familyId);

        FamilyMemberVO target = familyMemberRepository.findMemberViewByFamilyAndUser(familyId, targetUserId);
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_FAMILY_MEMBER, "镜像对象不属于当前家族");
        }
        familyService.attachRelationshipLabels(familyId, viewerUserId, List.of(target));

        AuthorizedMemoryRecallResult recall = memoryRecallService.recallForMirror(
                familyId,
                targetUserId,
                viewerUserId,
                query,
                DIARY_LIMIT,
                MEMORY_LIMIT);
        List<DiaryEntry> diaries = recall.getDiaries();
        List<MemoryEntry> memories = recall.getMemories();
        annotateTemporalLayers(diaries, memories);
        MirrorAgentData mirrorProfile = mirrorAgentDataRepository.findVisibleByFamilyAndTarget(
                familyId,
                targetUserId,
                viewerUserId);

        boolean insufficientRecords = diaries.size() < 2 && memories.size() < 2;
        return MirrorContextResponse.builder()
                .familyId(familyId)
                .viewerUserId(viewerUserId)
                .targetMember(target)
                .diaries(diaries)
                .memories(memories)
                .mirrorProfile(mirrorProfile == null ? Map.of() : mirrorProfile.getTraits())
                .memoryContext(buildMemoryContext(target, diaries, memories, mirrorProfile))
                .disclaimer(DISCLAIMER)
                .insufficientRecords(insufficientRecords)
                .sourceSummary(buildSourceSummary(diaries, memories))
                .retrievalMode(recall.getRetrievalMode())
                .retrievalQuery(recall.getQuery())
                .embeddingReadyCount(recall.getEmbeddingReadyCount())
                .suggestedQuestions(buildSuggestedQuestions(target, diaries, memories))
                .missingRecordSuggestions(buildMissingRecordSuggestions(diaries, memories))
                .build();
    }

    private static String buildMemoryContext(
            FamilyMemberVO target,
            List<DiaryEntry> diaries,
            List<MemoryEntry> memories,
            MirrorAgentData mirrorProfile) {
        String name = memberName(target);
        String relationshipLine = target.getRelationshipLabel() == null || target.getRelationshipLabel().isBlank()
                ? "当前用户尚未为该成员设置亲属称呼。"
                : "当前用户对镜像对象的称呼：" + target.getRelationshipLabel();
        return """
                镜像参考对象：%s
                %s

                边界：
                - 你不是 %s 本人，也不能声称自己代表 %s 的真实想法。
                - 只能基于以下后端权限过滤后的授权记录，做“风格参考、价值观参考、人生经验整理、自我复盘辅助”。
                - D 编号代表目标成员本人记录；R 编号代表家人为该成员补充的观察或留言，只能作为外部观察线索，不能当作本人自述。
                - 每条记录都有时间层级：近期可作为当前线索；淡出/印象只能说明过去曾经如此，不能推断现在仍然如此；沉淀记忆可作为较稳定的家族经验或价值观参考。
                - 如果记录不足，要直接说明“不确定”，不要编造经历、隐私或情绪。
                - 回答时可以使用第一人称模拟语气，但必须保持克制，并避免制造依赖。
                - 回答开头用一句话说明“我参考了 X 条授权日记和 Y 条可见家族经验”。
                - 涉及具体判断时，尽量用“从本人记录 D1 / 家人补充 R1 / 家族经验 M1 可以看出...”这样的编号引用；不要暴露未授权内容。

                授权画像摘要：
                %s

                授权日记：
                %s

                家族经验：
                %s
                """.formatted(name, relationshipLine, name, name, mirrorProfileLines(mirrorProfile), diaryLines(diaries), memoryLines(memories));
    }

    private static String mirrorProfileLines(MirrorAgentData profile) {
        if (profile == null || profile.getTraits() == null || profile.getTraits().isEmpty()) {
            return "暂无该成员的授权画像摘要。";
        }
        return mapText(profile.getTraits(), 600);
    }

    private static String diaryLines(List<DiaryEntry> diaries) {
        if (diaries == null || diaries.isEmpty()) {
            return "暂无该成员的授权日记。";
        }
        StringBuilder builder = new StringBuilder();
        int selfIndex = 0;
        int relatedIndex = 0;
        for (DiaryEntry entry : diaries) {
            boolean related = isRelatedDiary(entry);
            int sourceIndex = related ? ++relatedIndex : ++selfIndex;
            builder.append(related ? "R" : "D").append(sourceIndex)
                    .append(". [")
                    .append(related ? "家人补充；" : "本人记录；")
                    .append(textFromMap(entry.getMetadata(), "temporalLayerLabel", "未分层"))
                    .append("；")
                    .append(textFromMap(entry.getStructured(), "entryType", "DIARY"))
                    .append("] ")
                    .append(textFromMap(entry.getStructured(), "title", textFromMap(entry.getStructured(), "summary", "未命名记录")))
                    .append("：")
                    .append(truncate(entry.getRawText(), 260));
            if (entry.getMood() != null && !entry.getMood().isBlank()) {
                builder.append("；心情：").append(entry.getMood());
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private static String memoryLines(List<MemoryEntry> memories) {
        if (memories == null || memories.isEmpty()) {
            return "暂无可见家族经验。";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < memories.size(); i++) {
            MemoryEntry memory = memories.get(i);
            builder.append("M").append(i + 1)
                    .append(". [")
                    .append(textFromMap(memory.getMetadata(), "temporalLayerLabel", "未分层"))
                    .append("；")
                    .append(memory.getType() == null ? "FAMILY" : memory.getType());
            String scenario = textFromMap(memory.getMetadata(), "scenario", "");
            if (!scenario.isBlank()) {
                builder.append("；场景：").append(scenario);
            }
            builder.append("] ")
                    .append(memory.getSummary() == null || memory.getSummary().isBlank()
                            ? truncate(memory.getContent(), 220)
                            : memory.getSummary())
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private static String buildSourceSummary(List<DiaryEntry> diaries, List<MemoryEntry> memories) {
        long relatedCount = diaries == null ? 0 : diaries.stream().filter(MirrorContextService::isRelatedDiary).count();
        long selfCount = safeSize(diaries) - relatedCount;
        return "已加载 " + selfCount + " 条本人记录、" + relatedCount + " 条家人补充、" + safeSize(memories) + " 条可见家族经验。";
    }

    private static List<String> buildSuggestedQuestions(
            FamilyMemberVO target,
            List<DiaryEntry> diaries,
            List<MemoryEntry> memories) {
        String name = memberName(target);
        if (safeSize(diaries) == 0 && safeSize(memories) == 0) {
            return List.of(
                    "现在记录还不多，我应该先补充哪些内容？",
                    "如果只基于现有资料，你能确定什么、不能确定什么？",
                    "我该怎样开始写第一条有价值的家族日记？"
            );
        }
        return List.of(
                "基于授权记录，" + name + "在做选择时可能更看重什么？",
                "如果以" + name + "的经历作参考，我现在这个问题可以怎么拆解？",
                "哪些家族经验可能对我现在的处境有帮助？",
                "从现有记录看，我还需要补充哪些信息才能让镜像参考更准确？"
        );
    }

    private static List<String> buildMissingRecordSuggestions(List<DiaryEntry> diaries, List<MemoryEntry> memories) {
        if (safeSize(diaries) >= 3 && safeSize(memories) >= 2) {
            return List.of();
        }

        if (safeSize(diaries) == 0) {
            return List.of(
                    "补充 2-3 条目标成员自己的日记或重要事件记录。",
                    "记录一次真实选择：当时遇到什么、为什么那样决定、后来结果如何。",
                    "补充一条“给家人的话”，帮助 AI 理解表达方式和边界。"
            );
        }

        if (safeSize(memories) == 0) {
            return List.of(
                    "补充 1-2 条长者经验或家族故事，让镜像参考有家庭价值观来源。",
                    "为经验标注适用场景，例如视力保护、挫折、选择、亲子沟通。",
                    "补充一条健康或成长提醒，帮助 AI 输出更具体的行动建议。"
            );
        }

        return List.of(
                "继续补充不同阶段的日记，避免镜像只依据单一事件判断。",
                "补充更多家族经验或长者建议，让回答更有家庭脉络。",
                "补充一次复盘记录：问题、行动、结果和后来学到的经验。"
        );
    }

    private static int safeSize(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private static void annotateTemporalLayers(List<DiaryEntry> diaries, List<MemoryEntry> memories) {
        if (diaries != null) {
            diaries.forEach(entry -> {
                TemporalLayer layer = temporalLayer(entry.getCreatedAt(), false, 0);
                entry.setMetadata(mergeTemporalMetadata(entry.getMetadata(), layer));
            });
        }
        if (memories != null) {
            memories.forEach(memory -> {
                boolean core = isCoreMemory(memory);
                TemporalLayer layer = temporalLayer(
                        memory.getUpdatedAt() == null ? memory.getCreatedAt() : memory.getUpdatedAt(),
                        core,
                        memory.getImportance() == null ? 0 : memory.getImportance());
                memory.setMetadata(mergeTemporalMetadata(memory.getMetadata(), layer));
            });
        }
    }

    private static boolean isCoreMemory(MemoryEntry memory) {
        if (memory == null) {
            return false;
        }
        if ("true".equalsIgnoreCase(textFromMap(memory.getMetadata(), "coreMemory", ""))) {
            return true;
        }
        if (memory.getImportance() != null && memory.getImportance() >= 4) {
            return true;
        }
        String type = memory.getType() == null ? "" : memory.getType();
        return "ELDER_ADVICE".equals(type) || "VALUE".equals(type) || "FAMILY_STORY".equals(type);
    }

    private static TemporalLayer temporalLayer(LocalDateTime time, boolean coreMemory, int importance) {
        if (coreMemory) {
            double weight = importance >= 5 ? 1.0 : 0.9;
            return new TemporalLayer(
                    "CORE_MEMORY",
                    "沉淀记忆",
                    BigDecimal.valueOf(weight),
                    "这类记录更像家族经验或价值观沉淀，时间衰减较慢。");
        }
        if (time == null) {
            return new TemporalLayer(
                    "IMPRESSION",
                    "印象",
                    BigDecimal.valueOf(0.35),
                    "缺少明确时间，只能作为模糊印象参考。");
        }
        long days = Math.max(0, Duration.between(time, LocalDateTime.now()).toDays());
        if (days <= 30) {
            return new TemporalLayer(
                    "FRESH",
                    "近期",
                    BigDecimal.valueOf(1.0),
                    "近期记录，可以作为当前线索，但仍需结合上下文。");
        }
        if (days <= 180) {
            return new TemporalLayer(
                    "FADING",
                    "淡出",
                    BigDecimal.valueOf(0.65),
                    "这件事正在淡出，只能说明一段时间内有过相关迹象。");
        }
        return new TemporalLayer(
                "IMPRESSION",
                "印象",
                BigDecimal.valueOf(0.35),
                "时间较久，只能作为过去印象，不能直接判断当前状态。");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mergeTemporalMetadata(Object metadata, TemporalLayer layer) {
        Map<String, Object> next = new LinkedHashMap<>();
        if (metadata instanceof Map<?, ?> map) {
            next.putAll((Map<String, Object>) map);
        }
        next.put("temporalLayer", layer.code());
        next.put("temporalLayerLabel", layer.label());
        next.put("temporalWeight", layer.weight());
        next.put("temporalNote", layer.note());
        return next;
    }

    private static String memberName(FamilyMemberVO member) {
        if (member == null) {
            return "家族成员";
        }
        if (member.getRelationshipLabel() != null && !member.getRelationshipLabel().isBlank()) {
            return member.getRelationshipLabel();
        }
        if (member.getNickname() != null && !member.getNickname().isBlank()) {
            return member.getNickname();
        }
        if (member.getUsername() != null && !member.getUsername().isBlank()) {
            return member.getUsername();
        }
        return "用户 " + member.getUserId();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private static String textFromMap(Object value, String key, String fallback) {
        if (value instanceof Map<?, ?> map) {
            Object item = map.get(key);
            if (item != null && !String.valueOf(item).isBlank()) {
                return String.valueOf(item);
            }
        }
        return fallback;
    }

    private static boolean isRelatedDiary(DiaryEntry entry) {
        return "RELATED_BY_FAMILY".equals(textFromMap(entry == null ? null : entry.getMetadata(), "mirrorSourceType", ""));
    }

    private static String mapText(Map<String, Object> map, int maxLength) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() != null) {
                builder.append(entry.getKey()).append("：").append(entry.getValue()).append('\n');
            }
        }
        return truncate(builder.toString().trim(), maxLength);
    }

    private record TemporalLayer(String code, String label, BigDecimal weight, String note) {}
}
