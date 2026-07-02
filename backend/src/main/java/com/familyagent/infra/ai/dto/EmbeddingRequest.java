package com.familyagent.infra.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmbeddingRequest {

    private String text;
    private String model;
    private Integer dimensions;

    @JsonProperty("source_type")
    private String sourceType;

    @JsonProperty("family_id")
    private Long familyId;

    @JsonProperty("user_id")
    private Long userId;
}
