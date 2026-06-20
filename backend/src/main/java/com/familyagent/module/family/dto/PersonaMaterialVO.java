package com.familyagent.module.family.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PersonaMaterialVO {

    private Long id;
    private Long familyId;
    private Long personaId;
    private String title;
    private String content;
    private List<String> tags;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
