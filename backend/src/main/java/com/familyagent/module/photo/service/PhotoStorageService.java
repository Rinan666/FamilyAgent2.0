package com.familyagent.module.photo.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.photo.dto.PhotoContentResource;
import io.minio.*;
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

    public String upload(Long familyId, MultipartFile file) {
        ensureBucket();
        String ext = getExtension(file.getOriginalFilename());
        String objectKey = "family-photos/" + familyId + "/" + UUID.randomUUID() + ext;
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
            log.error("MinIO upload failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED);
        }
        return objectKey;
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
            log.error("MinIO read failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED);
        }
    }

    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            log.error("MinIO ensureBucket failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED);
        }
    }

    private static String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return "." + filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
