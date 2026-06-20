package com.familyagent.module.media.dto;

import com.familyagent.common.constant.MediaRecordType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MediaAttachmentResponse {
    private Long id;
    private Long uploaderId;
    private Long familyId;
    private String assetUrl;
    private String mimeType;
    private Long fileSize;
    private String originalName;
    private MediaRecordType recordType;
    private Long recordId;
    private LocalDateTime createdAt;
}
