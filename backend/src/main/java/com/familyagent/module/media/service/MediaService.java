package com.familyagent.module.media.service;

import com.familyagent.common.constant.MediaRecordType;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.media.dto.MediaAttachmentResponse;
import com.familyagent.module.media.dto.MediaContentResource;
import com.familyagent.module.media.entity.MediaAttachment;
import com.familyagent.module.media.mapper.MediaAttachmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final int MAX_FILES = 10;
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 40L * 1024 * 1024;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/heic", "image/heif");

    private final MediaAttachmentMapper attachmentMapper;
    private final MediaStorageService storageService;
    private final MediaRecordAccessFacade recordAccessFacade;

    @Transactional
    public List<MediaAttachmentResponse> upload(String recordTypeValue, Long recordId, List<MultipartFile> files) {
        MediaRecordType recordType = parseRecordType(recordTypeValue);
        MediaRecordAccess access = recordAccessFacade.requireWritable(recordType, recordId);
        validateFiles(files);

        Long uploaderId = CurrentUserGuard.currentUserId();
        List<String> uploadedKeys = new ArrayList<>();
        List<MediaAttachmentResponse> responses = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                String objectKey = storageService.upload(access.familyId(), recordType, recordId, file);
                uploadedKeys.add(objectKey);

                MediaAttachment attachment = new MediaAttachment();
                attachment.setUploaderId(uploaderId);
                attachment.setFamilyId(access.familyId());
                attachment.setObjectKey(objectKey);
                attachment.setMimeType(file.getContentType());
                attachment.setFileSize(file.getSize());
                attachment.setOriginalName(file.getOriginalFilename());
                attachment.setRecordType(recordType.name());
                attachment.setRecordId(recordId);
                attachmentMapper.insert(attachment);
                responses.add(toResponse(attachment));
            }
        } catch (RuntimeException e) {
            for (String objectKey : uploadedKeys) {
                try {
                    storageService.delete(objectKey);
                } catch (RuntimeException cleanupError) {
                    log.warn("Failed to clean up orphaned media object {}: {}", objectKey, cleanupError.getMessage());
                }
            }
            throw e;
        }
        return responses;
    }

    public List<MediaAttachmentResponse> list(String recordTypeValue, Long recordId) {
        MediaRecordType recordType = parseRecordType(recordTypeValue);
        recordAccessFacade.requireReadable(recordType, recordId);
        return attachmentMapper.selectByRecord(recordType.name(), recordId).stream()
                .map(this::toResponse)
                .toList();
    }

    public MediaContentResource getContent(Long attachmentId) {
        MediaAttachment attachment = requireAttachment(attachmentId);
        recordAccessFacade.requireReadable(parseRecordType(attachment.getRecordType()), attachment.getRecordId());
        return storageService.read(attachment.getObjectKey());
    }

    @Transactional
    public void delete(Long attachmentId) {
        MediaAttachment attachment = requireAttachment(attachmentId);
        recordAccessFacade.requireWritable(parseRecordType(attachment.getRecordType()), attachment.getRecordId());
        attachmentMapper.deleteById(attachmentId);
        try {
            storageService.delete(attachment.getObjectKey());
        } catch (RuntimeException e) {
            log.warn("Media row deleted but object cleanup failed: attachmentId={}, objectKey={}, error={}",
                    attachmentId, attachment.getObjectKey(), e.getMessage());
        }
    }

    private MediaAttachment requireAttachment(Long attachmentId) {
        MediaAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Media attachment not found");
        }
        return attachment;
    }

    private static void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Please upload at least one image");
        }
        if (files.size() > MAX_FILES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "You can upload up to " + MAX_FILES + " images at a time");
        }
        long totalBytes = 0;
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Empty file is not allowed: " + file.getOriginalFilename());
            }
            String mimeType = file.getContentType();
            if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase(Locale.ROOT))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        (file.getOriginalFilename() == null ? "File" : file.getOriginalFilename())
                                + " is not a supported image type");
            }
            if (file.getSize() > MAX_FILE_BYTES) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, file.getOriginalFilename() + " exceeds the 10 MB per-file limit");
            }
            totalBytes += file.getSize();
        }
        if (totalBytes > MAX_TOTAL_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Total upload size exceeds the 40 MB limit");
        }
    }

    private static MediaRecordType parseRecordType(String recordTypeValue) {
        try {
            return MediaRecordType.parse(recordTypeValue);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported media record type");
        }
    }

    private MediaAttachmentResponse toResponse(MediaAttachment attachment) {
        return MediaAttachmentResponse.builder()
                .id(attachment.getId())
                .uploaderId(attachment.getUploaderId())
                .familyId(attachment.getFamilyId())
                .assetUrl("/api/media/" + attachment.getId() + "/content")
                .mimeType(attachment.getMimeType())
                .fileSize(attachment.getFileSize())
                .originalName(attachment.getOriginalName())
                .recordType(parseRecordType(attachment.getRecordType()))
                .recordId(attachment.getRecordId())
                .createdAt(attachment.getCreatedAt())
                .build();
    }
}
