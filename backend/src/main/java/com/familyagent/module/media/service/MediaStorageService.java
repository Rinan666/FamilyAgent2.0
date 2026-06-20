package com.familyagent.module.media.service;

import com.familyagent.common.constant.MediaRecordType;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.media.dto.MediaContentResource;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
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
public class MediaStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @PostConstruct
    public void init() {
        ensureBucket();
    }

    public String upload(Long familyId, MediaRecordType recordType, Long recordId, MultipartFile file) {
        String objectKey = buildObjectKey(familyId, recordType, recordId, getExtension(file.getOriginalFilename()));
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());
        } catch (Exception e) {
            log.error("Media upload failed: familyId={}, recordType={}, recordId={}, filename={}, error={}",
                    familyId, recordType, recordId, file.getOriginalFilename(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED, "Media upload failed: " + e.getMessage());
        }
        return objectKey;
    }

    public void delete(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
        } catch (Exception e) {
            log.error("Media delete failed: objectKey={}, error={}", objectKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED, "Failed to delete media content: " + e.getMessage());
        }
    }

    public MediaContentResource read(String objectKey) {
        try {
            GetObjectResponse response = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
            String contentType = response.headers().get("Content-Type");
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }
            return new MediaContentResource(response, contentType);
        } catch (Exception e) {
            log.error("Media read failed: objectKey={}, error={}", objectKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED, "Failed to read media content: " + e.getMessage());
        }
    }

    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            log.error("Media bucket preparation failed: bucket={}, error={}", bucketName, e.getMessage(), e);
            throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED, "Failed to prepare media storage: " + e.getMessage());
        }
    }

    private static String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return "." + filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private static String buildObjectKey(Long familyId, MediaRecordType recordType, Long recordId, String ext) {
        return "media/" + familyId + "/" + recordType.name().toLowerCase() + "/" + recordId + "/" + UUID.randomUUID() + ext;
    }
}
