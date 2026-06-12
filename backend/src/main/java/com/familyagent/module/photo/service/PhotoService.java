package com.familyagent.module.photo.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.photo.dto.PhotoContentResource;
import com.familyagent.module.photo.dto.PhotoUploadResponse;
import com.familyagent.module.photo.entity.Photo;
import com.familyagent.module.photo.mapper.PhotoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PhotoService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final PhotoStorageService storageService;
    private final PhotoMapper photoMapper;
    private final FamilyService familyService;

    private static final int MAX_FILES = 50;
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 200L * 1024 * 1024;

    public List<PhotoUploadResponse> upload(Long familyId, List<MultipartFile> files) {
        familyService.checkMembership(familyId);
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Please upload at least one photo");
        }
        if (files.size() > MAX_FILES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "You can upload up to " + MAX_FILES + " photos at a time");
        }
        long totalBytes = 0;
        for (MultipartFile file : files) {
            if (file.getSize() > MAX_FILE_BYTES) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, file.getOriginalFilename() + " exceeds the 10 MB per-file limit");
            }
            totalBytes += file.getSize();
        }
        if (totalBytes > MAX_TOTAL_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Total upload size exceeds the 40 MB limit");
        }

        Long uploaderId = CurrentUserGuard.currentUserId();
        List<PhotoUploadResponse> results = new ArrayList<>();
        for (MultipartFile file : files) {
            String objectKey = storageService.upload(familyId, file);
            Photo photo = new Photo();
            photo.setFamilyId(familyId);
            photo.setUploaderId(uploaderId);
            photo.setObjectKey(objectKey);
            photoMapper.insert(photo);
            results.add(toResponse(photo));
        }
        return results;
    }

    public void updateClusterResult(Long photoId, Object clusterResult) {
        Photo photo = requireAccessiblePhoto(photoId);
        photo.setMetadata(clusterResult);
        photoMapper.updateById(photo);
    }

    public List<PhotoUploadResponse> listByFamily(Long familyId, int limit) {
        familyService.checkMembership(familyId);
        return photoMapper.selectByFamilyId(familyId, normalizeLimit(limit)).stream()
                .map(this::toResponse)
                .toList();
    }

    public PhotoContentResource getPhotoContent(Long photoId) {
        Photo photo = requireAccessiblePhoto(photoId);
        return storageService.read(photo.getObjectKey());
    }

    private Photo requireAccessiblePhoto(Long photoId) {
        Photo photo = photoMapper.selectById(photoId);
        if (photo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Photo not found");
        }
        familyService.checkMembership(photo.getFamilyId());
        return photo;
    }

    private PhotoUploadResponse toResponse(Photo photo) {
        return PhotoUploadResponse.builder()
                .id(photo.getId())
                .familyId(photo.getFamilyId())
                .uploaderId(photo.getUploaderId())
                .assetUrl(buildAssetUrl(photo.getId()))
                .metadata(photo.getMetadata())
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

    private String buildAssetUrl(Long photoId) {
        return "/api/photos/" + photoId + "/content";
    }
}
