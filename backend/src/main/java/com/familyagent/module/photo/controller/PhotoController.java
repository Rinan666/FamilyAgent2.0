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
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("files") List<MultipartFile> files) {
        return Result.success(photoService.upload(familyId, scope, description, files));
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

    @GetMapping("/my")
    public Result<List<PhotoUploadResponse>> listMyPhotos(
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(photoService.listMyPhotos(limit));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        photoService.delete(id);
        return Result.success();
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
        // Only ever serve images inline. Anything else (e.g. a smuggled text/html
        // or svg payload) is forced to a non-renderable type to prevent stored XSS.
        if (!"image".equalsIgnoreCase(mediaType.getType()) || "svg+xml".equalsIgnoreCase(mediaType.getSubtype())) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline")
                .contentType(mediaType)
                .body(new InputStreamResource(content.stream()));
    }
}
