package com.familyagent.module.photo.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.photo.dto.PhotoContentResource;
import com.familyagent.module.photo.dto.PhotoUploadResponse;
import com.familyagent.module.photo.entity.Photo;
import com.familyagent.module.photo.mapper.PhotoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

    @Mock private PhotoStorageService storageService;
    @Mock private PhotoMapper photoMapper;
    @Mock private FamilyService familyService;
    @InjectMocks private PhotoService photoService;

    @Test
    void upload_usesCurrentUserAndReturnsBackendAssetUrl() {
        MockMultipartFile file = new MockMultipartFile("files", "family.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(storageService.upload(10L, file)).thenReturn("family-photos/10/family.jpg");
        doAnswer(invocation -> {
            Photo photo = invocation.getArgument(0);
            photo.setId(88L);
            return 1;
        }).when(photoMapper).insert(any(Photo.class));

        try (MockedStatic<CurrentUserGuard> currentUserMock = mockStatic(CurrentUserGuard.class)) {
            currentUserMock.when(CurrentUserGuard::currentUserId).thenReturn(301L);

            List<PhotoUploadResponse> result = photoService.upload(10L, List.of(file));

            verify(familyService).checkMembership(10L);
            assertEquals(1, result.size());
            assertEquals(88L, result.get(0).getId());
            assertEquals(301L, result.get(0).getUploaderId());
            assertEquals("/api/photos/88/content", result.get(0).getAssetUrl());
        }
    }

    @Test
    void getPhotoContent_checksMembershipBeforeReadingStorage() {
        Photo photo = new Photo();
        photo.setId(5L);
        photo.setFamilyId(10L);
        photo.setObjectKey("family-photos/10/5.jpg");
        PhotoContentResource content = new PhotoContentResource(new ByteArrayInputStream(new byte[]{1}), "image/jpeg");

        when(photoMapper.selectById(5L)).thenReturn(photo);
        when(storageService.read(photo.getObjectKey())).thenReturn(content);

        PhotoContentResource result = photoService.getPhotoContent(5L);

        verify(familyService).checkMembership(10L);
        verify(storageService).read("family-photos/10/5.jpg");
        assertEquals("image/jpeg", result.contentType());
    }

    @Test
    void updateClusterResult_rejectsMissingPhoto() {
        when(photoMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> photoService.updateClusterResult(999L, List.of()));
    }

    @Test
    void listByFamily_normalizesLimitAndMapsAssetUrl() {
        Photo photo = new Photo();
        photo.setId(12L);
        photo.setFamilyId(20L);
        photo.setUploaderId(7L);

        when(photoMapper.selectByFamilyId(20L, 100)).thenReturn(List.of(photo));

        List<PhotoUploadResponse> result = photoService.listByFamily(20L, 999);

        verify(familyService).checkMembership(20L);
        assertEquals(1, result.size());
        assertEquals("/api/photos/12/content", result.get(0).getAssetUrl());
    }
}
