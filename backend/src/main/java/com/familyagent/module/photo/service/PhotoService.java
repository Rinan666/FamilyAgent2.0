package com.familyagent.module.photo.service;

import com.familyagent.common.constant.PhotoScope;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.photo.dto.PhotoClusterMetadata;
import com.familyagent.module.photo.dto.PhotoContentResource;
import com.familyagent.module.photo.dto.PhotoUploadResponse;
import com.familyagent.module.photo.entity.Photo;
import com.familyagent.module.photo.mapper.PhotoMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class PhotoService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final PhotoStorageService storageService;
    private final PhotoMapper photoMapper;
    private final FamilyService familyService;
    private final ObjectMapper objectMapper;

    private static final int MAX_FILES = 50;
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 200L * 1024 * 1024;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/heic", "image/heif");

    @Transactional
    public List<PhotoUploadResponse> upload(Long familyId, String scopeValue, String description, List<MultipartFile> files) {
        familyService.checkMembership(familyId);
        PhotoScope scope = parseUploadScope(scopeValue);
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Please upload at least one photo");
        }
        if (description != null && description.length() > 512) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Photo description exceeds the 512 character limit");
        }
        if (files.size() > MAX_FILES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "You can upload up to " + MAX_FILES + " photos at a time");
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
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Total upload size exceeds the 200 MB limit");
        }

        Long uploaderId = CurrentUserGuard.currentUserId();
        List<PhotoUploadResponse> results = new ArrayList<>();
        List<String> uploadedKeys = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                String objectKey = storageService.upload(familyId, uploaderId, scope, file);
                uploadedKeys.add(objectKey);
                Photo photo = new Photo();
                photo.setFamilyId(familyId);
                photo.setUploaderId(uploaderId);
                photo.setObjectKey(objectKey);
                photo.setScope(scope.name());
                photo.setMimeType(file.getContentType());
                photo.setFileSize(file.getSize());
                photo.setOriginalName(file.getOriginalFilename());
                photo.setDescription(description);
                photoMapper.insert(photo);
                results.add(toResponse(photo));
            }
        } catch (RuntimeException e) {
            // DB rows roll back with the transaction, but MinIO is not transactional —
            // delete every object written in this call so none are left orphaned.
            for (String key : uploadedKeys) {
                try {
                    storageService.delete(key);
                } catch (RuntimeException cleanupError) {
                    log.warn("Failed to clean up orphaned photo object {}: {}", key, cleanupError.getMessage());
                }
            }
            throw e;
        }
        return results;
    }

    public void updateClusterResult(Long photoId, PhotoClusterMetadata clusterResult) {
        Photo photo = requireUploaderPhoto(photoId);
        photo.setMetadata(clusterResult);
        photoMapper.updateById(photo);
    }

    public List<PhotoUploadResponse> listByFamily(Long familyId, int limit) {
        familyService.checkMembership(familyId);
        return photoMapper.selectByFamilyIdAndScope(familyId, PhotoScope.FAMILY.name(), normalizeLimit(limit)).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<PhotoUploadResponse> listMyPhotos(int limit) {
        Long uploaderId = CurrentUserGuard.currentUserId();
        return photoMapper.selectByUploaderIdAndScope(uploaderId, PhotoScope.PERSONAL.name(), normalizeLimit(limit)).stream()
                .map(this::toResponse)
                .toList();
    }

    public PhotoContentResource getPhotoContent(Long photoId) {
        Photo photo = requireAccessiblePhoto(photoId);
        return storageService.read(photo.getObjectKey());
    }

    public void delete(Long photoId) {
        Photo photo = photoMapper.selectById(photoId);
        if (photo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Photo not found");
        }
        Long currentUserId = CurrentUserGuard.currentUserId();
        if (!currentUserId.equals(photo.getUploaderId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the uploader can delete this photo");
        }
        photoMapper.deleteById(photoId);
        try {
            storageService.delete(photo.getObjectKey());
        } catch (RuntimeException e) {
            log.warn("Photo row deleted but object cleanup failed: photoId={}, objectKey={}, error={}",
                    photoId, photo.getObjectKey(), e.getMessage());
        }
    }

    private Photo requireAccessiblePhoto(Long photoId) {
        Photo photo = photoMapper.selectById(photoId);
        if (photo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Photo not found");
        }
        PhotoScope scope = PhotoScope.fromNullable(photo.getScope());
        if (scope == PhotoScope.PERSONAL) {
            Long currentUserId = CurrentUserGuard.currentUserId();
            if (!currentUserId.equals(photo.getUploaderId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Photo not found");
            }
            return photo;
        }
        familyService.checkMembership(photo.getFamilyId());
        return photo;
    }

    private Photo requireUploaderPhoto(Long photoId) {
        Photo photo = photoMapper.selectById(photoId);
        if (photo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Photo not found");
        }
        Long currentUserId = CurrentUserGuard.currentUserId();
        if (!currentUserId.equals(photo.getUploaderId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the uploader can update this photo");
        }
        if (PhotoScope.fromNullable(photo.getScope()) != PhotoScope.PERSONAL) {
            familyService.checkMembership(photo.getFamilyId());
        }
        return photo;
    }

    private PhotoUploadResponse toResponse(Photo photo) {
        return PhotoUploadResponse.builder()
                .id(photo.getId())
                .familyId(photo.getFamilyId())
                .uploaderId(photo.getUploaderId())
                .scope(PhotoScope.fromNullable(photo.getScope()).name())
                .assetUrl(buildAssetUrl(photo.getId()))
                .mimeType(photo.getMimeType())
                .fileSize(photo.getFileSize())
                .originalName(photo.getOriginalName())
                .description(photo.getDescription())
                .metadata(toClusterMetadata(photo.getMetadata()))
                .takenAt(photo.getTakenAt())
                .createdAt(photo.getCreatedAt())
                .build();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private PhotoScope parseUploadScope(String scopeValue) {
        if (scopeValue == null || scopeValue.isBlank()) {
            return PhotoScope.FAMILY;
        }
        try {
            return PhotoScope.valueOf(scopeValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported photo scope");
        }
    }

    private String buildAssetUrl(Long photoId) {
        return "/api/photos/" + photoId + "/content";
    }

    private PhotoClusterMetadata toClusterMetadata(Object metadata) {
        if (metadata == null) {
            return null;
        }
        if (metadata instanceof PhotoClusterMetadata clusterMetadata) {
            return clusterMetadata;
        }
        try {
            PhotoClusterMetadata converted = objectMapper.convertValue(metadata, PhotoClusterMetadata.class);
            if (converted == null
                    || (converted.groups() == null && converted.totalFaces() == null && converted.silhouetteScore() == null)) {
                return null;
            }
            return converted;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
