package com.familyagent.module.diary.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UpdateDiaryEntryRequest {

    @NotBlank
    private String content;

    private String entryType;
    private String title;
    private String mood;
    private List<String> tags;
    private String visibility;
    private DiaryEntryMetadata metadata;
}
