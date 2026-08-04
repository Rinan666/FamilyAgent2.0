package com.familyagent.module.memory.service;

import com.familyagent.common.constant.MemoryRecallSourceType;
import com.familyagent.module.family.facade.FamilyRelationshipGraphView;
import com.familyagent.module.family.facade.FamilyRelationshipNode;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
import com.familyagent.module.memory.dto.RecallParticipantSummary;
import com.familyagent.module.memory.dto.RecallSourceSummary;
import com.familyagent.module.memory.entity.MemoryEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class AuthorizedMemoryRecallSourceAssembler {

    public List<RecallSourceSummary> assemble(
            List<AuthorizedMemoryRecallCandidate> diaries,
            List<AuthorizedMemoryRecallCandidate> memories,
            List<AuthorizedMemoryRecallCandidate> growthRecords,
            FamilyRelationshipGraphView relationships) {
        List<RecallSourceSummary> summaries = new ArrayList<>();
        append(summaries, diaries, relationships);
        append(summaries, memories, relationships);
        append(summaries, growthRecords, relationships);
        return summaries;
    }

    public List<RecallSourceSummary> assemble(
            List<AuthorizedMemoryRecallCandidate> candidates,
            FamilyRelationshipGraphView relationships) {
        List<RecallSourceSummary> summaries = new ArrayList<>();
        append(summaries, candidates, relationships);
        return summaries;
    }

    public Set<Long> participantUserIds(
            List<AuthorizedMemoryRecallCandidate> diaries,
            List<AuthorizedMemoryRecallCandidate> memories,
            List<AuthorizedMemoryRecallCandidate> growthRecords) {
        return Stream.of(diaries, memories, growthRecords)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .flatMap(candidate -> Stream.of(candidate.authorUserId(), candidate.subjectUserId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public Set<Long> participantUserIds(List<AuthorizedMemoryRecallCandidate> candidates) {
        return candidates.stream()
                .filter(Objects::nonNull)
                .flatMap(candidate -> Stream.of(candidate.authorUserId(), candidate.subjectUserId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static void append(
            List<RecallSourceSummary> summaries,
            List<AuthorizedMemoryRecallCandidate> candidates,
            FamilyRelationshipGraphView relationships) {
        for (AuthorizedMemoryRecallCandidate candidate : candidates) {
            if (candidate == null || candidate.entry().getId() == null) {
                continue;
            }
            MemoryEntry entry = candidate.entry();
            Map<?, ?> index = metadataIndex(entry.getMetadata());
            RecallSourceSummary.RecallSourceSummaryBuilder builder = RecallSourceSummary.builder()
                    .id(candidate.publicId())
                    .sourceType(candidate.sourceType().name())
                    .title(title(candidate))
                    .snippet(snippet(firstNonBlank(entry.getSummary(), entry.getContent(), "")))
                    .visibility(entry.getScope())
                    .temporalLayer(asString(index.get("temporalLayer")))
                    .topics(stringList(index.get("topics")))
                    .scenes(stringList(index.get("scenes")));
            if (candidate.sourceType() == MemoryRecallSourceType.GROWTH_OBSERVATION) {
                builder.observer(participant(relationships, candidate.authorUserId()))
                        .subject(participant(relationships, candidate.subjectUserId()));
            } else {
                builder.author(participant(relationships, candidate.authorUserId()));
            }
            summaries.add(builder.build());
        }
    }

    private static String title(AuthorizedMemoryRecallCandidate candidate) {
        MemoryEntry entry = candidate.entry();
        return switch (candidate.sourceType()) {
            case LIFE_RECORD -> firstNonBlank(
                    entry.getTitle(),
                    nestedText(entry.getMetadata(), "legacyDiary", "entryType"),
                    "Daily record");
            case GROWTH_OBSERVATION -> firstNonBlank(
                    nestedText(entry.getMetadata(), "legacyGrowth", "category"),
                    entry.getTitle(),
                    "Growth observation");
            case FAMILY_EXPERIENCE, PERSONAL_MEMORY -> firstNonBlank(
                    entry.getTitle(), entry.getSummary(), entry.getType(), "Memory");
        };
    }

    private static RecallParticipantSummary participant(
            FamilyRelationshipGraphView relationships,
            Long userId) {
        FamilyRelationshipNode node = relationships.member(userId);
        return new RecallParticipantSummary(
                node.userId(),
                node.displayName(),
                node.relationshipToViewer(),
                node.relationshipToTarget(),
                node.currentViewer(),
                node.currentTarget());
    }

    private static Map<?, ?> metadataIndex(Object metadata) {
        if (metadata instanceof Map<?, ?> map && map.get("index") instanceof Map<?, ?> index) {
            return index;
        }
        return Map.of();
    }

    private static String nestedText(Object metadata, String objectKey, String valueKey) {
        if (metadata instanceof Map<?, ?> map
                && map.get(objectKey) instanceof Map<?, ?> nested
                && nested.get(valueKey) != null) {
            return String.valueOf(nested.get(valueKey));
        }
        return "";
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(item -> !item.isBlank())
                .limit(6)
                .toList();
    }

    private static String snippet(String value) {
        String text = firstNonBlank(value, "").replaceAll("\\s+", " ").trim();
        return text.length() <= 90 ? text : text.substring(0, 90) + "...";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
