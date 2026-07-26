package com.familyagent.module.diary.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DiaryEntry {

    private Long id;

    private Long userId;
    private Long familyId;
    private String rawText;

    private Object structured;

    private String mood;

    private String[] tags;

    private String privacyLevel;
    private String visibility;

    private Object permissionScope;

    private String source;
    private String voiceUrl;

    private Object metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
