package com.familyagent.module.photo.service;

import com.familyagent.common.lifecycle.FamilyScopedResourceCleaner;
import com.familyagent.module.photo.mapper.PhotoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PhotoFamilyResourceCleaner implements FamilyScopedResourceCleaner {

    private final PhotoMapper photoMapper;
    private final PhotoStorageService storageService;

    @Override
    public void cleanFamilyResources(Long familyId) {
        List<String> objectKeys = photoMapper.selectObjectKeysByFamilyId(familyId);
        for (String objectKey : objectKeys) {
            try {
                storageService.delete(objectKey);
            } catch (RuntimeException e) {
                log.warn("Failed to delete family photo object: familyId={}, objectKey={}, error={}",
                        familyId, objectKey, e.getMessage());
            }
        }
    }
}
