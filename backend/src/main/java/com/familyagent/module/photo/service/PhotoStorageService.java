package com.familyagent.module.photo.service;

import com.familyagent.common.constant.PhotoScope;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.photo.dto.PhotoContentResource;
import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @PostConstruct
    public void init() {
        ensureBucket();
    }

    public String upload(Long familyId, Long uploaderId, PhotoScope scope, MultipartFile file) {
        String ext = getExtension(file.getOriginalFilename());
        String objectKey = buildObjectKey(familyId, uploaderId, scope, ext);
        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build()
            );
        } catch (Exception e) {
            log.error("MinIO upload failed: familyId={}, filename={}, error={}",
                    familyId, file.getOriginalFilename(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED, "Photo upload failed: " + e.getMessage());
        }
        return objectKey;
    }

    public void delete(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            log.error("MinIO delete failed: objectKey={}, error={}", objectKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED, "Failed to delete photo content: " + e.getMessage());
        }
    }

    public PhotoContentResource read(String objectKey) {
        try {
            GetObjectResponse response = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            String contentType = response.headers().get("Content-Type");
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }
            return new PhotoContentResource(response, contentType);
        } catch (Exception e) {
            log.error("MinIO read failed: objectKey={}, error={}", objectKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED, "Failed to read photo content: " + e.getMessage());
        }
    }

    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            log.error("MinIO ensureBucket failed: bucket={}, error={}", bucketName, e.getMessage(), e);
            throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED, "Failed to prepare photo storage: " + e.getMessage());
        }
    }

    private static String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return "." + filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private static String buildObjectKey(Long familyId, Long uploaderId, PhotoScope scope, String ext) {
        if (scope == PhotoScope.PERSONAL) {
            return "personal/" + uploaderId + "/" + UUID.randomUUID() + ext;
        }
        return "family/" + familyId + "/" + UUID.randomUUID() + ext;
    }
}
