package com.familyagent.module.memorylibrary.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class MemoryLibrarySearchRequest {
    private Long familyId;
    private Integer page;
    private Integer pageSize;
    private String keyword;
    private String type;
    private Long memberUserId;
    private String visibility;
    private String tag;
    private LocalDate dateFrom;
    private LocalDate dateTo;
}
