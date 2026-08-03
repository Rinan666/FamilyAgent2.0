package com.familyagent.module.mirror.service;

import com.familyagent.common.constant.DiaryRecallSource;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.family.dto.FamilyMemberVO;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryItem;
import com.familyagent.module.mirror.entity.MirrorAgentData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MirrorContextPromptBuilder {

    private final MirrorTemporalLayerAnnotator temporalLayerAnnotator;

    public void annotateTemporalLayers(
            List<DiaryEntry> diaries,
            List<MemoryEntry> memories,
            List<GrowthGuardRecord> growthRecords) {
        temporalLayerAnnotator.annotate(diaries, memories, growthRecords);
    }

    public String buildPrivateStyleReference(
            List<DiaryEntry> allTargetDiaries,
            List<MemoryEntry> allTargetMemories,
            List<GrowthGuardRecord> allTargetGrowthRecords) {
        int diaryCount = safeSize(allTargetDiaries);
        int memoryCount = safeSize(allTargetMemories);
        int growthCount = safeSize(allTargetGrowthRecords);
        if (diaryCount + memoryCount + growthCount == 0) {
            return "暂无可用于抽象语言风格的目标成员私有数据；只能根据对话者可见资料做低置信镜像。";
        }

        List<String> diaryTexts = allTargetDiaries == null
                ? List.of()
                : allTargetDiaries.stream()
                .map(DiaryEntry::getRawText)
                .filter(text -> text != null && !text.isBlank())
                .toList();
        int avgLength = diaryTexts.isEmpty()
                ? 0
                : (int) Math.round(diaryTexts.stream().mapToInt(String::length).average().orElse(0));
        double firstPersonRatio = ratio(diaryTexts, "我", "自己", "我的", "我们");
        double questionRatio = ratio(diaryTexts, "?", "？", "怎么", "为什么", "要不要");
        double reflectionRatio = ratio(diaryTexts, "觉得", "反思", "后来", "如果", "选择", "担心", "希望", "明白");
        double actionRatio = ratio(diaryTexts, "应该", "需要", "先", "计划", "坚持", "做到", "试试");

        Map<String, Integer> tagCounts = new LinkedHashMap<>();
        if (allTargetDiaries != null) {
            for (DiaryEntry entry : allTargetDiaries) {
                if (entry.getTags() != null) {
                    Arrays.stream(entry.getTags())
                            .filter(tag -> tag != null && !tag.isBlank())
                            .forEach(tag -> increment(tagCounts, tag.trim()));
                }
                incrementIfPresent(tagCounts, entry.getMood());
            }
        }

        Map<String, Integer> memoryTypeCounts = new LinkedHashMap<>();
        if (allTargetMemories != null) {
            for (MemoryEntry memory : allTargetMemories) {
                incrementIfPresent(memoryTypeCounts, memory.getType());
                incrementIfPresent(memoryTypeCounts, textFromMap(memory.getMetadata(), "scenario", ""));
            }
        }

        Map<String, Integer> growthCategoryCounts = new LinkedHashMap<>();
        if (allTargetGrowthRecords != null) {
            for (GrowthGuardRecord record : allTargetGrowthRecords) {
                incrementIfPresent(growthCategoryCounts, record.getCategory());
            }
        }

        return """
                - 数据覆盖：目标本人记录 %d 条，目标本人经验 %d 条，目标相关成长观察 %d 条。
                - 语言节奏：%s；平均记录长度约 %d 字。
                - 叙述视角：%s。
                - 思考方式：%s。
                - 表达拟合建议：%s。
                - 常见情绪/标签线索：%s。
                - 常见经验/价值主题：%s。
                - 常见观察主题：%s。
                - 镜像边界：以上只代表风格统计和主题倾向。可以帮助回答更像“理解过这个人”，但不得冒充现实本人，不得引用原文、透露未授权事件，或把抽象主题说成具体事实。
                """.formatted(
                diaryCount,
                memoryCount,
                growthCount,
                avgLength <= 45 ? "偏短句、直接" : avgLength <= 120 ? "中等长度、较自然" : "偏长段、复盘感较强",
                avgLength,
                firstPersonRatio >= 0.5 ? "较常从自身感受和自身选择出发" : "不总是直接表达自我，需要保留不确定性",
                reflectionRatio >= 0.45 ? "偏复盘、会追问原因和后果" : actionRatio >= 0.45 ? "偏行动安排、重视下一步怎么做" : "暂未形成稳定模式",
                questionRatio >= 0.35 ? "适合用反问、商量式语气延伸" : "适合用平实陈述和温和建议延伸",
                topItems(tagCounts, 8),
                topItems(memoryTypeCounts, 8),
                topItems(growthCategoryCounts, 6));
    }

    public String buildMemoryContext(
            FamilyMemberVO viewer,
            FamilyMemberVO target,
            List<DiaryEntry> diaries,
            List<MemoryEntry> memories,
            List<GrowthGuardRecord> growthRecords,
            List<MemoryLibraryItem> libraryItems,
            MirrorAgentData mirrorProfile,
            String privateStyleReference) {
        String name = memberName(target);
        String viewerName = memberName(viewer);
        String viewerRole = viewer == null || viewer.getRole() == null ? "UNKNOWN" : viewer.getRole();
        String targetRole = target == null || target.getRole() == null ? "UNKNOWN" : target.getRole();
        String identityContext = "当前服务器时间：" + LocalDateTime.now()
                + "\n" + ageLine("当前对话者年龄", viewer)
                + "\n" + ageLine("镜像对象年龄", target);
        String relationshipLine = target.getRelationshipLabel() == null || target.getRelationshipLabel().isBlank()
                ? "当前用户尚未为该成员设置亲属称呼。"
                : "当前用户对镜像对象的称呼：" + target.getRelationshipLabel();
        String baseContext = """
                当前对话者：%s
                当前对话者在本家族的软件身份：%s
                镜像参考对象：%s
                镜像对象在本家族的软件身份：%s
                %s

                边界：
                - 我不是 %s 本人，也不能声称自己代表 %s 的真实想法。
                - 我的回答只能基于以下“对话者已授权可见”的记录、经验和知识库片段。
                - “私有风格参考”来自镜像对象的全部可用记录，但已经被后端抽象为语言风格、价值排序和推测边界；它只能帮助我拟合表达方式，不能当作可透露事实，也不能引用、复述或暗示其中的私密事件。
                - “本人记录”代表目标成员本人记录；“家人补充”代表家人为该成员补充的观察或留言，只能作为外部观察线索，不能当作本人自述。
                - 每条记录都有时间层级：近期可作为当前线索；淡出/印象只能说明过去曾经如此，不能推断现在仍然如此；沉淀记忆可作为较稳定的经验沉淀或价值观参考。
                - 回答时先理解用户真正想问的关系、选择或情绪张力，再引用资料线索；不要把记录逐条复读成检索报告。
                - 如果要模拟 %s 的视角，可以写得有性格、有判断、有温度；但应保留“基于授权资料的可能看法”这一事实边界，不冒充现实本人正在发言。
                - 如果用户直接追问只存在于私密日记里的内容，我必须拒绝并模糊带过，不确认、不展开、不泄露，也不让回答反推出私密日记细节。
                - 额外匹配片段来自每日记录、成长观察和经验沉淀的合并检索，只能作为已授权线索；只有在用户追问依据或需要判断时，才用自然称呼轻量说明来源，不暴露 D1、R1、M1、L1 这类内部编号。

                私有风格参考（只用于拟合语气和逻辑，不得透露为事实）：
                %s

                授权画像摘要：
                %s

                授权日记：
                %s

                经验沉淀：
                %s

                本轮额外匹配的家族记忆片段：
                %s
                """.formatted(
                viewerName,
                viewerRole,
                name,
                targetRole,
                relationshipLine,
                name,
                name,
                name,
                privateStyleReference,
                mirrorProfileLines(mirrorProfile),
                diaryLines(diaries),
                memoryLines(memories) + "\n\n" + growthLines(growthRecords),
                libraryLines(libraryItems));
        return identityContext + "\n" + baseContext;
    }

    public String buildSourceSummary(
            List<DiaryEntry> diaries,
            List<MemoryEntry> memories,
            List<MemoryLibraryItem> libraryItems) {
        long relatedCount = diaries == null ? 0 : diaries.stream().filter(MirrorContextPromptBuilder::isRelatedDiary).count();
        long selfCount = safeSize(diaries) - relatedCount;
        return "已加载 " + selfCount + " 条本人记录、"
                + relatedCount + " 条家人补充、"
                + safeSize(memories) + " 条可见经验沉淀、"
                + safeSize(libraryItems) + " 条本轮额外匹配的家族记忆片段。";
    }

    public String buildSourceSummary(
            List<DiaryEntry> diaries,
            List<MemoryEntry> memories,
            List<GrowthGuardRecord> growthRecords,
            List<MemoryLibraryItem> libraryItems) {
        long relatedCount = diaries == null ? 0 : diaries.stream().filter(MirrorContextPromptBuilder::isRelatedDiary).count();
        long selfCount = safeSize(diaries) - relatedCount;
        return "已加载 " + selfCount + " 条本人记录、"
                + relatedCount + " 条家人补充、"
                + safeSize(growthRecords) + " 条可见成长观察、"
                + safeSize(memories) + " 条可见经验沉淀、"
                + safeSize(libraryItems) + " 条本轮额外匹配的家族记忆片段。";
    }

    public List<String> buildSuggestedQuestions(
            FamilyMemberVO target,
            List<DiaryEntry> diaries,
            List<GrowthGuardRecord> growthRecords) {
        String name = memberName(target);
        if (safeSize(diaries) == 0 && safeSize(growthRecords) == 0) {
            return List.of(
                    "现在记录还不多，我应该先补充哪些内容？",
                    "如果只基于现有资料，你能确定什么、不能确定什么？",
                    "我该怎样开始写第一条有价值的家族日记？"
            );
        }
        return List.of(
                "基于授权记录，" + name + "在做选择时可能更看重什么？",
                "如果以" + name + "的经历作参考，我现在这个问题可以怎么拆解？",
                "哪些经验沉淀可能对我现在的处境有帮助？",
                "从现有记录看，我还需要补充哪些信息才能让镜像参考更准确？"
        );
    }

    public List<String> buildMissingRecordSuggestions(List<DiaryEntry> diaries, List<GrowthGuardRecord> growthRecords) {
        if (safeSize(diaries) >= 3 && safeSize(growthRecords) >= 2) {
            return List.of();
        }

        if (safeSize(diaries) == 0) {
            return List.of(
                    "补充 2-3 条目标成员自己的日记或重要事件记录。",
                    "记录一次真实选择：当时遇到什么、为什么那样决定、后来结果如何。",
                    "补充一条“给家人的话”，帮助 AI 理解表达方式和边界。"
            );
        }

        if (safeSize(growthRecords) == 0) {
            return List.of(
                    "补充 1-2 条长者经验或家族故事，让镜像参考有家庭价值观来源。",
                    "为经验标注适用场景，例如视力保护、挫折、选择、亲子沟通。",
                    "补充一条健康或成长提醒，帮助 AI 输出更具体的行动建议。"
            );
        }

        return List.of(
                "继续补充不同阶段的日记，避免镜像只依据单一事件判断。",
                "补充更多经验沉淀或长者建议，让回答更有家庭脉络。",
                "补充一次复盘记录：问题、行动、结果和后来学到的经验。"
        );
    }

    private static double ratio(List<String> texts, String... tokens) {
        if (texts == null || texts.isEmpty()) {
            return 0;
        }
        long matched = texts.stream().filter(text -> containsAny(text, tokens)).count();
        return matched * 1.0 / texts.size();
    }

    private static boolean containsAny(String text, String... tokens) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static void incrementIfPresent(Map<String, Integer> counts, String value) {
        if (value != null && !value.isBlank()) {
            increment(counts, value.trim());
        }
    }

    private static void increment(Map<String, Integer> counts, String value) {
        counts.put(value, counts.getOrDefault(value, 0) + 1);
    }

    private static String topItems(Map<String, Integer> counts, int limit) {
        if (counts == null || counts.isEmpty()) {
            return "暂无稳定线索";
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(entry -> entry.getKey() + "×" + entry.getValue())
                .toList()
                .toString();
    }

    private static String mirrorProfileLines(MirrorAgentData profile) {
        if (profile == null || profile.getTraits() == null || profile.getTraits().isEmpty()) {
            return "暂无该成员的授权画像摘要。";
        }
        return mapText(profile.getTraits(), 600);
    }

    private String diaryLines(List<DiaryEntry> diaries) {
        if (diaries == null || diaries.isEmpty()) {
            return "暂无该成员的授权日记。";
        }
        StringBuilder builder = new StringBuilder();
        int selfIndex = 0;
        int relatedIndex = 0;
        for (DiaryEntry entry : diaries) {
            boolean related = isRelatedDiary(entry);
            int sourceIndex = related ? ++relatedIndex : ++selfIndex;
            builder.append(related ? "家人补充 " : "本人记录 ").append(sourceIndex)
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
            builder.append("；").append(diaryRecordContext(entry, related));
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private String memoryLines(List<MemoryEntry> memories) {
        if (memories == null || memories.isEmpty()) {
            return "暂无可见经验沉淀。";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < memories.size(); i++) {
            MemoryEntry memory = memories.get(i);
            builder.append("家族成员的经验 ").append(i + 1)
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
                    .append("；")
                    .append(memoryRecordContext(memory))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private String growthLines(List<GrowthGuardRecord> growthRecords) {
        if (growthRecords == null || growthRecords.isEmpty()) {
            return "暂无可见成长观察。";
        }
        StringBuilder builder = new StringBuilder("成长观察：\n");
        for (int i = 0; i < growthRecords.size(); i++) {
            GrowthGuardRecord record = growthRecords.get(i);
            builder.append("观察 ").append(i + 1)
                    .append(". [")
                    .append(textFromMap(record.getMetadata(), "temporalLayerLabel", "未分层"))
                    .append("；")
                    .append(record.getCategory() == null ? "OTHER" : record.getCategory())
                    .append("；severity=")
                    .append(record.getSeverity() == null ? 0 : record.getSeverity())
                    .append("] ")
                    .append(truncate(record.getContent(), 220))
                    .append("；")
                    .append(growthRecordContext(record))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private static String libraryLines(List<MemoryLibraryItem> items) {
        if (items == null || items.isEmpty()) {
            return "本轮未额外匹配到相关家族记忆片段。";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            MemoryLibraryItem item = items.get(i);
            builder.append("额外匹配的家族记忆 ").append(i + 1)
                    .append(". [")
                    .append(sourceTypeLabel(item.getSourceType()))
                    .append("；")
                    .append(item.getMemberName() == null || item.getMemberName().isBlank()
                            ? "未知成员"
                            : item.getMemberName())
                    .append("；")
                    .append(item.getVisibility() == null ? "UNKNOWN" : item.getVisibility())
                    .append("] ")
                    .append(item.getTitle() == null || item.getTitle().isBlank()
                            ? "未命名片段"
                            : item.getTitle())
                    .append("：")
                    .append(truncate(item.getBody(), 220))
                    .append("；")
                    .append(libraryRecordContext(item))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private String diaryRecordContext(DiaryEntry entry, boolean related) {
        LocalDateTime recordTime = temporalLayerAnnotator.referenceTime(entry.getMetadata(), entry.getCreatedAt());
        String source = related
                ? textFromMap(entry.getMetadata(), "relatedMemberName", "家人补充")
                : "镜像对象本人";
        return "记录时间：" + timeLabel(recordTime)
                + "；创建时间：" + timeLabel(entry.getCreatedAt())
                + "；作者/来源：" + source
                + "；时间层级：" + textFromMap(entry.getMetadata(), "temporalLayerLabel", "未分层");
    }

    private String memoryRecordContext(MemoryEntry memory) {
        String status = textFromMap(memory.getMetadata(), "curationStatus", "");
        String reason = firstNonBlank(
                textFromMap(memory.getMetadata(), "promotionReason", ""),
                textFromMap(memory.getMetadata(), "coreReason", ""),
                textFromMap(memory.getMetadata(), "curationReason", ""));
        String proposer = textFromMap(memory.getMetadata(), "promotedByName", textFromMap(memory.getMetadata(), "createdByName", ""));
        return "记录时间：" + timeLabel(temporalLayerAnnotator.referenceTime(memory.getMetadata(), memory.getCreatedAt()))
                + "；更新时间：" + timeLabel(memory.getUpdatedAt())
                + (status.isBlank() ? "" : "；沉淀状态：" + status)
                + (reason.isBlank() ? "" : "；沉淀理由：" + truncate(reason, 80))
                + (proposer.isBlank() ? "" : "；提出人：" + proposer);
    }

    private String growthRecordContext(GrowthGuardRecord record) {
        return "观察日期：" + (record.getObservedAt() == null ? "未知" : record.getObservedAt())
                + "；创建时间：" + timeLabel(record.getCreatedAt())
                + "；可见范围：" + (record.getVisibility() == null ? "UNKNOWN" : record.getVisibility())
                + "；时间层级：" + textFromMap(record.getMetadata(), "temporalLayerLabel", "未分层");
    }

    private static String libraryRecordContext(MemoryLibraryItem item) {
        String status = textFromMap(item.getMetadata(), "curationStatus", "");
        String reason = firstNonBlank(
                textFromMap(item.getMetadata(), "promotionReason", ""),
                textFromMap(item.getMetadata(), "coreReason", ""),
                textFromMap(item.getMetadata(), "curationReason", ""));
        return "记录归属：" + (item.getMemberName() == null || item.getMemberName().isBlank() ? "未知成员" : item.getMemberName())
                + "；创建时间：" + timeLabel(item.getCreatedAt())
                + "；更新时间：" + timeLabel(item.getUpdatedAt())
                + (status.isBlank() ? "" : "；沉淀状态：" + status)
                + (reason.isBlank() ? "" : "；沉淀理由：" + truncate(reason, 80));
    }

    private static String sourceTypeLabel(String sourceType) {
        if ("LIFE_RECORD".equals(sourceType)) {
            return "每日记录";
        }
        if ("FAMILY_EXPERIENCE".equals(sourceType)) {
            return "经验沉淀";
        }
        if ("GROWTH_OBSERVATION".equals(sourceType)) {
            return "成长观察";
        }
        return "记忆片段";
    }

    private static int safeSize(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private static String ageLine(String label, FamilyMemberVO member) {
        Integer age = memberAge(member);
        return label + "：" + (age == null ? "未设置" : age + "岁");
    }

    private static Integer memberAge(FamilyMemberVO member) {
        if (member == null) {
            return null;
        }
        if (member.getBirthDate() != null && !member.getBirthDate().isBlank()) {
            try {
                LocalDate birthDate = LocalDate.parse(member.getBirthDate().trim());
                int age = Period.between(birthDate, LocalDate.now()).getYears();
                return age >= 0 && age <= 130 ? age : null;
            } catch (DateTimeParseException ignored) {
                // Try birth year below.
            }
        }
        if (member.getBirthYear() == null || member.getBirthYear().isBlank()) {
            return null;
        }
        try {
            int birthYear = Integer.parseInt(member.getBirthYear().trim());
            int age = LocalDate.now().getYear() - birthYear;
            return age >= 0 && age <= 130 ? age : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
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

    private static String timeLabel(LocalDateTime value) {
        return value == null ? "未知" : value.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
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
        return DiaryRecallSource.RELATED_BY_FAMILY.name().equals(textFromMap(
                entry == null ? null : entry.getMetadata(),
                DiaryRecallSource.METADATA_KEY,
                ""));
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

}
