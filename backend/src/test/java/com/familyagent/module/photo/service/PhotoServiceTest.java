package com.familyagent.module.photo.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.constant.PhotoScope;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.photo.dto.PhotoClusterMetadata;
import com.familyagent.module.photo.dto.PhotoContentResource;
import com.familyagent.module.photo.dto.PhotoUploadResponse;
import com.familyagent.module.photo.entity.Photo;
import com.familyagent.module.photo.mapper.PhotoMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

    @Mock private PhotoStorageService storageService;
    @Mock private PhotoMapper photoMapper;
    @Mock private FamilyMembershipFacade familyMembershipFacade;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private PhotoService photoService;

    @Test
    void upload_usesCurrentUserAndReturnsBackendAssetUrl() {
        MockMultipartFile file = new MockMultipartFile("files", "family.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(storageService.upload(10L, 301L, PhotoScope.FAMILY, file)).thenReturn("family/10/family.jpg");
        doAnswer(invocation -> {
            Photo photo = invocation.getArgument(0);
            photo.setId(88L);
            return 1;
        }).when(photoMapper).insert(any(Photo.class));

        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(301L);

            List<PhotoUploadResponse> result = photoService.upload(10L, null, null, List.of(file));

            verify(familyMembershipFacade).checkMembership(10L);
            assertEquals(1, result.size());
            assertEquals(88L, result.get(0).getId());
            assertEquals(301L, result.get(0).getUploaderId());
            assertEquals("FAMILY", result.get(0).getScope());
            assertEquals("image/jpeg", result.get(0).getMimeType());
            assertEquals("family.jpg", result.get(0).getOriginalName());
            assertEquals("/api/photos/88/content", result.get(0).getAssetUrl());
        }
    }

    @Test
    void upload_rejectsUnknownScope() {
        MockMultipartFile file = new MockMultipartFile("files", "family.jpg", "image/jpeg", new byte[]{1});

        assertThrows(BusinessException.class, () -> photoService.upload(10L, "PUBLIC", null, List.of(file)));
    }

    @Test
    void getPhotoContent_checksMembershipBeforeReadingStorage() {
        Photo photo = new Photo();
        photo.setId(5L);
        photo.setFamilyId(10L);
        photo.setUploaderId(7L);
        photo.setScope("FAMILY");
        photo.setObjectKey("family/10/5.jpg");
        PhotoContentResource content = new PhotoContentResource(new ByteArrayInputStream(new byte[]{1}), "image/jpeg");

        when(photoMapper.selectById(5L)).thenReturn(photo);
        when(storageService.read(photo.getObjectKey())).thenReturn(content);

        PhotoContentResource result = photoService.getPhotoContent(5L);

        verify(familyMembershipFacade).checkMembership(10L);
        verify(storageService).read("family/10/5.jpg");
        assertEquals("image/jpeg", result.contentType());
    }

    @Test
    void getPhotoContent_allowsPersonalPhotoOnlyForUploader() {
        Photo photo = new Photo();
        photo.setId(5L);
        photo.setFamilyId(10L);
        photo.setUploaderId(301L);
        photo.setScope("PERSONAL");
        photo.setObjectKey("personal/301/5.jpg");
        PhotoContentResource content = new PhotoContentResource(new ByteArrayInputStream(new byte[]{1}), "image/jpeg");

        when(photoMapper.selectById(5L)).thenReturn(photo);
        when(storageService.read(photo.getObjectKey())).thenReturn(content);

        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(301L);

            PhotoContentResource result = photoService.getPhotoContent(5L);

            verify(storageService).read("personal/301/5.jpg");
            assertEquals("image/jpeg", result.contentType());
        }
    }

    @Test
    void getPhotoContent_rejectsPersonalPhotoForOtherUser() {
        Photo photo = new Photo();
        photo.setId(5L);
        photo.setFamilyId(10L);
        photo.setUploaderId(301L);
        photo.setScope("PERSONAL");
        photo.setObjectKey("personal/301/5.jpg");

        when(photoMapper.selectById(5L)).thenReturn(photo);

        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(302L);

            assertThrows(BusinessException.class, () -> photoService.getPhotoContent(5L));
        }
    }

    @Test
    void updateClusterResult_rejectsMissingPhoto() {
        when(photoMapper.selectById(999L)).thenReturn(null);

        PhotoClusterMetadata metadata = new PhotoClusterMetadata(List.of(), 0, null);

        assertThrows(BusinessException.class, () -> photoService.updateClusterResult(999L, metadata));
    }

    @Test
    void updateClusterResult_requiresUploaderAndMembershipForFamilyPhoto() {
        Photo photo = new Photo();
        photo.setId(12L);
        photo.setFamilyId(20L);
        photo.setUploaderId(301L);
        photo.setScope("FAMILY");
        PhotoClusterMetadata metadata = new PhotoClusterMetadata(List.of(), 0, null);

        when(photoMapper.selectById(12L)).thenReturn(photo);

        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(301L);

            photoService.updateClusterResult(12L, metadata);

            verify(familyMembershipFacade).checkMembership(20L);
            verify(photoMapper).updateById(photo);
            assertEquals(metadata, photo.getMetadata());
        }
    }

    @Test
    void updateClusterResult_rejectsNonUploader() {
        Photo photo = new Photo();
        photo.setId(12L);
        photo.setFamilyId(20L);
        photo.setUploaderId(301L);
        photo.setScope("FAMILY");
        PhotoClusterMetadata metadata = new PhotoClusterMetadata(List.of(), 0, null);

        when(photoMapper.selectById(12L)).thenReturn(photo);

        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(302L);

            assertThrows(BusinessException.class, () -> photoService.updateClusterResult(12L, metadata));
            verify(photoMapper, never()).updateById(any(Photo.class));
        }
    }

    @Test
    void listByFamily_normalizesLimitAndMapsAssetUrl() {
        Photo photo = new Photo();
        photo.setId(12L);
        photo.setFamilyId(20L);
        photo.setUploaderId(7L);

        when(photoMapper.selectByFamilyIdAndScope(20L, "FAMILY", 100)).thenReturn(List.of(photo));

        List<PhotoUploadResponse> result = photoService.listByFamily(20L, 999);

        verify(familyMembershipFacade).checkMembership(20L);
        assertEquals(1, result.size());
        assertEquals("/api/photos/12/content", result.get(0).getAssetUrl());
    }

    @Test
    void listMyPhotos_returnsOnlyCurrentUserPersonalPhotos() {
        Photo photo = new Photo();
        photo.setId(12L);
        photo.setFamilyId(20L);
        photo.setUploaderId(301L);
        photo.setScope("PERSONAL");

        when(photoMapper.selectByUploaderIdAndScope(301L, "PERSONAL", 50)).thenReturn(List.of(photo));

        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(301L);

            List<PhotoUploadResponse> result = photoService.listMyPhotos(50);

            assertEquals(1, result.size());
            assertEquals("PERSONAL", result.get(0).getScope());
        }
    }

    @Test
    void upload_rejectsNonImageMimeType() {
        MockMultipartFile file = new MockMultipartFile("files", "evil.html", "text/html", new byte[]{1});

        BusinessException ex = assertThrows(BusinessException.class,
                () -> photoService.upload(10L, "FAMILY", null, List.of(file)));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void delete_requiresUploaderAndRemovesRowBeforeStorage() {
        Photo photo = new Photo();
        photo.setId(12L);
        photo.setFamilyId(20L);
        photo.setUploaderId(301L);
        photo.setScope("FAMILY");
        photo.setObjectKey("family/20/12.jpg");

        when(photoMapper.selectById(12L)).thenReturn(photo);

        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(301L);

            photoService.delete(12L);

            InOrder inOrder = inOrder(photoMapper, storageService);
            inOrder.verify(photoMapper).deleteById(12L);
            inOrder.verify(storageService).delete("family/20/12.jpg");
        }
    }

    @Test
    void delete_keepsSuccessWhenObjectCleanupFailsAfterRowRemoval() {
        Photo photo = new Photo();
        photo.setId(12L);
        photo.setFamilyId(20L);
        photo.setUploaderId(301L);
        photo.setScope("FAMILY");
        photo.setObjectKey("family/20/12.jpg");

        when(photoMapper.selectById(12L)).thenReturn(photo);
        doThrow(new BusinessException(ErrorCode.OSS_UPLOAD_FAILED, "storage offline"))
                .when(storageService).delete("family/20/12.jpg");

        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(301L);

            photoService.delete(12L);

            InOrder inOrder = inOrder(photoMapper, storageService);
            inOrder.verify(photoMapper).deleteById(12L);
            inOrder.verify(storageService).delete("family/20/12.jpg");
        }
    }
}
