package com.familyagent.module.photo.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PhotoUploadResponse {
    private Long id;
    private Long familyId;
    private Long uploaderId;
    private String assetUrl;
    private Object metadata;
    private LocalDateTime takenAt;
    private LocalDateTime createdAt;
}
