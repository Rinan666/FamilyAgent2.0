package com.familyagent.module.photo.service;

import com.familyagent.module.photo.mapper.PhotoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoFamilyResourceCleanerTest {

    @Mock private PhotoMapper photoMapper;
    @Mock private PhotoStorageService storageService;
    @InjectMocks private PhotoFamilyResourceCleaner cleaner;

    @Test
    void cleanFamilyResources_deletesAllPhotoObjects() {
        when(photoMapper.selectObjectKeysByFamilyId(10L)).thenReturn(List.of(
                "family/10/a.jpg",
                "family/10/b.jpg"));

        cleaner.cleanFamilyResources(10L);

        verify(storageService).delete("family/10/a.jpg");
        verify(storageService).delete("family/10/b.jpg");
    }

    @Test
    void cleanFamilyResources_continuesAfterDeleteFailure() {
        when(photoMapper.selectObjectKeysByFamilyId(10L)).thenReturn(List.of(
                "family/10/a.jpg",
                "family/10/b.jpg"));
        doThrow(new RuntimeException("storage offline")).when(storageService).delete("family/10/a.jpg");

        cleaner.cleanFamilyResources(10L);

        verify(storageService).delete("family/10/a.jpg");
        verify(storageService).delete("family/10/b.jpg");
    }
}
