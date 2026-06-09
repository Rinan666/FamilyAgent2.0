package com.familyagent.module.session.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.session.entity.ChatSessionMessage;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
public class ChatSessionArchiveStorageService {

    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;
    private final String bucketName;

    public ChatSessionArchiveStorageService(@Value("${minio.endpoint}") String endpoint,
                                            @Value("${minio.access-key}") String accessKey,
                                            @Value("${minio.secret-key}") String secretKey,
                                            @Value("${minio.bucket-name}") String bucketName,
                                            ObjectMapper objectMapper) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.objectMapper = objectMapper;
        this.bucketName = bucketName;
    }

    public String writeTranscript(Long sessionId, int startSeq, int endSeq, List<ChatSessionMessage> messages) {
        ensureBucket();
        String objectKey = "chat-sessions/" + sessionId + "/archives/" + startSeq + "-" + endSeq + ".json";
        try {
            byte[] payload = objectMapper.writeValueAsBytes(messages);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .contentType("application/json")
                    .stream(new ByteArrayInputStream(payload), payload.length, -1)
                    .build());
            return objectKey;
        } catch (Exception error) {
            throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED, "Failed to archive chat transcript: " + error.getMessage());
        }
    }

    public List<ChatSessionMessage> readTranscript(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return List.of();
        }
        try (var stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(objectKey)
                .build())) {
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return objectMapper.readValue(json, new TypeReference<List<ChatSessionMessage>>() {});
        } catch (Exception error) {
            log.warn("Failed to read archived chat transcript: key={}, error={}", objectKey, error.getMessage());
            return List.of();
        }
    }

    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception error) {
            throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED, "Failed to prepare MinIO bucket: " + error.getMessage());
        }
    }
}
