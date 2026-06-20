package com.familyagent.module.media.service;

import com.familyagent.common.constant.MediaRecordType;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.media.dto.MediaAttachmentResponse;
import com.familyagent.module.media.dto.MediaContentResource;
import com.familyagent.module.media.entity.MediaAttachment;
import com.familyagent.module.media.mapper.MediaAttachmentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock private MediaAttachmentMapper attachmentMapper;
    @Mock private MediaStorageService storageService;
    @Mock private MediaRecordAccessFacade recordAccessFacade;
    @InjectMocks private MediaService mediaService;

    @Test
    void upload_checksWritePermissionAndReturnsAssetUrl() {
        MockMultipartFile file = new MockMultipartFile("files", "diary.jpg", "image/jpeg", new byte[]{1, 2});
        when(recordAccessFacade.requireWritable(MediaRecordType.DIARY, 10L))
                .thenReturn(new MediaRecordAccess(MediaRecordType.DIARY, 10L, 3L));
        when(storageService.upload(3L, MediaRecordType.DIARY, 10L, file)).thenReturn("media/3/diary/10/a.jpg");
        doAnswer(invocation -> {
            MediaAttachment attachment = invocation.getArgument(0);
            attachment.setId(99L);
            return 1;
        }).when(attachmentMapper).insert(any(MediaAttachment.class));

        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(42L);

            List<MediaAttachmentResponse> result = mediaService.upload("DIARY", 10L, List.of(file));

            assertEquals(1, result.size());
            assertEquals(99L, result.get(0).getId());
            assertEquals(42L, result.get(0).getUploaderId());
            assertEquals("/api/media/99/content", result.get(0).getAssetUrl());
            assertEquals(MediaRecordType.DIARY, result.get(0).getRecordType());
        }
    }

    @Test
    void upload_rejectsNonImageMimeType() {
        MockMultipartFile file = new MockMultipartFile("files", "note.txt", "text/plain", new byte[]{1});
        when(recordAccessFacade.requireWritable(MediaRecordType.DIARY, 10L))
                .thenReturn(new MediaRecordAccess(MediaRecordType.DIARY, 10L, 3L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> mediaService.upload("DIARY", 10L, List.of(file)));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void list_requiresReadPermission() {
        MediaAttachment attachment = attachment(9L, "MEMORY", 12L, "media/3/memory/12/a.jpg");
        when(recordAccessFacade.requireReadable(MediaRecordType.MEMORY, 12L))
                .thenReturn(new MediaRecordAccess(MediaRecordType.MEMORY, 12L, 3L));
        when(attachmentMapper.selectByRecord("MEMORY", 12L)).thenReturn(List.of(attachment));

        List<MediaAttachmentResponse> result = mediaService.list("MEMORY", 12L);

        verify(recordAccessFacade).requireReadable(MediaRecordType.MEMORY, 12L);
        assertEquals(1, result.size());
        assertEquals("/api/media/9/content", result.get(0).getAssetUrl());
    }

    @Test
    void getContent_requiresReadPermissionBeforeStorageRead() {
        MediaAttachment attachment = attachment(9L, "GROWTH", 12L, "media/3/growth/12/a.jpg");
        MediaContentResource content = new MediaContentResource(new ByteArrayInputStream(new byte[]{1}), "image/jpeg");

        when(attachmentMapper.selectById(9L)).thenReturn(attachment);
        when(recordAccessFacade.requireReadable(MediaRecordType.GROWTH, 12L))
                .thenReturn(new MediaRecordAccess(MediaRecordType.GROWTH, 12L, 3L));
        when(storageService.read("media/3/growth/12/a.jpg")).thenReturn(content);

        MediaContentResource result = mediaService.getContent(9L);

        InOrder inOrder = inOrder(recordAccessFacade, storageService);
        inOrder.verify(recordAccessFacade).requireReadable(MediaRecordType.GROWTH, 12L);
        inOrder.verify(storageService).read("media/3/growth/12/a.jpg");
        assertEquals("image/jpeg", result.contentType());
    }

    @Test
    void delete_removesRowBeforeStorageObject() {
        MediaAttachment attachment = attachment(9L, "DIARY", 12L, "media/3/diary/12/a.jpg");
        when(attachmentMapper.selectById(9L)).thenReturn(attachment);
        when(recordAccessFacade.requireWritable(MediaRecordType.DIARY, 12L))
                .thenReturn(new MediaRecordAccess(MediaRecordType.DIARY, 12L, 3L));

        mediaService.delete(9L);

        InOrder inOrder = inOrder(attachmentMapper, storageService);
        inOrder.verify(attachmentMapper).deleteById(9L);
        inOrder.verify(storageService).delete("media/3/diary/12/a.jpg");
    }

    private static MediaAttachment attachment(Long id, String recordType, Long recordId, String objectKey) {
        MediaAttachment attachment = new MediaAttachment();
        attachment.setId(id);
        attachment.setUploaderId(42L);
        attachment.setFamilyId(3L);
        attachment.setRecordType(recordType);
        attachment.setRecordId(recordId);
        attachment.setObjectKey(objectKey);
        attachment.setMimeType("image/jpeg");
        attachment.setFileSize(2L);
        attachment.setOriginalName("image.jpg");
        return attachment;
    }
}
