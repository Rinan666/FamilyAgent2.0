package com.familyagent.module.photo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PhotoClusterMetadata(
        List<Group> groups,
        @JsonProperty("total_faces")
        Integer totalFaces,
        @JsonProperty("silhouette_score")
        Double silhouetteScore) {

    public record Group(
            @JsonProperty("group_id")
            Integer groupId,
            List<Face> faces) {
    }

    public record Face(
            @JsonProperty("photo_id")
            Long photoId,
            @JsonProperty("file_index")
            Integer fileIndex,
            @JsonProperty("face_index")
            Integer faceIndex,
            BoundingBox bbox) {
    }

    public record BoundingBox(
            Double x,
            Double y,
            Double w,
            Double h) {
    }
}
