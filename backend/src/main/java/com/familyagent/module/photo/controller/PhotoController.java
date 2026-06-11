package com.familyagent.module.photo.controller;

import com.familyagent.common.response.Result;
import com.familyagent.module.photo.dto.PhotoContentResource;
import com.familyagent.module.photo.dto.PhotoUploadResponse;
import com.familyagent.module.photo.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;

    @PostMapping("/upload")
    public Result<List<PhotoUploadResponse>> upload(
            @RequestParam("familyId") Long familyId,
            @RequestParam("files") List<MultipartFile> files) {
        return Result.success(photoService.upload(familyId, files));
    }

    @PatchMapping("/{id}/cluster-result")
    public Result<Void> updateClusterResult(
            @PathVariable Long id,
            @RequestBody Map<String, Object> clusterResult) {
        photoService.updateClusterResult(id, clusterResult);
        return Result.success();
    }

    @GetMapping("/family/{familyId}")
    public Result<List<PhotoUploadResponse>> listByFamily(
            @PathVariable Long familyId,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(photoService.listByFamily(familyId, limit));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> getContent(@PathVariable Long id) {
        PhotoContentResource content = photoService.getPhotoContent(id);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.contentType());
        } catch (InvalidMediaTypeException ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(mediaType)
                .body(new InputStreamResource(content.stream()));
    }
}
