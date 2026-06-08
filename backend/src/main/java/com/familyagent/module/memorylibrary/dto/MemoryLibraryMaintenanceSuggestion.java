package com.familyagent.module.memorylibrary.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MemoryLibraryMaintenanceSuggestion {
    private String action;
    private int score;
    private String title;
    private String reason;
    private List<String> reasons;
    private List<MemoryLibraryItem> items;
}
