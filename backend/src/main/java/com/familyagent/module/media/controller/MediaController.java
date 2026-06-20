package com.familyagent.module.media.controller;

import com.familyagent.common.response.Result;
import com.familyagent.module.media.dto.MediaAttachmentResponse;
import com.familyagent.module.media.dto.MediaContentResource;
import com.familyagent.module.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/upload")
    public Result<List<MediaAttachmentResponse>> upload(
            @RequestParam("recordType") String recordType,
            @RequestParam("recordId") Long recordId,
            @RequestParam("files") List<MultipartFile> files) {
        return Result.success(mediaService.upload(recordType, recordId, files));
    }

    @GetMapping
    public Result<List<MediaAttachmentResponse>> list(
            @RequestParam("recordType") String recordType,
            @RequestParam("recordId") Long recordId) {
        return Result.success(mediaService.list(recordType, recordId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mediaService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> getContent(@PathVariable Long id) {
        MediaContentResource content = mediaService.getContent(id);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.contentType());
        } catch (InvalidMediaTypeException ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        // Only image media may render inline; SVG is forced out of renderable mode.
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
